# Changelog

This changelog covers the community source snapshot. Official hosted builds may ship different
artifacts with the same upstream Aerial base.

## [1.93.002] - 2026-11-12

Parent app, parent-web unlock and textbook coverage update, based on Aerial 1.93 / versionCode
131. Version code `131002`.

### Added

- Optional Android phone parent app (`:parent`, `com.planyourtv.parent`, Android 7.0+). It scans
  the Parent Center QR code or accepts its full address, remembers the TV, and opens the same
  parent web page full screen in a WebView. File import, JSON export, clipboard and external
  links keep working. It has no account or cloud service and only requests `INTERNET` and
  `CAMERA`; it is an optional third APK in the same GitHub Release, not a TV APK.
- Temporary unlock on the parent web page. While the TV is on its lock screen, a parent can enter
  the 6-digit parent password to unlock it temporarily. It does not start, end or pause a timed
  session, leave study mode, or change the guard or system settings; turning the screen off or
  restarting the TV restores the guard. The password is used only for that unlock and is not
  stored by the page.
- In manual mode, the same “Me” page can start one timed session (5–120 minutes) with the parent
  password, so the parent does not have to type it on the TV. Temporary unlock and manual start
  share one failed-attempt limiter.
- The Parent Center keeps the parent-web QR code visible in local kids mode, in both automatic
  and manual modes, and even when the quiz is disabled, so the question bank stays reachable. A
  second static QR code downloads the optional parent app without depending on the TV's local
  server.
- Curriculum catalogue 2026.5: 302 textbook volumes across 10 subjects and 43 editions, with
  2,205 catalogue units (1,970 `UNIT` nodes) and no whitelist gaps. This adds 西南大学版 primary
  maths grades 1–6, 外研社 English grades 1–6, 中图版 geography grades 7–8, and 北师大版 physics
  grades 8–9 editor variants; 译林版 English covers grades 3–6 because the source has no grades
  1–2 books.
- The preset bank now holds 5,500 first-semester midterm questions in 55 grade / subject /
  textbook combinations, including 600 new 西南大学版 maths questions for grades 1–6.

### Changed

- Switching between automatic and manual mode now takes effect immediately. A running timed
  segment and the old mode's rest countdown are ended, so the new mode never inherits the
  previous break. Daily usage and the configured time windows are unchanged, and the confirmation
  dialog warns when a running timer or rest countdown will be discarded.
- Lock-screen actions on the parent web page are consolidated into two rows on the “Me” page:
  temporary unlock and a timed manual start.
- Parent app address examples use the RFC 5737 documentation range (`192.0.2.0/24`) instead of a
  private LAN address, so the public source contains no home-network example.

### Fixed

- A rest countdown started in one mode no longer follows the TV into the other mode or returns
  after a process restart.

## [1.93.000] - 2026-11-11

Question-bank, installer and parent-settings update, based on Aerial 1.93 / versionCode 131.
Version code `131000`.

### Added

- Manual question-bank import: on the phone settings page, upload a `.json` / `.txt` file or
  paste questions, choose an existing tag or create a new one, then preview duplicates and free
  space before importing. Only single-choice questions are accepted. Questions and tags stay on
  the TV: no AI service and no upload. An imported tag can then be used as a custom level.
- Auto update in Parent Center. On TVs that allow silent installs, a new version downloads in
  the background and installs after another app has been in front for a while; all settings are
  kept. On other TVs the update remains a manual Download and Install in Control Center.
- A confirmation dialog when switching between automatic and manual modes. It explains how the
  active timing rules change, so one stray OK cannot replace the parent's setup.
- Configurable inputs in Control Center → Inputs → Input Settings: choose which ports appear in
  the list and on Home, whether the TV switches when a connected device turns on over HDMI-CEC,
  and which input to open when the TV turns on or wakes from standby.
- Native installers for Windows, macOS and Linux: one entry point per platform, LAN TV discovery,
  standard-to-guard retry and guided permission setup, with manual ADB steps as the fallback.
- Launcher preflight in the native installers. PlanYourTV is itself the home screen, but Android
  allows other launchers to stay installed; the installer detects CleanYourTV, Aerial / HaloTV,
  Quiet TV and any other third-party HOME app, reports them, and continues without uninstalling by
  default. An explicit `--remove-launchers` / `-RemoveLaunchers` removes them first and deletes
  their app data. The TV's stock launcher and system apps are never touched.

### Changed

- Public releases now ship two APKs, `planyourtv-standard.apk` and `planyourtv-guard.apk`. If a
  firmware refuses the standard launcher build, the installer automatically retries with guard.
- PlanYourTV requires Android 5.0 (API 21) or later. Android 4.x is not supported, and the
  separate Lite APK is no longer published.

### Fixed

- The phone settings page starts when port 8790 is already used by another app, as Aerial can be.
  It falls back to a free port, and the QR code uses the port that was actually bound.

## [1.85.011] - 2026-11-10

AI provider and quiz flow update, based on Aerial 1.85 / versionCode 116. Version code
`116011`.

### Added

