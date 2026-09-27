# CI/CD

## Workflows

### `ci.yml`
Runs on push to `main` and all pull requests.

Checks:
- Gradle validation/build (`assembleDebug`)
- Unit tests (`testDebugUnitTest`)
- Android lint (`lintDebug`)

### `artifact-debug-apk.yml`
Runs on push to `main` and manual dispatch.

Outputs:
- Builds `app:assembleDebug`
- Uploads debug APK as a workflow artifact

### `release-signed-skeleton.yml`
Manual workflow skeleton for future release signing.

- Expects secrets for keystore and passwords
- Uses placeholders intentionally; do not commit raw signing material

## Artifact/release process

1. Merge to `main` or trigger artifact workflow manually
2. Download debug APK artifact from workflow page
3. Share APK for manual install/testing

## Secrets and signing guidance

Required for signed release workflow:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Decode keystore only inside CI runtime and remove after build.
