# Install on a TV

This guide is for a release APK built from this repository. It uses ADB because Home Guard and a
few system integrations need permissions that cannot be granted from the TV's normal UI.

Only install on a TV you own or are authorized to manage. Keep a working ADB connection until you
have checked that Home still works. The install replaces the home screen; if the app is removed or
misconfigured, the TV can be left without its normal launcher.

## Quick start with an AI agent

The fastest path is to give the copy-paste prompt in the [README](../README.md#让-ai-帮你安装) to a
desktop AI agent that can run commands. The full Chinese guide for agents, including per-brand ADB
instructions, permission explanations, verification and troubleshooting, is
[docs/AGENT-INSTALL.md](AGENT-INSTALL.md).

Use only the official downloads:

- [planyourtv-standard.apk](https://planyourtv.me/download/planyourtv-standard.apk)
- [planyourtv-guard.apk](https://planyourtv.me/download/planyourtv-guard.apk)

Do not use third-party APK sites, app stores, ADB enablers, or other tools. Do not uninstall or
disable the stock launcher or any system app.

## Before installing

- The TV must run Android 5.0 or newer. Android 4.x and the Aerial Lite build are not part of the
  community source snapshot.
- Enable developer options and USB or wireless debugging on the TV. Prefer a USB cable. If you use
  wireless ADB, turn it off when you are done and never expose port 5555 to the internet.
- Install the Android SDK platform tools on your computer and check that `adb devices` shows the TV
  as `device`, not `unauthorized`.
- Download the APK from the official site and verify its published SHA-256 checksum where one is
  provided. Official APKs are release-signed by the project. A build you make from source without a
  signing configuration is debug-signed and cannot overwrite an official install.

The two flavors differ only in their manifest:

| Flavor | Declares a home activity | Use it when |
|---|---|---|
| `standard` | Yes | The TV accepts a sideloaded launcher, for example Xiaomi. |
| `guard` | No | The firmware refuses to install a package that declares a home activity. Home Guard takes over through accessibility instead. |

## Install the APK

```bash
adb install -r planyourtv-standard.apk
```

Use the `guard` APK instead if the install fails with a message about a home-screen activity or
`INSTALL_FAILED_...`. Do not edit the manifest to force it; use the flavor built for that case.

If Android reports a signature mismatch, an app with the same package name was signed by a
different key. Do not uninstall it blindly: `adb uninstall com.planyourtv.tv` removes the app's
data, including rules, question bank, wallpapers and photos. Confirm with the user that losing the
data is acceptable before uninstalling, then install the official APK again.

The package name is `com.planyourtv.tv`. The part after `/` in the component names below is the
source namespace (`app.orchard.tv`) and is intentional; it is not a second package.

## Grant the ADB-only permissions

Run these after every install or update that did not use an install helper:

```bash
PKG=com.planyourtv.tv

adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS

adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell appops set "$PKG" WRITE_SETTINGS allow
adb shell appops set "$PKG" REQUEST_INSTALL_PACKAGES allow
adb shell appops set "$PKG" GET_USAGE_STATS allow
adb shell appops set "$PKG" android:get_usage_stats allow
adb shell appops set "$PKG" ACCESS_RESTRICTED_SETTINGS allow
```

Why each one is used:

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | Read the Wi-Fi network name for display; location is not uploaded by the community build. |
| `WRITE_SECURE_SETTINGS` | Keep Home Guard enabled after firmware changes the secure settings. |
| `SYSTEM_ALERT_WINDOW` | Show the countdown and recover the launcher after boot. |
| `WRITE_SETTINGS` | Adjust screen brightness on devices where that uses the standard setting. |
| `REQUEST_INSTALL_PACKAGES` | Install an update that the user explicitly downloads in the app. |
| `GET_USAGE_STATS` | Detect which app is in the foreground on firmwares where accessibility cannot see it. |
| `ACCESS_RESTRICTED_SETTINGS` | Allow an accessibility service on Android 13+ when the app was installed from a file. |
| Notification listener | Let the TV rebind the guard after a firmware force-stop. |

On TCL and FFALCON firmware, these vendor-specific auto-start operations may also be needed:

```bash
for op in APP_AUTO_START APP_ASSOC_START AUTO_START ASSOC_START; do
  adb shell appops set "$PKG" "$op" allow
done
```

Unknown operations fail harmlessly on other firmwares.

Then allow the notification listener:

```bash
adb shell cmd notification allow_listener \
  "$PKG/app.orchard.tv.guard.RevivalListener"
```

Android 6 and 7 may not have the `cmd notification` command. In that case, append the listener to
the existing setting without removing any other listener:

```bash
LISTENER="$PKG/app.orchard.tv.guard.RevivalListener"
CURRENT=$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')
case "$CURRENT" in
  *"$LISTENER"*) ;;
  ""|null) adb shell settings put secure enabled_notification_listeners "$LISTENER" ;;
  *) adb shell settings put secure enabled_notification_listeners "${CURRENT}:${LISTENER}" ;;
esac
```

## Start the app once

```bash
adb shell am start -W -n "$PKG/app.orchard.tv.MainActivity"
```

Some firmware compiles the app inefficiently right after installation. After the launcher has
started, wait about five seconds and compile it ahead of time:

```bash
sleep 5
adb shell cmd package compile -m speed -f "$PKG"
```

## Make it the home app

On `standard`, Android should ask which home app to use the first time you press Home. Choose
PlanYourTV. If it does not ask, open the TV's settings and choose the home app there. Do not
disable or uninstall the stock launcher: keeping it installed gives you a way back.

On firmware that keeps sending Home to its own launcher, Home Guard takes over. It is an
accessibility service that answers Home and returns to PlanYourTV. Enable it from the TV's
Accessibility settings, or append it to the existing list and enable accessibility:

```bash
GUARD="$PKG/app.orchard.tv.guard.HomeGuardService"
CURRENT=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$CURRENT" in
  *"$GUARD"*) ;;
  ""|null) adb shell settings put secure enabled_accessibility_services "$GUARD" ;;
  *) adb shell settings put secure enabled_accessibility_services "'$CURRENT:$GUARD'" ;;
esac
adb shell settings put secure accessibility_enabled 1
```

The append is deliberate. Do not replace other services in that setting.

Check that Home Guard is actually bound, not merely listed in settings:

```bash
adb shell dumpsys accessibility | grep -i -E 'PlanYourTV|HomeGuardService'
```

On some Xiaomi firmware, a short standby can let the stock launcher show for up to a minute before
the sync adapter brings PlanYourTV back. Keep auto-sync enabled.

## Verify the install

Run these and keep the raw output. Do not report success without them:

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys window | grep mCurrentFocus
adb shell dumpsys package com.planyourtv.tv | grep versionName
```

- `resolve-activity` should return PlanYourTV for `standard`, or the user's chosen launcher for
  `guard` when Home Guard takes over through accessibility.
- After pressing Home, `mCurrentFocus` should contain `com.planyourtv.tv` (or the user confirms the
  PlanYourTV home screen is visible).
- `versionName` should match the APK you installed.
- If Home Guard is enabled, the accessibility setting should contain it, and `dumpsys accessibility`
  should show it is actually bound.

## Change the default PIN

Fresh installs use `123456`. Open PlanYourTV's parental settings and change the six-digit PIN before
enabling child mode. `123456` and `666666` are treated as default values and the parent screen shows
a warning while either is in use.

## Optional integrations

These are not required to use local child mode.

Set PlanYourTV as the system screensaver when the TV uses Android's standard screensaver. Some
firmware resets this on boot:

```bash
adb shell settings put secure screensaver_components \
  "$PKG/app.orchard.tv.dream.AerialDream"
```

Let the app turn ADB back on after the TV or firmware disables it:

```bash
adb shell am broadcast -a "$PKG.CONFIG" \
  -n "$PKG/app.orchard.tv.guard.ConfigReceiver" --ez keep_adb true
```

Keeping ADB enabled is a security risk. Use it only while setting up or debugging, and turn it off
when finished.

## Troubleshooting

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: the installed app was signed with another key.
  Uninstall it if you accept losing its data, then install again.
- **Firmware refuses a home-screen app**: use the `guard` APK.
- **Home Guard is enabled but does not run**: some TCL and Hisense firmware block sideloaded
  accessibility services. Allow PlanYourTV in the TV's security, auto-start and accessibility
  settings, then start the app again.
- **Home Guard stops after standby**: complete the permission and notification-listener steps.
  On Xiaomi, make sure auto-sync is on.
- **The app is slow after installation**: run the `cmd package compile` command above after the
  first start. TV debug builds are much slower than release builds and are not suitable for daily
  use.
- **The remote-control feature is missing**: the community build is local-only by design. Remote
  mode requires an official build with a backend endpoint and a paired hosted account.

## Uninstall

Switch the home app back to the stock launcher first if you can. Then:

```bash
adb shell am force-stop com.planyourtv.tv
adb shell pm uninstall com.planyourtv.tv
```

Uninstalling removes app data. If you keep ADB enabled, also turn it off. Other accessibility and
notification-listener services are not removed by the app and should be left alone.

## Security notes

The local phone settings page is served over plain HTTP on the TV's LAN while the app runs. Its URL
contains a per-run token, but anyone who can reach the TV and knows the URL can use it. Do not
port-forward it or use it on an untrusted network. See [../SECURITY.md](../SECURITY.md) and
[../PRIVACY.md](../PRIVACY.md).

More answers: [FAQ.md](FAQ.md) · Agent-oriented install guide: [AGENT-INSTALL.md](AGENT-INSTALL.md).
