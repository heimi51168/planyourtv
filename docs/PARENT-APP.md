# Parent phone shell

The repository contains an optional Android phone app (`:parent`) that opens the Parent Center
page served by the TV over the local network. It is a browser shell, not a second parent app: the
page, its PIN gate and its data stay on the TV.

## What it does

- Scans the QR code shown in Parent Center on the TV, or accepts the complete address encoded
  in that code.
- Remembers the address, so the code is scanned once.
- Shows the page full screen in a WebView, with the browser behavior the page relies on:
  file import, JSON export through the system save dialog, copy to clipboard, and external links
  in the system browser.
- Refreshes the page and can pair with another TV from the menu.

It has no account, no cloud service and no connection to any server other than the paired TV. Its
only permissions are `INTERNET` and `CAMERA`. The admin session is stored in the WebView's local
storage, exactly as it would be in a browser; uninstalling the app or clearing its data removes it.

## What it does not do

- It does not include TV-native settings that are not part of the web page, such as initial
  binding, migration, or guard-service controls.
- It does not work when the phone and the TV are on different networks, or when the TV's local
  Parent Center is not running.
- It is not a TV APK and is not installed by the TV installer. The TV release still contains only
  `standard` / `guard`; the parent app is a separate optional download.

## Download

The official optional build is served from the project site:

```text
https://planyourtv.me/download/planyourtv-parent.apk
```

GitHub fallback:

```text
https://github.com/heimi51168/planyourtv/releases/latest/download/planyourtv-parent.apk
```

It requires Android 7.0 (API 24) or newer. Open the link in the phone's browser, allow the browser
to install unknown apps, install the APK, and open it. The phone and TV must be on the same Wi-Fi
when pairing. This is not a TV APK: do not install it on the television.

## Build

The module targets Android 7.0 (API 24) and newer. It is built with the same Gradle wrapper as the
TV app:

```bash
./gradlew :parent:assembleDebug
adb install -r parent/build/outputs/apk/debug/parent-debug.apk
```

A release build (R8 and resource shrinking) is produced the same way:

```bash
./gradlew :parent:assembleRelease
```

The release build reads `local/parent-signing.properties`, separate from the TV app's key. Without
one, a local release build is signed with the local debug key and must not be distributed as an
update. Official builds must use a stable parent-only key; never reuse the TV release key.

## Pairing

1. On the TV, open Parent Center so the QR code is visible.
2. On the phone, open the app and tap the square scan button. If the camera is unavailable, tap
   "Can't scan? Enter the address manually" and type the complete address encoded in the QR code,
   including the IP, port and the code after them (for example
   `192.0.2.10:8790/abcd2345/quiz`).
3. Use the menu to refresh, open the page in the system browser, or scan a different TV.

The phone and the TV must be on the same Wi-Fi network, and the TV's address and token must not
change. If the page cannot be reached, check that Parent Center is open on the TV and try again.

See [BUILDING.md](BUILDING.md) for the general toolchain and build requirements.
