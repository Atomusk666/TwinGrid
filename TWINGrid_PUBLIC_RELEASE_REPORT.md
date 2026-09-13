# TwinGrid public release report

**TWINGRID PUBLIC BETA PUBLISHED**

Published and verified on 2026-09-13.

## GitHub

- Public repository: [Atomusk666/TwinGrid](https://github.com/Atomusk666/TwinGrid).
- Default branch: `main`.
- Tag: `v0.9.0-beta.1-code135`.
- [Release: TwinGrid 0.9.0 Beta (code135)](https://github.com/Atomusk666/TwinGrid/releases/tag/v0.9.0-beta.1-code135).
- [APK: TwinGrid-0.9.0-code135.apk](https://github.com/Atomusk666/TwinGrid/releases/download/v0.9.0-beta.1-code135/TwinGrid-0.9.0-code135.apk).
- Initial source commit: `37e1cf49555ce57ee2bb633252ea99c7c9e279ae`.
- No existing repository was overwritten; no force push or account-level setting change.

## Identity

| Field | Verified value |
| --- | --- |
| Product / application label | TwinGrid |
| versionName | `0.9.0` |
| versionCode | `135` |
| Android package | `com.rgds.ultimate.shell` |
| APK size | 6,989,166 bytes |
| Local APK SHA-256 | `faeaaafb0ce44658fc0a81a09face069aa1c51b0599c3fefcfc1134528dc067b` |
| GitHub download SHA-256 | `faeaaafb0ce44658fc0a81a09face069aa1c51b0599c3fefcfc1134528dc067b` |
| Certificate SHA-256 | `1e9019315ba95df9a5cfd51c5bc81d81db472bcb5cdc10e9c055054d32494a3e` |
| Catalog version | `2026.09.09-textbuild01-code113` |
| Catalog SHA-256 | `ad64ec385522e64b5c1b388bb30c898cee3267a84245f334657ddb9fee50414f` |
| Original signed build time | `2026-09-13T13:15:01.2808835+08:00` |

Identity was extracted from the actual APK. v2/v3 signatures pass; binary
manifest `debuggable`, `testOnly`, `allowBackup` and `usesCleartextTraffic` are
false. The original certificate is retained. This is the same frozen candidate,
renamed for the Release asset; it was not replaced by a new signed build.

See [release_manifest.json](release_manifest.json). The Release also includes
that manifest and `SHA256SUMS.txt`; no extra source ZIP or offline data pack was
uploaded. GitHub's automatic source archives are sufficient.

## Public files

- [English README](README.md) and [中文 README](README.zh-CN.md).
- [LICENSE](LICENSE), [CHANGELOG](CHANGELOG.md), [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md).
- [CONTRIBUTING](CONTRIBUTING.md), [SECURITY](SECURITY.md), [BUILDING](docs/BUILDING.md).
- [Bug template](.github/ISSUE_TEMPLATE/bug_report.yml),
  [compatibility template](.github/ISSUE_TEMPLATE/compatibility_report.yml),
  [feature template](.github/ISSUE_TEMPLATE/feature_request.yml).
- [FAQ](docs/FAQ.md), [compatibility](docs/COMPATIBILITY.md),
  [validation scope](docs/VALIDATION.md), [audit](docs/PUBLIC_RELEASE_AUDIT.md).
- 103 production Java source files, Android resources, four pinned JARs,
  catalog registration and public unsigned build scripts.
- Three original code135 screenshots with [provenance](assets/screenshots/README.md)
  and [byte hashes](assets/screenshots/manifest.json).

## Compatibility

- Tested target: Anbernic RG DS, stock V1.18, Android 14 / API 34.
- Recorded stock DraStic: `r2.5.2.2a` / code104,
  package `com.dsemu.drastic`, exact APK SHA-256
  `4c9bd23fc7a3366f73bbcba4aef2858bfa3a3568b376160792322ed431180189`.
- code135: verified in-place installation, targeted Chinese genre/footer UI
  checks and 35 preservation checks. The two-view save comparison covers 49
  files. Broad core and safe A/B launch tests remain historical code134 evidence.
- GammaOS not fully verified; Gamma Nano native runner not integrated. Other
  Android DraStic builds/storage combinations may require setup, fail to load
  the target, or not satisfy the adapter contract.
- Historical bulk-read incident root cause remains unknown. Catalog coverage
  is incomplete: 1,360 edited bilingual works out of 3,417.

## Audit

The initial public commit contains 174 allowlisted files, approximately 50.4 MB
before Git compression/deduplication. The initial staged audit reported **PASS,
zero findings**. It inspected all decoded SQLite rows (69,072 including the
identical registered/packaged copies), 1,444 JAR members, compressed suffix-list
data, hidden configuration, text/binary secret patterns and the signed APK.

Private keys, signing configuration, tokens, device identifiers, personal host
paths, user databases, logs, ROMs, saves, BIOS, DraStic APKs, cover caches,
firmware images and historical review archives were excluded. No secret was
masked in place and then published. The three screenshots were visually
reviewed and copied byte-for-byte. No downloaded box art is included.

Third-party licenses are retained separately: Apache-2.0 libraries, MPL-2.0
suffix data, OFL 1.1 font, MIT pronunciation data, and catalog-specific
attribution/share-alike notices. Original external prose/source databases and
media were not included. No unresolved redistribution blocker was identified
in the selected public payload. MIT covers TwinGrid's own work only.

No product code/resource change, device installation, gameplay, HOME/system
modification or save write was made during public preparation. Historical
development evidence and private materials remain outside the public tree.

## Release verification

- Pre-release: **true**; draft: **false**; repository: **Public**.
- Anonymous repository and Release page GETs: **HTTP 200**.
- Issue chooser: **HTTP 200**; all three forms are present with valid YAML.
- GitHub recognizes the root license as **MIT**.
- README HTML contains the screenshot references; all three public image
  downloads exactly match their local original hashes.
- Anonymous Release APK download: **HTTP 200**, full SHA-256 equals the frozen
  local candidate. This fulfills the required final download/readback gate.
- Public source build and a clean archive of the initial Git commit both
  compile successfully without private signing material. Every decoded code
  and resource payload matches the official APK; differences are confined to
  signature metadata. The unsigned outputs were not uploaded.
- 901 UI resource references resolve. SQLite/production-lock/resource gates pass.

Browser automation was unavailable in this session. Page availability and image
delivery were verified through actual public HTTP/HTML and image-byte readback;
no browser-render screenshot check is claimed.

## Remaining

- GammaOS, other devices and other DraStic builds need compatibility reports.
- code135 has targeted checks, not a newly repeated complete bilingual/core suite.
- Only the three suitable exact-code135 captures are published; HOME, English,
  search and onboarding galleries need future same-version captures.
- Offline metadata coverage and independent editorial fact review remain incomplete.

No remaining publication blocker. **TWINGRID PUBLIC BETA PUBLISHED**.
