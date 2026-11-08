# Changelog

This changelog covers the community source snapshot. Official hosted builds may ship different
artifacts with the same upstream Aerial base.

## [1.85.001] - 2026-11-08

Maintenance release, based on Aerial 1.85 / versionCode 116. Version code `116001`.

### Changed

- Re-encoded the bundled quiz intro video at the same visual quality, reducing the APK from
  29.9 MB to 16.65 MB. Download and in-app updates are faster, and the APKs fit within the
  per-file size limit of the project website's static hosting.
- Published the 1.85.001 APKs on the project website so the in-app updater can find them
  without going through GitHub.

## [1.85.000] - 2026-11-08

First public source release, based on Aerial 1.85 / versionCode 116. Version code `116000`.

### Added

- Lock-screen quiz challenge with a bundled grade 1–6 sample question bank, offline voice cues
  and local sound effects. No account or network connection is used during a challenge.
- In-app update check and installer. The updater reads the project website first and falls back to
  GitHub Releases, then verifies the package name, signer and version code before installing.

### Changed

- Rebased on Aerial 1.85.
- The hosted remote-management entry is compiled out by default
  (`REMOTE_MANAGEMENT_ENABLED = false`). Local parental control and the LAN settings page are
  unaffected.

## [1.78.000] - 2026-11-02 (unreleased snapshot)

Pre-release source snapshot, based on Aerial 1.78 / versionCode 101. Version code `101000`. It was
prepared for publication but never published as a source repository.

### Added

- Local child mode with allowed windows, daily and per-session limits, rest periods, app
  allowlists and a separate study scope.
- Parent PIN, temporary unlock, lock screen, on-screen countdown and a TV parent center.
- LAN phone settings page for wallpapers, app layout, rules and parental settings.
- `standard` and `guard` build flavors for TVs that do or do not accept a sideloaded home app.
- Five interface languages: English, Simplified Chinese, Russian, Ukrainian and Kazakh.
- Public documentation: installation, building, architecture, privacy, security, contributing and
  third-party notices.

### Changed

- Community builds use the `com.planyourtv.tv` application ID and PlanYourTV launcher icons,
  banner and branding.
- The community build is local-first. It bakes in no backend endpoint, does not install the
  HTTP/MQTT policy adapters and hides the hosted remote-control UI. Official builds can opt in at
  compile time with `-PtvApiBase`.
- A persisted cloud mode in a build without a backend is normalized to `NONE`, so the TV does not
  get stuck on a binding screen.
- The local default-PIN warning now checks the PIN of the active mode, including local mode.
- Product text in the TV UI and phone page no longer presents the app as Aerial or compares it to
  Apple TV. Factual device-input labels such as an HDMI input named "Apple TV" are unchanged.

### Known limitations

- Only Xiaomi `standard` has been verified by this project on a real TV. Other devices and the
  `guard` flavor follow Aerial's upstream support and are not verified here.
- Remote control is a hosted service. Its server, parent-app source and device protocol are not
  published, and self-hosting is not supported.
- Android 4.x and Aerial Lite are not included in this snapshot.
- The community snapshot omits Aerial's Apple-hosted film catalogue. The aerial wallpaper and film
  screensaver are not offered; Home uses built-in or Bing wallpapers, and the screensaver falls
  back to photos or the clock.
- Fresh installs use the default PIN `123456`; change it before enabling child mode.
