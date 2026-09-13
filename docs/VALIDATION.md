# Release validation scope

Selected release: TwinGrid 0.9.0, code135. The user explicitly selected this
existing candidate; no product code or resource bytes were changed for public
repository preparation.

## Candidate evidence

- code135 exact signed APK: SHA-256
  `faeaaafb0ce44658fc0a81a09face069aa1c51b0599c3fefcfc1134528dc067b`,
  6,989,166 bytes, same original project certificate.
- code135 physical in-place installation and targeted Chinese library UI
  verification: PASS. All 13 genre counts visible; other library footers kept
  their original pixels in the targeted comparisons.
- code135 data-preservation report: 35 checks passed; 49 save files compared
  through both filesystem views, with filenames, sizes and SHA-256 unchanged.
  ROM path/size/mtime preserved; this is not a full ROM-content hashing claim.
  SAF grants and protected settings remained intact. Normal navigation and
  documented derived-cache updates were recorded separately.
- Broad core/launch regression: historical code134 evidence, not a new full
  code135 suite. code135 follow-up launched no games and did not reboot.
- Final pixel appearance and physical input feel are not inferred from host
  checks or automatic screenshots.

The earlier historical bulk-read incident's original exception was not
captured. Successful subsequent reads do not establish its historical root
cause; that remains unknown. It is not presented as a confirmed code135 failure
or as a proven historical fix.

## Public preparation checks

Binary manifest, v2/v3 signature, source identity list, resource lock, SQLite
integrity and media exclusion are checked directly against the candidate and
public source tree. The public unsigned build is a separate verification
artifact and must never be substituted for the frozen signed release APK.

Full private evidence, raw logs, device IDs, installed-user databases and save
backups remain on the development host. The current gallery contains six
code135 composites: Home, Library All and Library Types in Chinese and English.
Each joins the complete upper and lower screens at 640 × 960; both decoded
halves match their original capture pixels. Source frame and composite hashes
are in the screenshot manifest. This is a visual capture follow-up, not a new
full regression suite.

The 2026-09-13 screenshot follow-up compared the fresh 50-file save baseline
through both filesystem views: names, sizes and SHA-256 remained identical.
ROM path/size/mtime, directories, all metadata tables, original artwork caches,
SAF grants and default HOME remained unchanged. Original language, page and
selected game were restored. Navigation records and a derived thumbnail
changed; private diagnostics are retained separately. No byte-for-byte claim
is made for the entire app-private directory.

See [PUBLIC_RELEASE_AUDIT](PUBLIC_RELEASE_AUDIT.md) and
`release_manifest.json` for preparation results. Publication is complete only
after the public GitHub APK has been downloaded and its full SHA-256 matched.
