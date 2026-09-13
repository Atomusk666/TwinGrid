# Screenshot provenance

All six images were captured on 2026-09-13 from the physical RG DS running
TwinGrid 0.9.0 **code135**, stock firmware 1.18 / Android 14.
The installed APK SHA-256 was checked for every captured pair:
`faeaaafb0ce44658fc0a81a09face069aa1c51b0599c3fefcfc1134528dc067b`.

| Scene | Chinese | English |
| --- | --- | --- |
| Home | home-zh.png | home-en.png |
| Library — All | library-all-zh.png | library-all-en.png |
| Library — Types | library-genres-zh.png | library-genres-en.png |

Each 640 × 960 PNG joins the complete 640 × 480 upper framebuffer above the
complete 640 × 480 lower framebuffer. There is no crop, resize, padding,
retouching, translated overlay or generated UI. Both halves were checked pixel
for pixel against their original captures after decoding. PNG encoding differs
because two frames are saved as one lossless image. Chinese and English pairs
use the same scene and selected game/category. Clocks reflect capture time.
Original frame hashes, composite hashes and capture times are in
[manifest.json](manifest.json). Raw diagnostics remain private.

The language was changed through TwinGrid's settings and restored to its
original System default option. The app was normally closed and reopened to
refresh static labels after switching languages. Screenshot navigation used
only browsing and language controls, with no game-launch input.

Screenshots show an example local library, including in-context game banner
icons and box art. These belong to their respective rights holders and are not
relicensed by TwinGrid's MIT license. ROMs, saves, standalone game images and
the user's cover cache are not supplied. No device serial, account or private
absolute path is visible. See [third-party notices](../../THIRD_PARTY_NOTICES.md).

This gallery replaces the previous separate-screen images and historical
settings captures. It documents these three code135 scenes in both languages;
it does not claim a complete gameplay or compatibility regression.
