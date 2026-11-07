# Contributing

PlanYourTV is a community source snapshot of a TV app that is also built in a private product
tree. Pull requests are reviewed and may be integrated into that tree rather than merged directly
into the public repository's history.

## Before you start

- For a bug, include the TV model, Android version, app version, build flavor and install method.
- For a security issue, follow [SECURITY.md](SECURITY.md), not a public issue.
- Open an issue before a large feature or refactor so the approach can be agreed first.
- Do not include television IP addresses, ADB keys, signing files, account credentials, personal
  photos, or videos from someone else's device in an issue or commit.

## Code guidelines

- Keep the product logic in `:policy`. The Aerial base in `:app` should gain only thin integration
  points, with comments that explain why the hook is needed.
- Keep the Aerial source namespace (`app.orchard.tv`) so upstream merges stay small. Do not
  globally rename packages to the installed application ID.
- Comments and identifiers are English. User-visible text belongs in resources:
  `values`, `values-zh`, `values-ru`, `values-uk`, `values-kk`; placeholders must match in all five.
- The phone settings page lives in `app/src/main/assets/web/index.html`. Its `KEYS` and `TEXTS`
  tables must stay aligned in all five languages.
- TV layout is designed for a 960x540 dp canvas. Do not compute layout from the real panel DPI.
- Use the existing motion curves and `Pressable` component for remote-control interaction.
- Do not add advertising, analytics, crash-reporting or attribution SDKs.
- Do not add code that downloads or recommends other apps, opens an ADB shell, or depends on a
  content server the project does not control.
- Do not bundle third-party media, brand artwork, user photos, or screenshots. The community
  snapshot deliberately omits Aerial's Apple-hosted film catalogue; do not add it back or add
  content you cannot redistribute.
- Preserve Aerial's copyright, MIT license text, and third-party notices.

## Tests and builds

Run at least:

```bash
./gradlew :app:compileStandardDebugKotlin :policy:test
```

Release builds should be verified with a local release build or the project's release process.
State clearly in the pull request what was tested and on which device. A feature is not "verified"
just because it compiles.

## Pull requests

- Keep one logical change per pull request.
- Explain the user-visible behavior and the risk to existing TV takeover, update or data flows.
- Mention any new permission, network destination, or stored data.
- Update [PRIVACY.md](PRIVACY.md), [SECURITY.md](SECURITY.md) or the notices when behavior changes.
- By submitting a contribution, you agree to license it under the repository's MIT license and
  confirm you have the right to submit it.
