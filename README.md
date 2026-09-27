# TrueNAS Android

Native Android companion app for connecting to TrueNAS SCALE through its WebSocket JSON-RPC API.

## Current status

- ✅ Kotlin + Jetpack Compose + MVVM + repository pattern
- ✅ WebSocket JSON-RPC client scaffold with extensible method handling
- ✅ Connection profile form (host, port, token, TLS toggle)
- ✅ Secure local profile persistence with `EncryptedSharedPreferences`
- ✅ Day 1 dashboard showing basic instance info states (loading/success/error)
- ✅ Mock mode for local development and previews without a NAS
- ✅ GitHub Actions CI + debug APK artifact workflow
- 🚧 Future: richer navigation and additional TrueNAS feature domains

## Architecture summary

This project starts as a single `:app` module with clear package boundaries for future extraction:

- `app`: Activity/bootstrap + Compose screen wiring
- `core/model`: shared domain models
- `core/network`: JSON-RPC transport client and parsers
- `core/storage`: secure connection profile persistence
- `feature/connection`: form + connection ViewModel
- `feature/dashboard`: repository and instance info loading
- `di`: Hilt modules

### Migration path to multi-module

When features expand, move package groups into modules with minimal API surface changes:
1. Extract `core:model`, `core:network`, `core:storage`
2. Extract feature modules (`feature:connection`, `feature:dashboard`)
3. Keep `:app` as nav/bootstrap only

## Quick start

### Requirements

- Android Studio Koala+ (or latest stable)
- JDK 17
- Android SDK 35

### Run locally

```bash
./gradlew assembleDebug
```

Then install from Android Studio or with `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Configure connection

1. Open app
2. Enter host/IP, port, API token
3. Leave **Use TLS** enabled unless testing on a trusted local network
4. Optional: enable **Mock mode** for offline development
5. Press **Connect**

## Tests and lint

```bash
./gradlew testDebugUnitTest lintDebug
```

## CI artifacts (debug APK)

- Open **Actions** tab
- Run **Build Debug APK Artifact** manually, or push to `main`
- Download APK from workflow artifacts

## TrueNAS API note

Method names can vary across SCALE releases. See `/docs/API_INTEGRATION.md` for where to update JSON-RPC methods and parsing.
