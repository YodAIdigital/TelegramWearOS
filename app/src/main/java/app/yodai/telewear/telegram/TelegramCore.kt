package app.yodai.telewear.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import app.yodai.telewear.BuildConfig
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.ConnectionStateReady
import dev.g000sha256.tdl.dto.NetworkType
import dev.g000sha256.tdl.dto.NetworkTypeNone
import dev.g000sha256.tdl.dto.NetworkTypeOther
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Result helpers — keep TdlResult handling terse at call sites. */
fun <T> TdlResult<T>.getOrNull(): T? = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> null
}

fun <T> TdlResult<T>.errorOrNull(): String? = when (this) {
    is TdlResult.Success -> null
    is TdlResult.Failure -> message
}

fun <T> TdlResult<T>.isNotFound(): Boolean = this is TdlResult.Failure && code == 404

/** What the auth flow should show right now. */
sealed interface AuthUi {
    data object Starting : AuthUi
    data object MissingApiCreds : AuthUi
    data object Connecting : AuthUi
    data class Qr(val link: String) : AuthUi
    data object PhoneEntry : AuthUi
    data class CodeEntry(val sentTo: String) : AuthUi
    data class PasswordEntry(val hint: String) : AuthUi
    data object Ready : AuthUi
    data class Unsupported(val reason: String) : AuthUi
}

private enum class LoginMode { QR, PHONE }

/**
 * Owns the TDLib client and drives the authorization state machine.
 * The client is recreated after logout (TDLib closes its instance), bumping [generation]
 * so repositories re-subscribe to the new client's update flows.
 */
class TelegramCore(private val context: Context, private val scope: CoroutineScope) {

    var client: TdlClient = TdlClient.create()
        private set

    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation

    private val _authUi = MutableStateFlow<AuthUi>(AuthUi.Starting)
    val authUi: StateFlow<AuthUi> = _authUi

    /** Last recoverable error (wrong code, invalid phone, …) for the auth screens. */
    val authError = MutableStateFlow<String?>(null)

    val isConnecting = MutableStateFlow(false)

    private var loginMode = LoginMode.QR

    init {
        bind(client)
        scope.launch {
            updates { it.connectionStateUpdates }.collect { u ->
                Log.i(TAG, "connection: ${u.state::class.simpleName}")
                isConnecting.value = u.state !is ConnectionStateReady
            }
        }
        watchNetwork()
    }

