# RedPlus IPTV

RedPlus IPTV is a native Android IPTV player project for users who already have a legal Xtream Codes-compatible IPTV account. It does **not** include IPTV servers, playlists, channels, copyrighted content, scraping, bypassing, or piracy sources.

## Main features

- Legal Xtream Codes login using server URL, username, and password
- Secure local session storage using Android Keystore encryption + DataStore
- Modern dark glassmorphism interface built with Jetpack Compose
- Live TV categories, channels, favorites, search, and recently watched
- PPV / live event detection from event-related categories and channel names
- Movies / VOD with categories, sorting, details, favorites, and resume support
- Series / shows with seasons, episodes, favorites, and watch progress
- AndroidX Media3 / ExoPlayer video player
- Playback controls, resize modes, retry UI, reconnect behavior, and progress tracking
- Basic EPG loading from Xtream short EPG when the provider supports it
- Global search across Live TV, events, movies, and series
- Favorites and history stored locally per account using Room
- Settings for account, player, UI, parental controls, data cleanup, and about/legal
- Phone, tablet, and TV-style D-pad friendly layout where possible

## Legal usage notice

This app is only a player. You must use it only with legal IPTV accounts and legally licensed content. The project deliberately avoids hardcoded IPTV sources, scraping, credential bypasses, unauthorized content access, or pirated playlists.

## Required tools

- Windows with PowerShell
- JDK 17 or newer
- Internet access during the first build

Android Studio is recommended for editing the project, but `BUILD.bat` can prepare a local Android SDK by itself if Android Studio / `ANDROID_HOME` / `ANDROID_SDK_ROOT` is not configured. The script downloads the official Android command-line tools from Google, installs Platform Tools, Android SDK Platform 35, and Build Tools 35.0.0, then writes `local.properties` automatically.

## How to build the APK on Windows

1. Extract the ZIP file.
2. Open the extracted `RedPlusIPTV` folder.
3. Double-click `BUILD.bat`.
4. On the first run, allow it to download Gradle and Android SDK packages.
5. Wait for Gradle to clean and build the debug APK.
6. The APK will be created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

The first run can take longer because it downloads Gradle, Android command-line tools, Android SDK Platform 35, Build Tools, and project dependencies. Later builds reuse the local cache.

## How to open in Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select the `RedPlusIPTV` folder.
4. Wait for Gradle sync to finish.
5. Run the `app` configuration on an Android device, emulator, tablet, or Android TV emulator.

## How to log in

Enter the legal account details provided by your IPTV provider:

- Server URL, for example `https://example.com:8080`
- Username
- Password

The app calls the Xtream Codes `player_api.php` endpoint to validate the account. If the account is valid and active, the dashboard opens.

## Common troubleshooting

### Build fails because Java is missing
Install JDK 17+ or Android Studio, then restart the terminal or run `BUILD.bat` again. If `winget` is available, this command installs Microsoft OpenJDK 17:

```bat
winget install Microsoft.OpenJDK.17
```

### Build previously failed with `SDK location not found`
This ZIP includes `scripts\prepare_android_sdk.ps1`. `BUILD.bat` runs it before Gradle, creates `local.properties`, and prepares a project-local `.android-sdk` folder when no Android SDK is already configured.

### Android SDK download fails
Check your internet connection, firewall, proxy, or DNS. The script needs access to Google Android repositories and Gradle/Maven repositories. Delete `.build-cache` and `.android-sdk` inside the project folder, then run `BUILD.bat` again if a download was interrupted.

### Gradle dependency download fails
Check your internet connection, proxy, firewall, or Android Studio Gradle settings. You can also open the project once in Android Studio and let it sync.

### Login fails with unsupported response
Some providers modify the Xtream API response. Check the server URL and make sure it is the Xtream API base URL, not an M3U playlist URL.

### Streams do not play
The provider may require a different container extension, may block the device/IP, or may return an unsupported stream format. Try another legal stream from your account.

### EPG is empty
EPG depends on provider support. If the provider does not expose EPG via Xtream API, the app will show empty guide states instead of crashing.

### HTTPS warning
The app uses whatever server URL the user enters. HTTPS is preferred when supported by the provider.

## Project structure

```text
RedPlusIPTV/
  app/
    src/main/java/com/redplus/iptv/
      data/       API, local database, secure session, repositories
      player/     Media3 player screen
      ui/         Compose screens, theme, navigation
      util/       formatting and helpers
  BUILD.bat       One-click Windows build helper
  scripts/
    prepare_android_sdk.ps1  Downloads/configures Android SDK when missing
  README.md       This file
```

## Privacy

- Passwords are encrypted before being written to DataStore.
- Favorites and watch history stay local on the device.
- No analytics, ads, trackers, or telemetry are included.
- Logout clears the saved session.


### Gradle cache lock fix

This project intentionally uses a local Gradle cache folder named `.gradle-user-home` beside `BUILD.bat`. This avoids the common Windows error:

```text
Timeout waiting to lock journal cache (C:\Users\<user>\.gradle\caches\journal-1)
```

If a future build error mentions a lock inside `.gradle-user-home`, close Android Studio and any Java/Gradle processes, delete the `.gradle-user-home` folder, and run `BUILD.bat` again. The folder is only a build cache and will be recreated automatically.

## Version 1.0.1 UI / Playback Fixes

This package includes the phone-focused UI and playback fix update:

- Fixed `SQLiteBlobTooBigException / CursorWindow` by moving large Xtream API cache JSON out of Room rows and into file-based cache storage.
- Added Android cleartext HTTP support because many legal Xtream providers still serve streams over `http://`.
- Improved the player HTTP data source with IPTV-friendly timeouts, redirects, and a RedPlus user-agent.
- Added automatic live stream fallback formats: `.ts`, `.m3u8`, and no-extension URL where applicable.
- Reworked screens for compact phone layouts: smaller headers, horizontal categories, tighter cards, compact channel rows, and cleaner error screens.
- Fixed dark text on dark background by forcing premium light text as the app content color.

If you update from an older test APK and still see old cached data, open Settings and press **Clear cache**, or uninstall the older APK once before installing this version.
