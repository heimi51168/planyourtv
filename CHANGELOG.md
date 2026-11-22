# Changelog

This changelog covers the community source snapshot. Official hosted builds may ship different
artifacts with the same upstream Aerial base.

## [2.0.003] - 2026-11-22

Friendlier first-run defaults, free-form parent limits, and app names on every Kids-mode desktop.
Version code `148003`. The optional parent app is unchanged at `1.2.4` / `10`.

### Added

- Kids-mode desktops — the lock desktop, a timed session's Home and Study-mode Home — now name
  the focused app under its icon in Aerial's own style. Fixed action cards keep the names drawn
  in their artwork, and the old fixed hint line under the lock dock is gone.
- The parent Web accepts any non-negative whole number for the three unlock routes' daily counts
  and any positive minute count for the shared per-session cap; the previous 10-count and
  120-minute product caps are removed. The TV checks only the integer storage bound, so large
  values are kept exactly instead of wrapping.

### Changed

- First-run defaults are more forgiving: entertainment and study daily quotas are unlimited, the
  per-session cap is 600 minutes, each unlock route allows 1000 sessions a day, and
  【用户配置密码输入】 starts off. The quiz route is still the only route enabled by default.
  Parents can tighten any rule; saved quotas, route switches and counts keep their stored values.
- Editing a family member's dates or exemption window no longer asks for the member switch
  password again when one already exists. A new 6-digit password is required only when
  protection is turned on without one.

### Fixed

- The user configuration page no longer shows a child's planned countdown in the out-of-plan
  timer block. While a plan session is active it shows “TV in use · planned timer”, keeps the
  out-of-plan start blocked without minutes, and disables that block's exit action. A real
  out-of-plan timer still shows its countdown and can be ended normally.

## [2.0.002] - 2026-11-22

Per-member timed password exemption, the parent-center Web service status, smoother member
switching, fresh-install family defaults, and wallpaper and parent-app polish. Version code
`148002`. The optional parent app is `1.2.4` / `10`.

### Added

- Timed switch exemption per member. When a member's switch password is on, the parent can select
  weekdays and one same-day window (default 09:00–15:00, no days selected until the parent opts
  in). Inside that window the TV switches to that member without a PIN and the member row shows
  `09:00–15:00 免密`; outside it the PIN is still required. The parent Web family page now asks for
  the parent password again (a five-minute `FAMILY_EDIT` proof) and writes are checked on the
  server, so a child cannot quietly change member security settings.
- Parent Web service status on the TV parent-center QR screen: whether the LAN phone page is
  starting, available, offline, missing an address or not answering, plus the last phone access
  and a next-step hint. The access token is never shown.
- Fresh-install defaults. A data-empty new install starts with two example members — 儿子 and an
  editable 爸爸 with one 生活技能提升 preset level. Upgrades never change the existing roster, and
  a seeded member deleted by the parent is not recreated.

### Changed

- New and reset entertainment plans enable only the quiz route. Learn-to-earn and direct start
  stay off until the parent turns them on; existing saved rules keep their stored values.
- Family members use a consistent illustrated avatar set. The member sheet is drawn inline and
  highlights the selected row, so opening it no longer waits for a separate TV window.
- Parent app `1.2.4` removes the native title bar; the page header carries the title and the
  global menu stays as a floating button.

### Fixed

- Kids-wallpaper carousel: switching between a picture and a video no longer flashes a bright or
  black frame.
- Member switching: quiz data is loaded as a sparse snapshot, removing the pause when changing
  the active child.
- Parent Web layout: member switching hint and card spacing have more breathing room.

## [2.0.000] - 2026-11-21

Family members, three unlock routes, a built-in parent-fun question bank, a rebuilt parent app,
mixed wallpaper carousel, BGM upload and an online BGM library, and the move to Aerial 2.0.
Version code `148000`. The optional parent app is `1.2.3` / `9`.

### Added

- Family members. Each child gets their own avatar, rules, quiz scope, progress and time state; the
  desktop avatar switches members in one press, and switching also switches the active quiz bank.
  Cloud-connected builds can carry member-level configuration, while local builds keep everything
  on the TV.
