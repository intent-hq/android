# Intent Android companion

This native Kotlin and Jetpack Compose app is under development. It currently shows a placeholder screen. Pairing and daemon connections are not available.

## Architecture

- `app`: Android application with one Compose activity. Minimum Android API: 26. Compile and target API: 37 (Android 17).
- `core/protocol`: pure Kotlin/JVM module for future protocol code. It declares kotlinx-serialization and OkHttp dependencies.
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

The unit test checks the protocol test setup and JSON dependency. It does not check a daemon protocol implementation.

## Release status

This repository has no release pipeline. Do not use this skeleton as a production companion app.
