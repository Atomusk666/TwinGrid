# Compatibility

## Tested target

Anbernic RG DS, stock firmware V1.18, Android 14 / API 34, native 640 × 480
screens. Package `com.rgds.ultimate.shell`, display name TwinGrid.

The stock DraStic APK is identified by package `com.dsemu.drastic` and SHA-256
`4c9bd23fc7a3366f73bbcba4aef2858bfa3a3568b376160792322ed431180189`.
This exact-byte identity is more restrictive than a version label. See
`ReceiverProfile.java` for the current receiver contract. No emulator binary
is distributed by TwinGrid.

Historical code134 evidence includes distinct safe A/B target launches and
immediate relaunch checks on that environment. code135 changes the genre-grid
footer visibility and has its own targeted installed UI and preservation
checks; no game was launched during that follow-up. These scopes are recorded
separately in [VALIDATION](VALIDATION.md).

## Other DraStic builds and storage

The adapter checks that the expected Android entry Activity is exported,
enabled and matches the expected task contract. An unknown but structurally
matching receiver is a controlled trial, requiring acknowledgement scoped to
its environment identity. It is not classified as tested support. A receiver
may lack storage access, require manual setup, open its menu without loading,
or be refused before dispatch. SD and internal paths can behave differently.

The launch flow preflights/rechecks the selected file before dispatch and can
replace the emulator task. Save before switching games: unsaved progress is
not automatically preserved. `DISPATCHED_UNKNOWN` is not proof of visible game
execution, and returning to the frontend is not proof of emulator exit.

## GammaOS and other systems

GammaOS compatibility has not been fully verified yet. Android compatibility
alone does not establish dual-screen, storage or launch support. The Gamma
Nano native runner is not integrated. Other Android devices are unqualified.

## Reporting

Use the compatibility issue form. Include TwinGrid version/code, RG DS firmware,
stock/GammaOS/other, DraStic version, bundled or user-installed, SD/internal,
whether it manually opens the same ROM, whether all games are affected and
reproduction steps. A screenshot or redacted diagnostic excerpt helps.
Do NOT upload ROMs or save files, APKs of DraStic, device serials or private logs.
