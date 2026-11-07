# Third-party notices

PlanYourTV's own source code is MIT-licensed (see [LICENSE](LICENSE)). The components and content
below keep their own terms. The MIT license does not grant rights to third-party media, marks, or
data.

## Aerial

- Project: https://github.com/pengchujin/aerial-tv
- Copyright (c) 2026 pengchujin
- License: MIT

The Android source namespace remains `app.orchard.tv` for upstream merge compatibility. Community
builds install as `com.planyourtv.tv`.

## Inter font

- File: `app/src/main/res/font/inter.ttf`
- Copyright 2020 The Inter Project Authors
- License: SIL Open Font License 1.1
- Full text: [FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)
- Project: https://github.com/rsms/inter

## Material Symbols

Some icon path data in `GlyphPaths.kt` comes from Google Material Symbols Rounded.

- License: Apache License 2.0
- Project: https://github.com/google/material-design-icons

## Build and runtime dependencies

These libraries are used as dependencies and are not vendored source in this repository. Their
license texts ship with the artifacts resolved by Gradle.

| Component | License |
|---|---|
| AndroidX, Jetpack Compose, Media3 | Apache License 2.0 |
| Kotlin standard library and Gradle plugins | Apache License 2.0 |
| Eclipse Paho MQTT client | Eclipse Public License 2.0 / Eclipse Distribution License 1.0 |
| NanoHTTPD | BSD 3-Clause |
| ZXing core | Apache License 2.0 |
| Gradle wrapper | Apache License 2.0 |
| JUnit 4 (tests) | Eclipse Public License 1.0 |
| Robolectric (tests) | MIT |

## Bundled quiz media

The quiz challenge ships its own media under `app/src/main/assets/quiz/` and
`app/src/main/res/raw/`:

- The two MP4 clips and their audio tracks were produced for this project.
- The voice clips were generated with Volcengine Doubao TTS (Seed-TTS 2.0) using the project's own
  account. Use of the generated audio also follows the provider's service terms.
- The sound effects and the question bank are project-made.

These media files are not covered by the MIT license that applies to the source code. If you fork
the project, replace them with media you have the right to distribute.

## Data and services

- **Aerial-film catalogue (not included).** The community snapshot omits
  `app/src/main/assets/aerials.json`, which lists films hosted by Apple. Neither the catalogue nor
  any film is bundled or licensed under this project's MIT license. If you add a catalogue
  yourself, you are responsible for its content and for Apple's terms. Apple and Apple TV are
  trademarks of Apple Inc.
- **Weather.** Weather data is requested from Open-Meteo (https://open-meteo.com/) and location
  lookup from ipinfo.io and Open-Meteo geocoding. Follow their attribution and usage terms.
- **Daily wallpaper.** Daily landscape photographs are requested from Bing's image service and are
  not bundled with this repository.
- **City list.** `app/src/main/assets/cities-cn.tsv` is derived from GeoNames
  (https://www.geonames.org/), licensed under CC BY 4.0.
- **Device profiles.** `app/src/main/assets/devices.json` and the Aerial device code are part of
  the Aerial base and are distributed under Aerial's MIT license.
