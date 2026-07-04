package app.yodai.telewear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.yodai.telewear.AppGraph
import app.yodai.telewear.settings.AppSettings
import app.yodai.telewear.telegram.AuthUi
import app.yodai.telewear.ui.auth.AuthFlow
import app.yodai.telewear.ui.chat.ChatScreen
import app.yodai.telewear.ui.chatlist.ChatListScreen
import app.yodai.telewear.ui.components.LocalAppGraph
import app.yodai.telewear.ui.components.LocalFontScale
import app.yodai.telewear.ui.settings.SettingsScreen
import app.yodai.telewear.ui.theme.TeleWearTheme

@Composable
fun TeleWearRoot(graph: AppGraph) {
    // Keep-alive service lifecycle is owned by KeepAliveController (AppGraph),
    // which also factors in the configurable battery floor.
    val settings by graph.settings.flow.collectAsState(initial = AppSettings())

    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalFontScale provides settings.fontScale,
    ) {
        TeleWearTheme {
            AppScaffold {
                val auth by graph.core.authUi.collectAsState()
                when (val state = auth) {
                    is AuthUi.Ready -> MainNav(graph)
                    else -> AuthFlow(state)
                }
            }
        }
    }
}

@Composable
private fun MainNav(graph: AppGraph) {
    val nav = rememberSwipeDismissableNavController()

    // A tapped notification lands here as a pending chat id.
    LaunchedEffect(Unit) {
        graph.pendingOpenChatId.collect { chatId ->
            if (chatId != null) {
                graph.pendingOpenChatId.value = null
                nav.navigate("chat/$chatId")
            }
        }
    }

    SwipeDismissableNavHost(navController = nav, startDestination = "chats") {
        composable("chats") {
            ChatListScreen(
                onOpenChat = { nav.navigate("chat/$it") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("chat/{chatId}") { backStack ->
            val chatId = backStack.arguments?.getString("chatId")?.toLongOrNull() ?: return@composable
            ChatScreen(chatId)
        }
        composable("settings") {
            SettingsScreen()
        }
    }
}