- Three unlock routes: quiz, learn-to-earn and direct use. Each has its own on/off switch and daily
  count; all three share one entertainment daily quota and one common per-session cap. A single
  available route opens directly, and multiple available routes show the chooser instead of
  picking for the child. Unavailable routes explain why they are greyed out.
- Built-in parent-fun question bank. It is a separate level source for everyday life skills and
  parent-child practice, independent of textbook-sync content, and can be enabled per level like
  any other source.
- Kids-wallpaper carousel. Several images and videos can be mixed in one rotation, with separate
  BGM. BGM can be uploaded from the phone or fetched from the online music library in
  cloud-connected builds.
- Parent app 1.2.3. Rebuilt UI, remembered TV list, self-update checks, and the same five-language
  parent web page.

### Changed

- Manual import is easier to correct: a Clear button empties the input, multiple-choice answers are
  accepted as `A、C` or `answerIndices`, and failures name the exact item and reason.
- Adding a family member now requires a 6-digit switch password. The first migrated member is
  created as "大宝" with the documented default password so existing installations keep working.
- The web parent page removes the first-entry privacy popup, clarifies the daily-quota label and no
  longer offers an ambiguous "unlimited" date/window option. New members must have a password.
- The TV now shows its app registration number on the About page.

### Fixed

- LeTV process-kill handling: the guard now restores the lock desktop when the launcher is
  reclaimed in the background. The author does not own a LeTV TV; this path was reproduced by
  simulating a process kill and still needs verification on real LeTV hardware.
- Quiz audio: starting a quiz stops the intro and takes exclusive background-audio focus; when the
  answer countdown ends, music and speech stop instead of continuing into feedback or the desktop.
- Wallpaper carousel: switching a video no longer leaves a black frame or freezes on a still.
- Unlock routing and timers: temporary parent unlock no longer shows a second PIN prompt; study
  and reward durations are independent inputs; the focus ring counts down to the reward; the
  no-accessibility fallback still brings the child back to the lock desktop.
- General stability and layout fixes across the lock screen, control center and parent page.

## [1.97.003] - 2026-11-17

Multiple-choice quiz rounds, optional DeepSeek voice reading, unlimited local wallpaper videos,
kids-wallpaper sound and the temporary-unlock and timer-audio fixes, based on Aerial 1.97 /
versionCode 142. Version code `142003`. The optional parent app is `1.2.2` / `8`.

### Added

- Quiz levels can mix single-choice and multiple-choice questions. A multiple-choice question needs
  at least three options, at least two correct answers and one wrong option; the quiz screen marks
  the question type and asks the child to confirm the selected set. Manual import accepts
  `answer: "A、C"` or `answerIndices: [0,2]`, and import failures now name the exact item and reason.
  The built-in bank adds 5,439 multiple-choice questions to the existing 5,500 single-choice ones.
- Optional DeepSeek voice reading. After the parent enables it in the phone's AI settings, the quiz
  reads the prompt and options, and reads the correct answer and explanation after a wrong or
  timed-out reply. The spoken language follows the question, and the parent can choose whether to
  read the prompt, options, answer and explanation. It uses the DeepSeek key and speech quota; a
  speech failure never blocks the quiz.
- Kids-mode wallpaper videos can play sound, on by default, only when the selected video has an
  audio track the TV can decode. Parent mode stays muted, and the switch does not affect the quiz
  intro or answer sounds.
- The optional parent app can remember several TVs, switch between them from a device list, and
  give each one a local name. Re-scanning after a DHCP address change updates the existing entry
  instead of adding a duplicate; the TV token is the device identity. Names, the list and the
  current selection stay on the phone.
- The optional parent app checks the official release metadata and updates itself. The APK is
  verified against its SHA-256, package name, version and signing certificate before Android's
  installer asks the user to confirm; there is no silent install.
- Release `notes.json` now carries a top-level `parent` object with the parent app's version,
  minSdk, SHA-256 and size. The existing TV update parser ignores it, so older TVs are unaffected.

### Changed

- Local wallpaper videos no longer have a duration, count or total-size limit. Each file is still
  capped at 128 MB, the TV keeps a free-space reserve, and several videos can be selected at once
  from the system photo picker. Quiz intros keep their separate 15-second / 16 MB limit.
