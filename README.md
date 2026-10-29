# Intent Android companion

This native Kotlin and Jetpack Compose app is under development. Pair with a desktop through a QR code, an `intent://pair` link, or manual connection details. Review the host and certificate fingerprint before you connect. The app saves encrypted connections and opens the workspace list after authentication. Direct hosts race the optional Tailcat tunnel. All routes use pinned TLS and bearer authentication. Builds without the Tailcat AAR use direct hosts only.

## Architecture

- `app`: Android application with one Compose activity. Minimum Android API: 26. Compile and target API: 37 (Android 17).
- `core/protocol`: pure Kotlin/JVM module for protocol models, sessions, and pinned transport.
- `tailcat`: optional native tunnel adapter with an unavailable implementation for builds without the AAR.
- `gradle/libs.versions.toml`: pinned library and plugin versions.

The protocol source of truth is [docs/protocol](https://github.com/intent-hq/intent/tree/main/docs/protocol). The [iOS companion](https://github.com/intent-hq/ios) is the feature reference.

## Setup

1. Install JDK 21. Set `JAVA_HOME` to its installation directory.
2. Download the Android command-line tools for your operating system from [Android Developers](https://developer.android.com/studio#command-tools).
3. Extract the tools into `$ANDROID_HOME/cmdline-tools/latest`. That directory must contain `bin/sdkmanager`.
4. Install the SDK packages:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk" # macOS; choose a local SDK directory on Linux
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

The Gradle wrapper downloads Gradle 9.3.1. Android Gradle Plugin 9.1.1 supplies built-in Kotlin support for the app.

## Build and test

Run the complete gate from this repository:

```sh
./gradlew build ktlintCheck detekt test
```

Run only the protocol tests:

```sh
./gradlew :core:protocol:test
```

Format Kotlin sources and Gradle scripts:

```sh
./gradlew ktlintFormat
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Open this directory in Android Studio to run it on an emulator or device.

Tests cover protocol fixtures, pinned transport, pairing, session bootstrap, stores, and the offline cache. Camera scanning and live desktop pairing still need device verification.

## Emulator UI tests

The smoke suite uses an in-process fake transport. It covers manual pairing, workspace selection,
note tasks, comment threads, and conversation sending. It captures light and dark screenshots;
these are review artifacts, not pixel comparison tests.

Run the same managed Pixel 8 API 34 suite as CI:

```sh
./gradlew :app:pixel8Api34DebugAndroidTest -Ptailcat.enabled=false
python3 scripts/check-ui-screenshots.py
```

Gradle downloads the emulator image for the host architecture. The first run can take several minutes.
CI runs the full `androidTest` suite without a class or method filter.
The shared Compose rule disables Bluetooth only on the managed emulator and captures failures before activity teardown.
Reports are in `app/build/reports/androidTests/`. Screenshots are in
`app/build/outputs/managed_device_android_test_additional_output/`.
The separate CI instrumentation job retains both for 14 days, including failed runs.

## Release artifacts (under development)

The tag workflow produces unsigned AAB and APK files plus a git-cliff changelog.
The app is under development. These artifacts do not establish production readiness.
The workflow uses direct connections only (`-Ptailcat.enabled=false`). It does not include the private Tailcat library.
It does not publish a GitHub Release, upload to Play, sign files, or join the desktop alpha release chain.

Set `appVersion` in `gradle.properties` before each release commit.
Use `MAJOR.MINOR.PATCH`, with each number between 0 and 999 and no leading zeros.
Gradle uses this value as `versionName`.
The `versionCode` is `MAJOR * 1000000 + MINOR * 1000 + PATCH + 1` (0.1.0 gives 1001).
Each release must increase the version. Prerelease suffixes and build metadata are not supported.
Do not reuse a version code for a different published build.

After review and approval, push a tag that matches the committed version, such as `v0.1.0`.
The workflow rejects a tag that differs from `appVersion`.
Download `intent-android-v<version>-unsigned` from the completed Actions run within 30 days.
The archive contains `app-release.aab`, `app-release-unsigned.apk`, and `release-notes.md`.

For a local build and changelog preview, run:

```sh
./gradlew :app:bundleRelease :app:assembleRelease -Ptailcat.enabled=false
git-cliff --config cliff.toml --output /tmp/android-changelog.md
```

Install git-cliff 2.10.1 for the same changelog engine as CI.
The configuration groups conventional commits with the monorepo conventions.
The CI changelog covers the latest tag; the local command includes all history.

See the [Android command-line build guide](https://developer.android.com/build/building-cmdline) for the signing tools.

### Manual signing procedure

Signing remains a manual release-owner step. Keep the keystore outside the repository and use interactive password prompts.
The organization must choose the key owner, secure backup, upload key, and Play App Signing policy before distribution.
CI has no signing secrets. Secret provisioning and alpha-channel integration need separate approval.

1. Obtain the approved keystore and alias from the release owner.
2. Set `ANDROID_KEYSTORE` to its absolute path and `ANDROID_KEY_ALIAS` to its alias.
3. Copy the unsigned AAB before signing it:

```sh
cp app/build/outputs/bundle/release/app-release.aab /tmp/intent-signed.aab
jarsigner -keystore "$ANDROID_KEYSTORE" /tmp/intent-signed.aab "$ANDROID_KEY_ALIAS"
jarsigner -verify -verbose -certs /tmp/intent-signed.aab
```

4. Align the unsigned APK, then sign and verify the aligned file:

```sh
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -P 16 -f -v 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk /tmp/intent-aligned.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign \
  --ks "$ANDROID_KEYSTORE" --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --out /tmp/intent-signed.apk /tmp/intent-aligned.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs /tmp/intent-signed.apk
```

5. Compare the reported certificate fingerprint with the approved release certificate.
6. Install the signed APK on a test device and complete the release checks before distribution.

An AAB needs Play or bundletool to produce installable APKs. Do not use `apksigner` to sign an AAB.
Do not put passwords in commands, Gradle properties, logs, or Git.

## Tailcat library (under development)

The library builds the shared `packages/ios/TailcatKit` Go wrapper. The connection manager races direct hosts against the tunnel and closes losing routes. Browser traffic uses the winning route with the same pinned TLS and bearer authentication.
The generated AAR contains `arm64-v8a`, `armeabi-v7a`, and `x86_64` libraries. The AAR and license copy stay in gitignored `tailcat/libs/`.

1. Initialize the private iOS submodule from the monorepo with `make ensure-ios-submodule`.
2. Install Go and JDK 21. Add `go` and `javac` to `PATH`.
3. Set `ANDROID_HOME` as described above.
4. Install the NDK:

```sh
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "ndk;28.2.13676358"
```

5. Run the build from this repository:

```sh
./scripts/build-tailcat.sh
./gradlew build ktlintCheck detekt test -Ptailcat.enabled=true
```

The script reads the Go and `golang.org/x/mobile` versions from `TailcatKit/go.mod`.
Go downloads the required toolchain through `GOTOOLCHAIN` (currently Go 1.27.0).
The script installs pinned gomobile/gobind tools in a temporary directory and builds a temporary source copy.
The original Go source and module files stay unchanged.
For a standalone Android checkout, set `TAILCAT_SOURCE_DIR` to an existing `TailcatKit` directory.
`ANDROID_NDK_HOME` and `GOTOOLCHAIN` can override the defaults.
The [gomobile documentation](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile) describes the Android archive and architecture targets.

Gradle selects the native adapter when `tailcat/libs/tailcatkit.aar` exists.
Without that file, normal builds use the stub. `-Ptailcat.enabled=true` fails early when the file is missing.
To test the stub with an AAR present, run:

```sh
./gradlew build ktlintCheck detekt test -Ptailcat.enabled=false
```

The Kotlin entry point is `dev.intent.tailcat.Tailcat`, which implements `TunnelClientFactory`.
`available` reports build support. `createClient(token)` throws `TunnelUnavailableException` in stub builds.
`TunnelClient.ping(timeoutMs)` and `forward(remotePort)` are blocking calls for a worker thread.
The forwarder exposes `localPort` on `127.0.0.1`. Close the forwarder, then the client, after use or cancellation.
Closing a forwarder stops new connections. Closing its client also stops the tunnel.
The adapter forwards raw TCP. Callers must verify the pinned TLS certificate before they send bearer credentials through it.
Host unit tests cover adapter selection and unavailable errors. Native tunnel traffic still needs Android device verification.

## Mermaid diagrams (under development)

Notes and transcripts render Mermaid fences in an offline WebView using Pele 1.8.0.
The app bundles all scripts. It does not need Node, a CDN, or a network connection for diagrams.
The renderer follows the app theme. Invalid, incomplete, or oversized diagrams show their original code.
Diagrams support scrolling and pinch zoom; editing is not supported.
Links, images, file access, and network requests are disabled inside diagram views.

To rebuild the pinned assets and run the browser fixtures:

```sh
cd scripts/vendor-mermaid
npm ci
npm run build
npx playwright install chromium
npm test
```

Commit the generated `app/src/main/assets/mermaid` files with changes to the vendor lockfile.
`vendor.json` records the package versions and SHA-256 hashes. `NOTICE.txt` contains the bundled licenses.
Browser fixtures cover themes, parse failures, limits, hostile source, and offline rendering.
Android unit tests cover source encoding, local asset selection, and raw-code fallback decisions.
Native WebView gestures still need device verification.