- AI provider presets for DeepSeek, Qwen, Kimi, GLM, Doubao/Ark, Hunyuan, ERNIE, Spark,
  MiniMax, LongCat and StepFun. A preset fills the endpoint and a sample model without
  overwriting a model the parent typed; the default remains DeepSeek.
- A free GLM preset (`glm-4.7-flash`), with thinking disabled and longer timeouts so slow
  free batches can finish.
- The generation page opens with a ready-to-run default: grade 4, first semester, math,
  人教版, unit 1 “一 万以上数的认识”. A parent can change it before generating.
- The quiz intro can be revealed with the remote's direction keys, and the start screen
  shows saved levels and the current round's net score.
- Parent settings on the phone page use a heartbeat, release the page after ten seconds of
  inactivity, and show when another parent is active or has taken over.

### Changed

- The API key field no longer suggests an `sk-` prefix or a provider-specific format. Keys are
  accepted as the provider issues them, including Spark's `APIKey:APISecret` form.
- OpenAI-compatible providers that reject optional request fields are retried with a minimal
  JSON request. Services without `GET /models` show a warning instead of a false key error.
- Model IDs from gateway providers may contain up to three `vendor/model` segments.
- Parent password dialogs share a consistent size and position across TV flows.

### Fixed

- Sessions that can no longer be decoded are isolated instead of breaking the quiz, and the
  `pullCount` value is bounds-checked.
- Generation tasks keep a 30-minute TTL and retry rate-limited (429) batches, so 20-, 30- and
  50-question targets do not fail on slow free tiers.

## [1.85.010] - 2026-11-09

Quiz, curriculum and scoring update, based on Aerial 1.85 / versionCode 116. Version code
`116010`.

### Added

- Grades 1–9 and junior-high subjects: the bundled curriculum matrix now drives grade, subject,
  textbook and unit limits for primary and junior high, including ethics and law, history,
  geography, biology, physics and chemistry.
- Built-in question bank expanded from 120 to 4,900 first-semester midterm questions for grades
  1–9, covering every supported subject and textbook combination used by the bank.
- Real textbook unit titles: the parent page and AI prompt now use the national smart-education
  platform catalogue (109 books, 765 nodes, 673 units), so questions target actual unit titles
  and lesson coverage instead of generic "Unit N" labels.
- Parent-designed quiz levels: save up to six named levels, each with its own answer scope, and
  let the child scroll between them on the TV.
- Difficulty scoring: independent rewards and deductions for easy, medium and hard questions, a
  minimum net score required to unlock, and a per-round cap. The web page previews the result of
  the configured rule.
- Answer scope on the TV: the child can choose an available scope before starting, and the
  confirmation panel shows the exact grade, subject, semester, textbook, scope and single-choice
  format.
- AI generation targets of 20, 30 or 50 questions, with progress reporting and a warning not to
  refresh the page while generating.

### Changed

- One round is now 5–50 questions in steps of 5 (default 5), with a 60-second limit per question.
  Difficulty rewards default to 1 / 2 / 5 minutes for easy / medium / hard, deductions default to
  zero, and the answer-display-duration setting was removed.
- The home generation page is consolidated into two cards; saved level cards wrap into 1 / 2–3 /
  4+ rows.
- The parent web page keeps a single active session. A second phone is shown an "another parent is
  using this page" message instead of competing with the first session.
- Parent PIN wording is unified as "家长密码".

### Fixed

- Batch generation no longer stops at 10 questions with an unparseable-response error. Complete
  questions are recovered from a truncated provider response and retried in smaller batches, so
  20-, 30- and 50-question targets finish and enter the bank automatically.
- Requests outside the selected textbook's unit range are rejected in the web UI and cannot be
  saved or sent to the provider. Every available unit is listed instead of only the first three.
- Existing midterm ("期中必考") questions remain compatible; the unit number stays the canonical
  key for the bank, cache, cloud and signatures.

## [1.85.006] - 2026-11-08

Quiz and settings update, based on Aerial 1.85 / versionCode 116. Version code `116006`. This
release rolls up the changes built since 1.85.001.

### Added

- AI question settings now link to the DeepSeek platform, where a parent can create the optional
  API key. The key remains optional, is stored only on the TV and is billed by the parent's own
  provider account.
- Quiz answer scope can cover all subjects and all textbooks, in addition to a custom selection.
- The TV's quiz start panel names the question bank in use, so it is clear whether questions come
  from the bundled bank or the family's own bank.

### Changed

- The quiz bottom navigation uses the home icon.
- The duplicate header after signing in was removed and the language picker moved into Me.
- The phone-side quiz page no longer shows a separate question-bank source hint.

### Fixed

- Automatic question import now reads the POST body of the request, fixing imports that previously
  failed even though the request was valid.

### Documentation

- Rewrote the public README, FAQ, AI installation guide and `llms.txt` around the child screen-time
  positioning of PlanYourTV.
- Documented that AI question generation is optional: the parent creates their own DeepSeek (or
  other OpenAI-compatible) API key, and the key is never requested by the AI installer or included
  in the public source.
- The installer now tries the official site first, rejects Android 4.x explicitly and no longer
  offers an Aerial Lite build. The public source pipeline adds an immutable release tag, gitleaks,
  a secret/private-identifier scan and an isolated no-key build.

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
