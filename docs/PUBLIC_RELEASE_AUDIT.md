# TwinGrid public release audit

Audit date: 2026-09-13. Selected file: **TwinGrid 0.9.0 / code135**,
SHA-256 `faeaaafb0ce44658fc0a81a09face069aa1c51b0599c3fefcfc1134528dc067b`.

## Preparation boundary

A new public directory was populated from an explicit allowlist. The existing
development workspace, historical evidence and signing material were kept
separately. No inherited Git history is imported. Application source/resources
match the code135 source identity record. Only public build instructions,
unsigned build plumbing, documentation and audit tooling were added.

## Secrets, privacy and archives

The source tree, including hidden GitHub templates and dotfiles, is scanned by
`scripts/public_audit.py`; the staged Git index is scanned again before commit.
Checks inspect text and binary payloads, all decoded SQLite rows, all 1,444
members of the four JAR archives and their compressed Public Suffix List data.
Rules cover private-key markers, common GitHub/LLM/Google/cloud tokens,
credential assignments and developer personal paths. Findings report rule IDs
and filenames, never matched secret values. Large files and forbidden
extensions are checked, and dependency hashes must match their registration.

No secrets were found in the selected public files by these checks. No signing
file was copied. The source development tree contains required private signing
materials and device evidence, all excluded as whole files rather than masked
password structures. No previous experimental Git history is published.

Excluded categories: keystores and signing configuration; passwords/tokens;
host-only Bangumi seed/raw webpage snapshots; installed-user databases; ADB
logs and diagnostics; raw directory trees; device serials; firmware/card images;
ROMs; BIOS; DraStic APKs; saves/savestates; downloaded covers; private screenshots;
all historical validation/review ZIPs; generated build output and caches.
The frozen official APK is a Release asset, never a tracked Git file.

An automated scan is not proof that every conceivable secret format is absent.
Manual review covers the publication file list, source resource whitelist,
catalog table meanings and the three selected screenshots. The fresh history
starts only from these reviewed files. Audit scripts and reports do not contain
real passwords or secret values.

## Copyright and resource decisions

- Original TwinGrid Java/UI/vector icons and authored documentation: MIT.
- Four pinned runtime JARs: Apache-2.0, with original notices; embedded Public
  Suffix List: MPL-2.0. JAR hashes match the existing dependency manifest.
- Fusion Pixel Font: OFL 1.1, exact approved asset hash; full notice retained.
  No extracted Nintendo, Android, DraStic or commercial font is included.
- Production SQLite: registered text/fact/mapping compilation with per-entry
  source references and rights; libretro/No-Intro-derived data, Bangumi and
  Wikipedia-derived edits retain their applicable attribution/share-alike terms.
  No original OpenVGDB database, scraped long-form pages or media is distributed.
- Pinyin-derived data: original pypinyin and pinyin-data MIT notices retained.
- Artwork index: relative paths, sizes and digests only; no bundled game image
  bytes or implied artwork redistribution license.
- Screenshots: three actual code135 frontend captures reviewed visually;
  no box-art images or extracted game graphics. No Nintendo reference UI image.

See [THIRD_PARTY_NOTICES](../THIRD_PARTY_NOTICES.md) for individual source links
and license boundaries. MIT does not replace third-party licenses. The review
does not represent publisher endorsement or independent fact-checking of every
catalog paragraph. No unresolved third-party binary/media permission issue
was identified in the selected payload.

## Build and identity

The public unsigned build compiles all 103 Java files with PowerShell 7, JDK 17,
Android SDK 34/build-tools 36.0.0 and Python. Its decoded APK code and resources
are identical to the frozen code135 APK; only signature archive entries differ.
The unsigned artifact is excluded and cannot replace the official release.

The candidate binary manifest confirms package `com.rgds.ultimate.shell`,
versionName `0.9.0`, versionCode `135`, label TwinGrid, non-debuggable/non-test
flags and disabled backup/cleartext. Signature v2/v3 and the original certificate
are checked directly. The catalog and paired manifest match their lock hashes,
SQLite integrity passes and the APK resource gate finds no game media/user cache.

## Publication gate

The release must remain a pre-release. No device install, gameplay, system/HOME
change or save modification is performed as part of this repository preparation.
Existing evidence is described with its original code134/code135 scope.

Publication is not complete until the repository and Release are public, the
README images/license/templates are accessible, and a newly downloaded GitHub
APK has the exact frozen SHA-256. The publication result and download readback
are recorded in the final public release report.
