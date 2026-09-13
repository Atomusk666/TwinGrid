# Building TwinGrid

This is the existing native Java / Android View application. It uses a direct
PowerShell pipeline: AAPT2 → javac → D8/R8 → ZIP assembly → zipalign. There is no
Gradle wrapper or generated replacement project.

## Requirements

- Windows with PowerShell 7 (`pwsh`).
- JDK 17; set `JAVA_HOME` or pass `-JavaHome`.
- Python 3 (standard library only); default command `python`, override with
  `-PythonExecutable`.
- Android SDK platform `android-34`, build-tools `36.0.0`.
  Set `ANDROID_SDK_ROOT` or pass `-AndroidSdkRoot`.
- The four pinned JARs already in `app/libs`; hashes in `docs/dependencies.json`.

Example using your own installed locations through environment variables:

```powershell
pwsh scripts/build_v02.ps1 -VersionCode 135 -VersionName 0.9.0 `
  -JavaHome $env:JAVA_HOME -AndroidSdkRoot $env:ANDROID_SDK_ROOT
```

The output is `artifacts/candidates/TwinGrid-0.9.0-code135-unsigned.apk`.
It is optimized by the original R8 rules and aligned, but **not signed or
installable until you sign it**. No private key, password file, emulator,
device or ROM is required for compilation. The script refuses to overwrite a
previous output. Preserve existing artifacts and use a fresh checkout to repeat.

## Local developer signing

Use a key you control with Android's `apksigner`. For example, after creating a
local developer keystore using JDK `keytool`, run the SDK signer interactively:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -jar "$env:ANDROID_SDK_ROOT/build-tools/36.0.0/lib/apksigner.jar" sign --ks $env:TWINGRID_DEV_KEYSTORE --out artifacts/TwinGrid-local.apk artifacts/candidates/TwinGrid-0.9.0-code135-unsigned.apk
& "$env:JAVA_HOME/bin/java.exe" -jar "$env:ANDROID_SDK_ROOT/build-tools/36.0.0/lib/apksigner.jar" verify --verbose --print-certs artifacts/TwinGrid-local.apk
```

Let the signer prompt for passwords; do not put passwords in commands, source
or issue reports. Do not use a different key to try to update an official
installation. Test developer builds on an isolated device/profile/emulator
without valuable app data; do not uninstall the user's official copy.

Official releases retain certificate SHA-256
`1e9019315ba95df9a5cfd51c5bc81d81db472bcb5cdc10e9c055054d32494a3e`.
The certificate has a historical debug-style subject name, but the official
APK is non-debuggable and signed with v2/v3. Its private key is excluded.
Git attributes preserve the exact bytes of frozen Android and catalog inputs,
including raw text resources whose line endings form part of the APK payload.
This repository's unsigned build never replaces the already-tested code135
release file. ZIP timestamps/signatures can differ on a rebuild; the public
APK identity is determined by the frozen signed file's complete SHA-256.

## Checks

```powershell
python scripts/check_ui_resource_keys.py
python scripts/production_catalog_gate.py
python scripts/final_resource_gate.py
python scripts/final_resource_gate.py artifacts/candidates/TwinGrid-0.9.0-code135-unsigned.apk
python scripts/public_audit.py
```

Resource checks verify SQLite integrity, catalog registration, the font hash,
dependency hashes through the public audit, source references, media exclusion
and the APK resource allowlist. They do not replace a physical RG DS test.
No CI workflow is advertised until that workflow itself has been exercised.

## Catalog and source layout

- `app/src/com/rgds/ultimate/shell/`: production Java classes.
- `app/AndroidManifest.xml`, `app/res/`, `app/proguard-rgds.pro`: Android inputs.
- `app/libs/`: pinned runtime JARs with separate licenses.
- `catalog/production_catalog.lock.json`: active database/manifest identity.
- `catalog/RGDS_TextBuild01/data/`: frozen production pair used by the guard;
  `app/res/raw/` contains the identical pair for APK packaging.
- `deployment/release.json`: original version, package and signing certificate record.
- `release_manifest.json`: identity of the frozen official APK.

The committed catalog is the build input. Rebuilding the complete historical
editorial collection is not part of the APK build. Old raw webpage snapshots,
source databases, private device data and experimental generation pipelines are
excluded. A correction requires reviewed provenance plus coordinated updates
to the catalog, manifest and lock. Do not regenerate from a historical sample.
