# Architecture

PlanYourTV is an Android TV launcher built on the Aerial codebase, with a separate policy layer
for local parental control. The split exists so that upstream Aerial updates can be merged with
small, reviewable changes instead of maintaining a second launcher.

## Modules

```text
:app       Aerial-based launcher, Home Guard, UI and Android integration
:policy    Testable parental-control rules, sessions, PIN and storage
```

`:app` depends on `:policy`. `:policy` must not depend on `:app`; when it needs something from the
launcher it uses an interface implemented in `:app`.

The community snapshot does not include the private `lite/` module. The Android 4.x launcher is
outside this source release.

## Identity

- Installed application ID: `com.planyourtv.tv`.
- Kotlin/Android source namespace: `app.orchard.tv`.
- Main activity: `com.planyourtv.tv/app.orchard.tv.MainActivity`.
- Home Guard: `com.planyourtv.tv/app.orchard.tv.guard.HomeGuardService`.

The namespace is intentionally not renamed. Keeping Aerial's class paths makes upstream merges
mechanical, while the application ID gives PlanYourTV its own install identity and lets a community
build coexist with an official build.

The two flavors share all code:

- `standard` adds a `CATEGORY_HOME` activity through `app/src/standard/AndroidManifest.xml`.
- `guard` has no home declaration and relies on the accessibility service for takeover.

## Integration points in Aerial

The policy layer is wired in at a small number of places:

- `MainActivity` and `HomeViewModel` call `PolicyProduct.install()` and render the parent center,
  lock screen, countdown and PIN UI when the coordinator says they are active.
- `HomeViewModel` filters the app list through the active access policy and reloads when the
  policy revision changes.
- `HomeGuardService` checks the foreground package against the access policy, brings Home back
  when an app is blocked, handles screen-off/on, and implements `PolicyHost` for the policy
  runtime.
- `BootReceiver` starts the same installation path after boot.
- The Aerial settings sheet and Control Center expose an entry to the parent center. The remote
  row is hidden when the build has no hosted backend.

Aerial's device profiles, brightness control, Home Guard main loop and launcher UI are otherwise
left alone. Product code should not be copied into Aerial files when a hook in `PolicyProduct`
will do.

## Runtime layers

### `PolicyProduct`

The app-side facade. It:

- reads `BuildConfig.CLOUD_ENABLED`, which is true only when a backend was baked in;
- installs the hosted HTTP/MQTT adapters only in that case;
- starts the app-inventory sync and the mode coordinator;
- exposes the parent-center visibility state to Compose.

### `PolicyModeCoordinator`

Owns the one active mode: `NONE`, `LOCAL` or `CLOUD`.

- `NONE`: the launcher behaves as a plain Aerial home screen. No lock, no PIN gate, no session.
- `LOCAL`: local rules and the local session engine run without any server.
- `CLOUD`: the optional hosted client runs. It may use the same local rule executor after a
  one-time handoff, or the older redemption model.

Only one engine is active. The coordinator exposes a single UI state and a revision counter. It
rejects a cloud switch when the build has no backend and sanitizes a persisted cloud choice to
`NONE` in a community build, so a TV cannot be stranded on a binding screen that can never finish.

Cloud mode is compile-time capability, not a hidden account feature. Without `-PtvApiBase`, the
HTTP/MQTT client classes are not installed at all.

### `PolicyRuntime`

The hosted-mode facade in `:policy`. It keeps the binding and command state, applies cloud PIN
changes to the local store, syncs usage and exposes session state to the UI. It talks to the host
through `PolicyHost` for actions such as bringing Home to the front or locking the screen.

The network client and command verifier live in `:app` under `app/orchard/tv/policy/`, because
they are Android/HTTP-specific. The server protocol is not part of this repository.

### `LocalPolicyRuntime`

The local executor in `:app`. Automatic rule sessions and parent-started manual sessions share
one timer, daily ledger, rest timer and app scope. Study mode changes the visible app scope but
does not start a timed ledger session.

The executor never reads the network. It reads rules from local storage and reports usage through
the policy module's usage sink. If the app process is killed, the next start restores a saved
session and reconciles wall-clock time.

### `:policy` building blocks

