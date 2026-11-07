# Privacy

PlanYourTV is designed so that local parental control works without an account or a project-run
server. This document describes what the community source does. Hosted builds connect to a
separate service with its own policy.

## Local data on the TV

The app stores the following in its private app storage:

- parental rules, time budgets, schedules, app allowlists and blocklists;
- which apps are installed and their labels, because a launcher must list them;
- parent PIN hash and salt, never the PIN itself; failed-attempt counters and lockout state;
- the chosen wallpaper, screensaver, weather city and display settings;
- photos and videos added through the local phone page;
- cached weather and daily-wallpaper metadata;
- crash/diagnostic log lines with addresses masked when shown in the diagnostics report;
- local pairing state if the TV has been bound to a hosted account.

Clearing the app's data or uninstalling it removes this local data. Android backup is disabled
(`android:allowBackup="false"`).

## Local phone settings page

While PlanYourTV runs, it starts a web server bound to the local network. The page lets a phone on
the same network manage wallpapers, sources, icons, app layout, local rules and parental settings,
and upload photos or videos. The URL contains a random per-run token.

The page uses plain HTTP, not HTTPS. Anyone who knows the URL and can reach the TV's network can
use it while it is running. Do not expose the port to the internet or use the page on an untrusted
Wi-Fi network.

## Network requests in the community build

The community build has no backend endpoint baked in and hides hosted-mode features. It still
makes these optional requests:

| Feature | Destination | What is sent |
|---|---|---|
| Update check and download | GitHub Releases | Standard HTTPS request; IP address is visible to GitHub |
| Weather | ipinfo.io, Open-Meteo | Approximate IP-based location, or the city entered by the user |
| Daily wallpaper | Bing image service | Image request and market/locale |
| App icon artwork | Disabled in the community build | None |
| Phone settings page | Local TV network | LAN traffic only |

The community snapshot omits Aerial's Apple-hosted film catalogue, so it makes no requests to
Apple's film servers. There is no analytics, advertising, crash-reporting or attribution SDK in the
community source. The project does not receive telemetry from community builds.

## Hosted mode

An official build compiled with `-PtvApiBase` can enable the hosted service. When a parent chooses
to bind the TV, the app can upload the device identifier, model/system information, installed-app
inventory, local rules, allowed/study app lists and the parent password to that service, as
described in the in-app consent notice. Parental controls continue to run on the TV if the service
is unreachable.

The hosted service, parent app and their retention/deletion practices are not part of this
repository and are not covered by this document. Use the hosted app's privacy information and
unbind/delete controls for that data.

## Children's privacy

PlanYourTV is a tool for parents and guardians. It does not create child accounts, collect names,
birthdates, contacts, precise GPS location, or microphone/camera data. Parents should not enter a
child's personal information into rules, app names, wallpapers or device names. App inventory and
usage rules may reveal household app usage; protect the parent PIN and the TV's network.

## Changes

Material changes to this document will be listed in [CHANGELOG.md](CHANGELOG.md).
