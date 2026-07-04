# YodChat — Telegram for Wear OS

An unofficial, standalone **Telegram client for Wear OS**, designed for the **Samsung Galaxy Watch 8 (44 mm)** and built with Jetpack **Compose for Wear OS (Material 3)** + **TDLib** (Telegram's official client library, via [tdl-coroutines](https://github.com/g000sha256/tdl-coroutines)).

| | |
|---|---|
| Chats | Full chat list — private chats, groups, channels — with avatars, unread badges, mute icons, **Telegram folder chips**, and **search** |
| Messages | Text bubbles with tappable links, photos (instant blurry preview → tap for a **bezel-zoomable** viewer), videos/GIFs with full player controls, voice notes & audio files with a speed-controlled player, PDFs, animated **Lottie stickers**, download progress + tap-to-cancel |
| Send | Text via watch keyboard / voice dictation / emoji (system RemoteInput), recorded **voice messages** (Ogg/Opus), configurable **quick-reply chips**, and emoji **reactions** (long-press a message) |
| Message menu | Long-press any bubble: react 👍❤️🔥😂😮🙏 or **read aloud** via on-watch TTS |
| Notifications | MessagingStyle with inline **Reply** (incl. quick-reply choices) and **Mark read**; **smart mute** skips duplicates while the phone is connected |
| Glanceable | **Tile** with unread total + top chats (deep-links into them) and a watch-face **complication** with the unread count |
| Settings | Notifications, previews, smart mute, quick replies, font size, playback speed, stay-connected with a **battery floor**, media cache view/clear, **auto-clean by age**, in-app **updates from GitHub**, logout |
| Design | Wear Material 3, Yodai-green dark theme, pure-black background, round-screen aware, rotary scrolling, haptic ticks |

---

## How it connects to your phone's Telegram

The official Telegram app has no companion API, so YodChat is a **standalone client that logs into your own Telegram account** as an additional session (exactly like Telegram Desktop does):

1. Launch YodChat on the watch → it shows a **QR code**.
2. On your phone (S24 Ultra) open **Telegram → Settings → Devices → Link Desktop Device**.
3. Scan the watch screen. Done — the watch shows the same chats, groups and messages as your phone.

You can see and revoke the watch session anytime from the phone (Settings → Devices). Phone-number + SMS-code login (incl. 2FA password) is also available on the watch.

> Internet reaches the watch through the phone's Bluetooth proxy, watch Wi-Fi, or LTE — TDLib works over all of them, so the watch keeps working even away from the phone (on Wi-Fi/LTE models).

---

## Setup

### 1. Get Telegram API credentials (2 minutes, free)

Every third-party Telegram client needs its own API pair:

1. Visit **https://my.telegram.org** → log in with your Telegram account.
2. Open **API development tools**, create an app (any name/short name).
3. Copy `api_id` and `api_hash` into [local.properties](local.properties):

```properties
telegram.api.id=1234567
telegram.api.hash=0123456789abcdef0123456789abcdef
```

`local.properties` is gitignored — the credentials never leave your machine. Without them the app still builds, but shows a "API keys missing" screen.

### 2. Build

**Android Studio:** open the project folder, let it sync, press Run/Build.

**Command line** (a JDK 21 + Android SDK 36 toolchain was already installed under `%LOCALAPPDATA%` when this project was generated):

```powershell
$env:JAVA_HOME = (Get-ChildItem "$env:LOCALAPPDATA\Java" -Directory -Filter 'jdk-21*')[0].FullName
.\gradlew.bat :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 3. Sideload onto the Galaxy Watch 8

1. On the watch: **Settings → About watch → Software info** → tap **Software version** 5× to unlock developer options.
2. **Settings → Developer options → Wireless debugging → On** (watch and PC on the same Wi-Fi).
3. Tap **Pair new device** on the watch, then on the PC:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb pair <ip>:<pairing-port>   # code shown on the watch
& $adb connect <ip>:<port>        # the port on the Wireless-debugging main screen
& $adb install app\build\outputs\apk\debug\app-debug.apk
```

(or `.\gradlew.bat :app:installDebug` once connected).

---

## Updates & CI

Every push to `main` triggers the GitHub Actions workflow ([.github/workflows/build.yml](.github/workflows/build.yml)), which builds a signed release APK and publishes it as a GitHub release tagged `v1.1.<run>`. **Settings → Check for updates** downloads and installs the newest one directly on the watch (grant "install unknown apps" on first use).

- The updater talks to the **unauthenticated** GitHub API, so it only works while the repo is **public**. On a private repo it reports "can't reach updates" — download the release APK from GitHub and `adb install` it instead.
- CI needs two repository secrets (already set): `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`.
- [app/release.keystore](app/release.keystore) is committed deliberately: it pins a stable signature so CI builds install *over* local builds without uninstalling. It contains no account secrets — but anyone with the file can sign APKs that update this app, which is acceptable for a personal sideload project. Keep the repo private if that bothers you (and use manual installs), or swap in your own keystore.

---

## Notifications — how to configure

Two complementary paths; pick per situation:

| Scenario | What delivers the notification |
|---|---|
| **Watch near phone** (default) | The official Telegram app on the phone posts notifications and Wear OS **bridges them to the watch automatically** — inline reply included, zero watch battery cost. Keep YodChat's *Stay connected* off. |
| **Watch away from phone** (Wi-Fi/LTE) | Enable **Settings → Stay connected** in YodChat. A foreground service keeps TDLib online and YodChat posts its own notifications with Reply/Mark-read. Costs battery. |

Tip: if you enable *Stay connected* while the phone is also nearby, you may see duplicate notifications (one bridged, one local) — mute one side.

---

## Architecture

```
app/src/main/java/app/yodai/telewear/
├── TeleWearApp.kt / AppGraph.kt        Application + manual DI graph
├── telegram/
│   ├── TelegramCore.kt                 TDLib client owner + auth state machine (QR & phone login)
│   ├── ChatRepository.kt               Chat list folded from TDLib incremental updates
│   ├── MessageRepository.kt            Per-chat live thread: history paging, send, read receipts
│   ├── FileRepository.kt               File-id → local path (synchronous TDLib downloads)
│   └── Models.kt                       UI-facing MsgItem/ChatItem mapping layer
├── audio/
│   ├── VoiceRecorder.kt                MediaRecorder Ogg/Opus 48 kHz + Telegram 5-bit waveform
│   └── VoicePlayer.kt                  Shared ExoPlayer for voice notes
├── notifications/                      MessagingStyle notifications + Reply/MarkRead receiver
├── service/KeepAliveService.kt         Optional FGS with Wear OngoingActivity chip
├── settings/SettingsRepository.kt      DataStore: notifications, preview, font scale, keep-alive
└── ui/                                 Wear Compose M3: auth, chat list, thread, settings
```

Key decisions:

- **TDLib DTOs never reach the UI.** `Models.kt` maps them to small data classes at the repository boundary, so TDLib's frequent schema changes stay contained.
- **Client restarts are first-class.** After logout TDLib closes its instance; `TelegramCore` recreates the client and bumps a `generation` counter that makes every repository re-subscribe (`updates {}` helper).
- **Voice notes are spec-exact**: Opus in Ogg @ 48 kHz mono — the same encoding official clients send — plus a real amplitude waveform packed in Telegram's 5-bit format.

## Limitations (v1)

- Stickers/GIFs/videos/documents show as labeled placeholders; images and voice notes render fully.
- No secret chats (disabled by design — a watch is not a great place for them).
- No calls.
- New-account signup must be done on the phone first.

## Privacy & terms

This is a personal-use client of the official Telegram API. Using your own `api_id`/`api_hash` is required by [Telegram's API terms](https://core.telegram.org/api/obtaining_api_id). Session data (TDLib database) lives in the app's private storage on the watch and is removed on logout/uninstall.
