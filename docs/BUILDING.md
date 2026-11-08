# Build from source

The project is a standard Gradle Android build. It produces an Android TV app for Android 5.0
(API 21) and newer. The community snapshot does not include the Aerial Lite module for Android 4.x.

## Requirements

- JDK 17.
- Android SDK Platform 36 and Android SDK Build-Tools. The Gradle wrapper downloads Gradle
  8.14.3; Android Gradle Plugin 8.12.0 and Kotlin 2.2.10 are declared in the build.
- Network access to the dependency repositories. The project lists Aliyun mirrors before Google
  and Maven Central so that it remains buildable from mainland China. If a mirror is unreachable,
  Gradle falls through to the next repository.
- A few GB of free space and, preferably, 8 GB or more of RAM.

Point Gradle at the SDK with `ANDROID_HOME`, or create an untracked `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Quick checks

Compile the standard debug variant and run the policy unit tests:

```bash
./gradlew :app:compileStandardDebugKotlin :policy:test
```

This catches Kotlin errors and tests the local rules, PIN, session and scheduling logic. It does not
prove that a feature works on a TV.

## Build an APK

Debug, for development only:

```bash
./gradlew :app:assembleStandardDebug
```

Release, for installation on a TV:

```bash
./gradlew :app:assembleStandardRelease
./gradlew :app:assembleGuardRelease
```

Outputs:

```text
app/build/outputs/apk/standard/release/app-standard-release.apk
app/build/outputs/apk/guard/release/app-guard-release.apk
```

The `standard` and `guard` flavors contain the same code. `standard` declares a home activity.
`guard` omits that declaration for firmware that refuses to install a third-party launcher and
takes over Home through accessibility instead. See [INSTALL.md](INSTALL.md).

Release builds enable R8 shrinking and resource shrinking and run Android lint's fatal API checks.
A debug build is noticeably slower on television hardware; use a release build for any real test.

## Signing

The release build reads `local/signing.properties` when it exists:

```properties
storeFile=planyourtv-release.jks
storePassword=change-me
keyAlias=planyourtv
keyPassword=change-me
```

The keystore path is resolved relative to `local/`. A keystore can be generated with the JDK:

```bash
mkdir -p local
keytool -genkeypair -v \
  -keystore local/planyourtv-release.jks \
  -alias planyourtv \
  -keyalg RSA -keysize 4096 -validity 10000
```

`local/` is ignored by Git. Never commit the keystore, its passwords, or `signing.properties`.

If `local/signing.properties` is absent, release builds fall back to this machine's Android debug
key. Such an APK installs, but it cannot update an app installed with a different key, and a build
from another machine will have a different identity. Generate a stable key for any build you
distribute. Uninstalling to change keys removes app data.

Verify a finished APK:

```bash
apksigner verify --print-certs app/build/outputs/apk/standard/release/app-standard-release.apk
sha256sum app/build/outputs/apk/standard/release/app-standard-release.apk
```

Publish the checksum with any APK you distribute. Signing keys for official hosted builds and
community builds are separate and cannot update over each other.

## Versions

The version follows the Aerial base version:

- `versionCode = Aerial versionCode * 1000 + productRevision`
- `versionName = "<Aerial version>.<productRevision padded to 3>"`
- The community baseline is `1.85.001` / `116001`, based on Aerial 1.85 / 116.

When rebasing on a newer Aerial release, update the Aerial version in `app/build.gradle.kts` and
reset the revision to 0. For each later product release, increase the revision by one. Never reuse
or decrease a version code, because Android will refuse the update. The version names and codes are
in `app/build.gradle.kts`.

Changing the version code is a release decision. For a local test build, it is fine to leave it
alone, but Android will not install a lower version over a higher one.

## Hosted mode

The community build has no backend endpoint baked in, hides remote-control UI and does not install
the HTTP/MQTT policy adapters. Local rules work normally.

An official-style build can opt in at compile time:

```bash
./gradlew :app:assembleStandardRelease -PtvApiBase=https://example.invalid/api/v1
```

The hosted service, parent app and device protocol are not part of this repository and are not
documented for self-hosting. Setting `-PtvApiBase` to an arbitrary server is not a supported
deployment path.

MQTT is an optional part of hosted mode and is not needed for a community build. Its properties
are empty by default.

## Installing

Install the release APK, grant the ADB-only permissions, start the app once, compile it, and choose
the home app. Follow [INSTALL.md](INSTALL.md) rather than stopping after `adb install`.

## Tests

```bash
./gradlew :policy:test
```

The policy module has JVM tests for PIN hashing, session timing, local rules, schedules, access
decisions and usage accounting. The app module also contains Robolectric test dependencies for
local Android framework tests. There is no device farm and no automated TV UI test suite.

## Troubleshooting

- **SDK location not found**: set `ANDROID_HOME` or `local.properties`.
- **Missing SDK packages or licenses**: install Platform 36 and Build-Tools with `sdkmanager`, then
  run `sdkmanager --licenses`.
- **Dependency downloads fail**: check access to the listed repositories. Do not add a third-party
  proxy or warehouse you do not trust.
- **Out of memory**: raise `org.gradle.jvmargs` in `gradle.properties`; the checked-in value is
  `-Xmx4g`.
- **Lint fails a release because of a newer API**: guard the call with a `Build.VERSION.SDK_INT`
  check. The project deliberately treats `NewApi` as fatal so Android 5.0 does not crash.
- **Build succeeds but the APK is very slow on the TV**: install a release build, not a debug
  build, and run `cmd package compile -m speed -f com.planyourtv.tv` after the first start.