- The parent app requests `REQUEST_INSTALL_PACKAGES` in addition to `INTERNET` and `CAMERA`; the
  permission is used only for its own update. It still has no account, analytics or cloud storage.
  Update checks contact the project site and, as a fallback, the project's GitHub Releases.
- Logging out of the parent page now opens the saved device list instead of discarding the pairing.
  Each TV keeps its own WebView session when switching.
- An existing single saved TV address migrates automatically to the device list.
- Parent app dialogs and the overflow menu use rounded cards, pill buttons and the shell's brand
  colors instead of the stock square AppCompat panels. The Control page shows device and
  service-configuration status, the update dialog shows the current and latest versions, and the
  current TV link can be copied.
- The parent app's Refresh action reloads the current document instead of re-opening the saved
  `#parent` address. WebView treats that address as an in-page fragment once the page has consumed
  it, so the old page and assets could stay on screen after a TV web update.
- Quiz levels can be paused, and the quiz screen shows the question type and available counts. A
  whole textbook bank can be cleared.

### Fixed

- Speech that is still playing stops when the answer timer ends, an answer is submitted, the
  feedback page opens or the child leaves the quiz, instead of continuing into the next question
  or the desktop.
- Temporarily leaving kids mode no longer returns to kids mode after another app opens or the
  launcher process is reclaimed. A live temporary unlock is restored within the same boot when the
  screen has not been turned off and the TV has not gone into standby, and before its deadline; a
  real reboot, screen-off or the parent returning to kids mode still restores the child guard.
- The phone page's entertainment and learning app icons can be selected again, the local upload
  button and the `.json` / `.txt` file picker respond, and level switches are tappable.
- External playback pauses after the screen locks, and the lock-screen source entry opens the TV
  input list only after a quiz round.

## [1.97.001] - 2026-11-16

Kids-mode wallpaper import, quiz-entry and update-flow refinements, based on Aerial 1.97 /
versionCode 142. Version code `142001`. The optional parent app is `1.1.1` / `5`.

### Added

- Kids-mode wallpapers can now be imported from the phone: JPG/PNG/WebP images and MP4/MOV videos
  are supported, alongside the official cloud wallpaper library. Imported images are validated and
  fitted for the TV screen; videos are transcoded for wider TV compatibility. A built-in kids default
  wallpaper is included.
- Configured entertainment apps can act as quiz entries on the lock desktop. A successful round
  opens the app that started the quiz once the earned session begins. The phone controls whether
  the dedicated lock-screen quiz entry is shown, without disabling quiz entry from entertainment
  apps, and a round can be set to a single question.
- The phone's More page shows the TV's current version and supports checking, downloading and
  installing a newer release manually.

### Changed

- Auto update and Software update moved from Local settings to Other on the phone's More page.
- The lock screen no longer shows a single-session timing shortcut; manual sessions are started
  from the phone's Control page.
- Local video uploads accept a wider range of MP4/MOV codecs.

### Fixed

- Logging out of the phone parent app now returns to the native QR pairing screen instead of the
  TV PIN page.
- Back from Quiz management returns to More when the page was opened there, instead of the Bank
  tab.
- The phone Control page recovers automatically when its editor lease briefly expires.

## [1.97.000] - 2026-11-15

Kids-mode desktop, separated wallpapers, wrong-review levels and phone-first Parent Center, based
on Aerial 1.97 / versionCode 142. Version code `142000`. The optional parent app is `1.1.0` / `4`.

### Added

- A wrong-review level on the phone page. One level draws from every local question whose latest
  answer was wrong, across subjects and tags; the parent can add, rename or delete it. A correct
  answer removes that question from the pool. The level can start only when the pool can fill the
  whole configured round, so a short review never turns into an easier round than other levels.
- A fresh TV starts with two ready-to-use levels: an all-subject grade-4 first-semester midterm
  level with 500 mixed-subject questions first, then the focused maths level. Existing installs
  keep the levels their parents designed.
- The phone page has a dedicated Control section: temporarily leave child mode, return to child
  mode, end the running timer, and start one manual session. The TV validates every action again,
  keeps used time, and asks for the parent password and confirmation where the same action would
  on the TV.