    /**
     * Wear OS flips between the Bluetooth phone proxy, Wi-Fi, and no network
     * constantly. Telling TDLib immediately makes it drop dead sockets and
     * reconnect right away instead of waiting out TCP timeouts.
     */
    private fun watchNetwork() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = pushNetworkType(NetworkTypeOther())

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val type = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        NetworkTypeWiFi()
                    } else {
                        NetworkTypeOther()
                    }
                    pushNetworkType(type)
                }

                override fun onLost(network: Network) = pushNetworkType(NetworkTypeNone())
            })
        }.onFailure { Log.w(TAG, "network callback registration failed", it) }
    }

    private var lastNetworkType: String? = null

    private fun pushNetworkType(type: NetworkType) {
        val name = type::class.simpleName
        if (name == lastNetworkType) return
        lastNetworkType = name
        Log.i(TAG, "network -> $name")
        scope.launch {
            client.setNetworkType(type).errorOrNull()?.let { Log.w(TAG, "setNetworkType: $it") }
        }
    }

    /**
     * Flow of updates that survives client restarts: re-selects from the fresh client
     * whenever [generation] bumps.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> updates(selector: (TdlClient) -> Flow<T>): Flow<T> =
        generation.flatMapLatest { selector(client) }

    /** Fire-and-forget request on the app scope (safe from ViewModels being cleared). */
    fun async(block: suspend (TdlClient) -> Unit) {
        scope.launch { block(client) }
    }

    private fun bind(c: TdlClient) {
        scope.launch {
            c.authorizationStateUpdates.collect { upd -> onAuthState(c, upd.authorizationState) }
        }
        // The first update may have fired before we started collecting — query current state.
        scope.launch {
            c.getAuthorizationState().getOrNull()?.let { onAuthState(c, it) }
        }
    }

    private suspend fun onAuthState(c: TdlClient, st: AuthorizationState) {
        when (st) {
            is AuthorizationStateWaitTdlibParameters -> {
                if (BuildConfig.TELEGRAM_API_ID == 0 || BuildConfig.TELEGRAM_API_HASH.isEmpty()) {
                    _authUi.value = AuthUi.MissingApiCreds
                    return
                }
                _authUi.value = AuthUi.Connecting
                val db = File(context.filesDir, "tdlib").apply { mkdirs() }
                val filesDir = File(context.filesDir, "tdlib-files").apply { mkdirs() }
                c.setTdlibParameters(
                    useTestDc = false,
                    databaseDirectory = db.absolutePath,
                    filesDirectory = filesDir.absolutePath,
                    databaseEncryptionKey = ByteArray(0),
                    useFileDatabase = true,
                    useChatInfoDatabase = true,
                    useMessageDatabase = true,
                    useSecretChats = false,
                    apiId = BuildConfig.TELEGRAM_API_ID,
                    apiHash = BuildConfig.TELEGRAM_API_HASH,
                    systemLanguageCode = "en",
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Wear OS watch" },
                    systemVersion = "Wear OS (Android ${Build.VERSION.RELEASE})",
                    applicationVersion = BuildConfig.VERSION_NAME,
                ).errorOrNull()?.let { Log.w(TAG, "setTdlibParameters: $it") }
            }

            is AuthorizationStateWaitPhoneNumber -> when (loginMode) {
                LoginMode.QR -> {
                    _authUi.value = AuthUi.Connecting
                    c.requestQrCodeAuthentication(LongArray(0))
                        .errorOrNull()?.let { Log.w(TAG, "requestQr: $it") }
                }
                LoginMode.PHONE -> _authUi.value = AuthUi.PhoneEntry
            }

            is AuthorizationStateWaitOtherDeviceConfirmation ->
                _authUi.value = AuthUi.Qr(st.link)

            is AuthorizationStateWaitCode ->
                _authUi.value = AuthUi.CodeEntry(st.codeInfo.phoneNumber)

            is AuthorizationStateWaitPassword ->
                _authUi.value = AuthUi.PasswordEntry(st.passwordHint.orEmpty())

            is AuthorizationStateWaitRegistration ->
                _authUi.value = AuthUi.Unsupported("No Telegram account for this number. Sign up on your phone first, then log in here.")

            is AuthorizationStateReady -> {
                authError.value = null
                _authUi.value = AuthUi.Ready
            }

            is AuthorizationStateLoggingOut -> _authUi.value = AuthUi.Connecting

            is AuthorizationStateClosed -> restart()

            else -> Unit
        }
    }

    /** TDLib instance is dead after Closed — build a fresh one (e.g. right after logout). */
    private fun restart() {
        loginMode = LoginMode.QR
        _authUi.value = AuthUi.Starting
        client = TdlClient.create()
        _generation.value += 1
        bind(client)
    }

    // ---- auth actions ----

    fun usePhoneLogin() {
        loginMode = LoginMode.PHONE
        _authUi.value = AuthUi.PhoneEntry
    }

    /**
     * Requests a fresh QR token without disturbing the visible state — QR login
     * tokens expire in ~30 s, so the screen re-requests one whenever it resumes
     * (and on tap). The new link arrives as a state update and redraws in place.
     */
    fun refreshQr() {
        scope.launch {
            client.requestQrCodeAuthentication(LongArray(0))
                .errorOrNull()?.let { Log.w(TAG, "refreshQr: $it") }
        }
    }

    fun useQrLogin() {
        loginMode = LoginMode.QR
        _authUi.value = AuthUi.Connecting
        scope.launch {
            client.requestQrCodeAuthentication(LongArray(0))
                .errorOrNull()?.let { authError.value = it }
        }
    }

    fun submitPhone(number: String) = action { it.setAuthenticationPhoneNumber(number, null).errorOrNull() }

    fun submitCode(code: String) = action { it.checkAuthenticationCode(code).errorOrNull() }

    fun submitPassword(password: String) = action { it.checkAuthenticationPassword(password).errorOrNull() }

    fun logout() = action { it.logOut().errorOrNull() }

    private fun action(block: suspend (TdlClient) -> String?) {
        scope.launch {
            authError.value = null
            authError.value = block(client)
        }
    }

    private companion object {
        const val TAG = "TelegramCore"
    }
}