- `LocalRules`: pure model of allowed windows, daily/session limits, rest, and the entertainment
  and study package scopes. Validation and schema migration are pure functions.
- `LocalScheduleEngine`: decides whether a rule session may start, must end, or is blocked by a
  window or daily limit.
- `PolicyExecutionPlan`: immutable snapshot of the budget, deadline, app scope and end reason used
  by the executor. Running plans are not rewritten by later rule changes.
- `RuleUsageStore` and `RuleUsageEventFactory`: durable usage ledger and event construction.
- `PolicySession` and `SessionTiming`: session state, countdown and end reasons.
- `LocalPin`, `PolicyPin` and `TvPinStore`: salted PIN hashing, failed-attempt lockout and the
  local/cloud PIN slots.
- `PolicyAccessPolicy`: decides whether a foreground package is allowed. The all-apps sentinel is
  expanded against the installed launchable apps by the host.
- `PolicyGuardSelector`: chooses the active guard for a bound cloud device, including the safe
  fallback when a backend has not sent a usable policy.
- `PolicyHost` and `PolicySyncHost`: interfaces implemented by Aerial so the policy module does not
  depend on it.

All Android-free logic is covered by JVM tests in `policy/src/test`.

## Local control flow

1. The parent opens the parent center and sets rules: windows, daily/session limits, rest, study
   apps and entertainment apps.
2. `LocalPolicyStore` persists the rules. The pure `LocalRules` model validates them.
3. `LocalScheduleEngine` evaluates the current day and minute against the rules.
4. When a session is allowed, `LocalPolicyRuntime` creates a `PolicyExecutionPlan` and starts the
   countdown. It records usage locally and writes periodic checkpoints.
5. While a session is active, `PolicyAccessPolicy` defines the allowed app set. Outside allowed
   apps, `HomeGuardService` returns the TV to PlanYourTV. Study apps remain available in study
   mode.
6. At a session limit, daily limit, window end or parent stop, the plan ends with a recorded
   reason. Rest time and parent temporary unlock are separate states.
7. A parent PIN unlocks the parent center or a temporary session. The PIN is verified using a
   salted hash and a failed-attempt lockout; the plain PIN is never stored.

## UI composition

The policy UI is additive Compose code under `app/src/main/java/app/orchard/tv/ui/policy/` and
related Aerial surfaces:

- the parent center overlay, opened from Control Center;
- the PIN pad and lock screen;
- the countdown overlay drawn by an Android overlay window managed by Home Guard;
- parent-settings rows in the wallpaper/personalization sheet;
- local app filtering on the Home grid.

Aerial's grid geometry, focus model, motion curves and `Pressable` component are reused. Policy UI
must not add continuous idle animations on TV hardware and should not bypass the 960x540 dp design
canvas.

## Storage and privacy boundary

Rules, PIN hashes, sessions, usage rows and optional photos live in app-private storage (MMKV plus
files). `android:allowBackup="false"`. The community build sends no telemetry. Optional network
features are update checks, weather and daily wallpaper; the community snapshot omits the
Apple-hosted film catalogue, so it has no aerial-film playback. The phone settings page is
LAN-only. See [../PRIVACY.md](../PRIVACY.md).

The hosted service, parent phone app and their data handling are separate from this repository.
The public source includes only the TV-side client code needed by the optional mode.

## Upstream Aerial boundary

Rules for keeping merges small:

- Keep `app.orchard.tv` as the source namespace.
- Prefer a new hook in `PolicyProduct`, `PolicyModeCoordinator` or a policy UI file over editing an
  Aerial hot file.
- Keep device-specific fixes upstream. Do not add model checks or product branches to
  `DeviceProfile.kt`, `SystemState.kt`, `Brightness.kt` or `MitvPanel.kt`.
- Keep UI text in the five resource locales and in the phone page's `KEYS`/`TEXTS` tables.
- Preserve Aerial's copyright and MIT terms; see [../NOTICE](../NOTICE) and
  [../THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md).

## Versions

`versionCode` and `versionName` are derived from the Aerial base version plus a product revision in
`app/build.gradle.kts`. The community baseline is `1.85.010` / `116010`, based on Aerial 1.85 /
116. An Aerial base update resets the revision; product releases increase it by one and never
decrease the code. See [BUILDING.md](BUILDING.md).