- Kids mode has a desktop-style lock screen and Home: a large clock, mode and date, today's
  availability and rules, the same status pill as Home, and a bottom dock for study, the quiz,
  manual timing and Parent Center. The configured video wallpaper can play on these surfaces.
- Child mode and parent mode keep separate wallpapers. While the guard is enforcing an active
  child session, the lock screen and Home use the wallpaper selected for the child; parent Home,
  Study mode, temporary parent unlock and child mode off keep the owner's Aerial wallpaper. When
  the child wallpaper is missing or unreadable, the stock picture is used instead of exposing the
  parent's wallpaper.

### Changed

- Parent Center configuration is now centred on the phone page: time and apps, quiz levels, the
  question bank, media and wallpapers, auto-update and control actions are edited there. The TV
  Parent Center keeps the child-mode switch, status and QR entry and no longer blocks the phone
  editor; a second phone still has to take over explicitly.
- The optional parent app scans with a CameraX preview and native zxing-cpp decoding. Continuous
  focus, tap-to-focus, pinch or slider zoom and the torch make the TV QR easier to read at an
  angle, in low light or from farther away. Parent app `1.1.0` / `4`.
- The Aerial base moves from 1.93 to 1.97 (versionCode 131 to 142). Upstream fixes include XGIMI
  Home chooser and accessibility guard recovery, held volume keys, recent apps on Skyworth and
  Dangbei, inputs on Skyworth, JMGO, Hisense, LeTV, HS350 and Redmi, background-restart and boot
  black-screen fixes, resumable in-app updates with a fallback when the installer page crashes,
  and the weather state in diagnostics. These upstream fixes were not re-tested on every model by
  this project; device support follows Aerial.

### Fixed

- A wrong-review level no longer starts with fewer mistakes than the configured round; the TV
  shows the shortfall and the phone shows the available count.
- The TV Parent Center can be closed with Back or Home again, and the phone control page no
  longer flashes the Parent Center while it loads.
- Parent Center and the phone page no longer wait on each other: opening the TV Parent Center
  does not lock out a phone that is already editing.

## [1.93.005] - 2026-11-12

Parent app scanner update, based on Aerial 1.93 / versionCode 131. Version code `131005`. There
are no TV behavior changes in this release.

### Fixed

- The optional parent app now captures QR codes with CameraX (Camera2) and keeps ZXing only as
  the decoder. The previous zxing-android-embedded 4.3.0 capture path used the legacy Camera1
  API, which focused poorly on some current Android phones: the TV QR code was only recognized
  intermittently even though the same code scanned instantly in a browser. Parent app version
  `1.0.2` / `3`.

## [1.93.003] - 2026-11-12

Quiz configuration, all-subject question mix and Parent Center update, based on Aerial 1.93 /
versionCode 131. Version code `131003`.

### Added

- Wrong-answer and timeout feedback can now hold for 1–30 seconds before the next question
  (default 3 seconds). The TV shows the remaining time and disables Continue until it ends, and
  the engine rejects an early close. Correct answers still advance immediately. Existing configs
  and sessions migrate once so an old 30-second default does not become a 30-second hold.

### Changed

- The answer-time limit is now 15 seconds to 30 minutes, in 30-second steps, with direct numeric
  entry within that range. Policy, AI generation and question import use the same 1,800-second
  upper limit.
- The parent web page replaces preset rollers with always-visible steppers for questions per
  round, answer time and minimum answer display time. Advanced quiz settings and the generation
  options are expanded instead of hidden behind “More settings”.
- The parent-web threshold field is labeled “TV time threshold (minutes)” instead of reusing the
  “Temporary unlock” label.
- Parent Center moves the two QR codes to a dedicated question-bank settings page: the parent web
  page and the optional parent app are placed in opposite corners with separate instructions.

### Fixed

- All-subject (`subject=ALL`) question selection now buckets by subject and rotates between them
  while preserving FIFO order within each subject, so a short round no longer fills with the
  first subject's questions.
- The installer and installation docs pass `-i com.planyourtv.tv`, so Android records PlanYourTV
  as the installer of record and in-app silent updates can work without a manual reinstall.

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
