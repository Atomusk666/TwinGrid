# Screenshot provenance

These 640 × 480 images are original framebuffer captures from
TwinGrid 0.9.0 on RG DS stock firmware 1.18. Capture versions are listed below.
They were copied without cropping, compositing, retouching or re-encoding.
Exact digests, original capture names and the APK identity are in
[manifest.json](manifest.json).

- `genres-lower.png`: lower-screen genre overview after the code135 footer fix.
- `genres-upper.png`: matching upper-screen genre preview.
- `folders-lower.png`: lower-screen physical folder navigation.

The above three images are from code135.

- `settings-metadata-en-lower.png`: English covers/metadata settings, lower
  screen, captured on code134.
- `settings-folders-en-lower.png`: English game-folder settings, lower screen,
  captured on code134.

The English settings captures are tied to code134 APK SHA-256
`8a7ff158be73bf5185a26d350abb9e6d83f75064bc9648b3859523219c47828b` in the
original capture records and evidence manifest. The code134 → code135 source
patch only changes genre-overview footer visibility, not these settings views.
Their original code134 identity is retained; this is not a claim of a new
code135 English capture or regression test.

The selected captures show original frontend UI and game/folder identifiers;
they contain no box art, ROM image payloads, device serial, account or private
absolute path. Other game artwork captures are excluded. The historical local
library count is an example, not content supplied by TwinGrid.

This is deliberately a small selection. No code135
HOME, English, search or first-scan capture was available in this targeted
evidence set. English code134 settings are now included with explicit version
captions; older-version images are not relabeled as code135. The complete
private regression archive remains outside the repository.
