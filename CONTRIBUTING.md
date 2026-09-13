# Contributing to TwinGrid

Please use a bug, compatibility or feature issue before proposing a substantial
change. Include the exact TwinGrid version/code, firmware and reproduction
steps. For launch problems, distinguish a request being sent from the selected
ROM visibly loading. Never attach ROMs, saves, commercial emulator APKs,
unredacted diagnostics, credentials or private device backups.

For code changes:

1. Build with the documented PowerShell toolchain in [BUILDING](docs/BUILDING.md).
2. Keep changes focused and preserve package identity, SAF access and user data.
3. Run resource/catalog checks and validate the affected behavior. Label host,
   emulator, physical device and user-experience evidence separately.
4. Explain the problem, resulting behavior and relevant checks in the pull request.

Do not add media, fonts, datasets or copied code without provenance and a
compatible redistribution license. Catalog corrections should retain source
URLs and rights fields; changing the registered catalog requires reviewing the
database and manifest together. Do not run a legacy generator over the frozen
production catalog.

New original source/documentation contributions are under the root MIT license.
Third-party material and data retain their applicable licenses. Never contribute
signing keys or change the package solely for branding.
