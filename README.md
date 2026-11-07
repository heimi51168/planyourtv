# PlanYourTV

Open-source TV-side parental control for Android TV, built on the Aerial launcher.

PlanYourTV adds a local child mode with app rules, study and leisure time, rest periods, a visible
countdown, a parent PIN and temporary unlock. Local parental control works without an account, a
server, or a cloud connection.

[中文说明](README.zh-CN.md) · [Install](docs/INSTALL.md) · [Build from source](docs/BUILDING.md) ·
[Architecture](docs/ARCHITECTURE.md)

> **Project status:** first public source release. The current community baseline is `1.85.000`,
> following upstream Aerial 1.85. Xiaomi `standard` is the only configuration tested by this
> project on a real TV. Other devices follow Aerial's upstream support and are not verified here.

## Features

- **Local child mode** — app allowlists, study and leisure time, rest periods and daily limits.
- **Flexible timing** — scheduled rules and manually started sessions, with a countdown on screen.
- **Parent controls** — a six-digit PIN, a parent center on the TV, and temporary unlock.
- **Home Guard** — keeps the launcher available on TVs whose firmware pins its own home screen.
- **Phone settings over the LAN** — the TV serves its own setup page while the app is running; no
  account is needed.
- **Five UI languages** — English, Simplified Chinese, Russian, Ukrainian and Kazakh.
- **No advertising or analytics SDK** — the community source does not include either.

## What is open

- The Android TV app and its local rules, PIN flow, lock screen and session timer.
- The generic client code used by the optional hosted mode.
- The build files needed to produce `standard` and `guard` APKs from this source.

## What is not open (yet)

- Remote control is provided by a separate hosted parent app and service. The server and parent-app
  source are not part of this repository.
- The device protocol is not documented yet and may change. There is no self-hosting promise for
  this release.

The community build defaults to local mode only: no backend endpoint is baked in and the remote UI
is hidden. An official build can opt in at build time with `-PtvApiBase=<endpoint>`.

## Install

For a release APK:

```bash
adb install -r PlanYourTV-standard-release.apk
```

Then open PlanYourTV from the TV's app list and choose it as the home app when Android asks.
`guard` builds are for firmwares that refuse to install a package declaring a home-screen intent;
see [docs/INSTALL.md](docs/INSTALL.md) for the full ADB setup, permissions, Home Guard and uninstall
steps.

Installing with plain `adb install` does not grant the ADB-only permissions used by Home Guard,
brightness and boot recovery. The app still opens, but some takeover features need the additional
commands in the install guide.

## Supported devices

| Device | Status |
|---|---|
| Xiaomi TV, `standard` flavor | Tested by this project |
| Other devices supported by Aerial | Inherited from upstream; not verified by this project |
| `guard` flavor | Inherited from Aerial; not verified by this project |
| Android 4.x (`Aerial Lite`) | Not included in the community source snapshot |

Only install on a TV you own or are authorized to manage. A home-screen replacement can leave a TV
without its normal launcher if it is removed or misconfigured. Keep a way to reach ADB before
installing.

## Privacy

Local parental control does not require an account and does not upload rules or app lists. The app
does make optional network requests for weather, daily wallpapers and update checks, and it serves
a local settings page on the LAN while it runs. See [PRIVACY.md](PRIVACY.md) for the full list.

## Security

PlanYourTV is a launcher with elevated capabilities granted over ADB, including accessibility,
notification-listener and secure-settings access. It is a parental-control aid, not a tamper-proof
boundary against the TV's owner or someone with ADB access. Change the default PIN before enabling
child mode. See [SECURITY.md](SECURITY.md) for reporting and hardening guidance.

## Legal notice

PlanYourTV is an independent project based on [Aerial](https://github.com/pengchujin/aerial-tv).
It is not affiliated with, endorsed by, or sponsored by Apple, Xiaomi, TCL, or the Aerial project.

The community snapshot does not include Aerial's Apple-hosted film catalogue. The aerial wallpaper
and film screensaver are therefore not offered; Home uses built-in or Bing wallpapers, and the
screensaver falls back to photos or the clock. Weather data comes from Open-Meteo, city data from
GeoNames, and daily wallpaper images from Bing.

## License

MIT. Aerial's copyright notice and third-party notices are preserved in [LICENSE](LICENSE),
[NOTICE](NOTICE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Contributing

Issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first. Do not include
television IP addresses, ADB keys, signing files, account credentials, or third-party media in a
report.
