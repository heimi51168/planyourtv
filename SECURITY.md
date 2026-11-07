# Security policy

## Reporting a vulnerability

Please do not open a public issue for a vulnerability that could expose users' data, devices, or
networks.

Use GitHub's private security advisory form for this repository:

https://github.com/planyourtv/planyourtv/security/advisories/new

Include the affected version, build flavor, Android version, reproduction steps, and the impact.
Do not include real device IP addresses, account credentials, ADB keys, signing files, or personal
photos. If a report also affects upstream Aerial, report it to that project as well.

There is no bug bounty. Reports are handled on a best-effort basis.

## Supported versions

Only the latest community source snapshot and its release builds are supported. Older builds may
contain known issues fixed upstream or in the local policy module.

## Security model and limitations

PlanYourTV is a home-screen replacement. To keep working on China-market TV firmware it requests or
is granted capabilities that ordinary apps do not have:

- accessibility service (Home Guard) to answer the Home key and step in front of the stock launcher;
- notification listener and sync adapter to recover after a firmware force-stop;
- `WRITE_SECURE_SETTINGS` to keep Home Guard and optional ADB recovery enabled;
- usage-access permission to detect which app is in front;
- overlay permission for boot recovery and the countdown;
- `QUERY_ALL_PACKAGES`, because a launcher must list launchable apps;
- install-related permissions for the in-app updater;
- fine-location permission granted over ADB only, used to read the Wi-Fi network name.

These permissions are powerful. A malicious build, a compromised update channel, or someone with
ADB access can use them against the TV's owner. Install only builds from a source you trust and
verify the published checksum and signing certificate.

This project is a parental-control aid, not a tamper-proof security boundary. A child or another
person with the TV remote, device-owner access, root, or ADB can potentially bypass or disable it.
It must not be used as the only protection for sensitive accounts, payments, or physical safety.

## Default PIN

Fresh installs use the well-known PIN `123456` so the first unlock always has a way in. `123456`
and `666666` are treated as default values. Change the PIN before enabling child mode. The app
shows a warning while a default PIN is in use, but it cannot force a change.

## Network exposure

- The built-in phone settings page listens on the local network over plain HTTP while the app is
  running. Its URL contains an unguessable per-run token. Do not expose that port to the internet,
  port-forward it, or use the page on an untrusted network.
- The community build has no backend endpoint baked in and disables the hosted-mode UI and network
  adapters. Official builds that opt in with `-PtvApiBase` connect to a hosted service and have a
  different privacy surface.
- ADB is a privileged debugging interface. Turn wireless ADB off when it is not needed, and never
  expose port 5555 to the internet.

## Release integrity

Official hosted builds and community builds use different signing identities and cannot update
over each other. A community APK installed over an official release, or the reverse, requires an
uninstall and loses app data. Release notes should publish a SHA-256 checksum; verify it before
installing.

## Secrets

Never commit `local/`, `local.properties`, signing keys, MQTT credentials, API tokens, television
IP addresses, or household information. The public source snapshot is generated from a whitelist;
do not paste private build configuration into an issue or pull request.
