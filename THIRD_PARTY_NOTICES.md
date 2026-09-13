# Third-party notices

The root MIT license covers TwinGrid's own source, original icons/UI and
documentation. It does not relicense the following components or grant rights
in game artwork, ROMs, BIOS files, emulator binaries or trademarks.

## Runtime libraries

These are unmodified Maven Central JARs, transformed to Android bytecode during
the build. Exact file digests are in [dependencies.json](docs/dependencies.json).

| Component | Copyright / license | Source |
| --- | --- | --- |
| OkHttp 4.12.0 | Square, Inc.; Apache-2.0 | [Upstream](https://github.com/square/okhttp/tree/parent-4.12.0) |
| Okio JVM 3.6.0 | Square, Inc.; Apache-2.0 | [Upstream](https://github.com/square/okio/tree/3.6.0) |
| Kotlin stdlib 1.9.10 | JetBrains; Apache-2.0 | [Upstream](https://github.com/JetBrains/kotlin/tree/v1.9.10) |
| JetBrains annotations 13.0 | JetBrains; Apache-2.0 | [Upstream](https://github.com/JetBrains/java-annotations) |

See [Apache-2.0](licenses/Apache-2.0.txt) and the
[OkHttp notice](licenses/okhttp-4.12.0_NOTICE). OkHttp's embedded Public Suffix
List is MPL-2.0; its source is available at
[publicsuffix/list](https://github.com/publicsuffix/list). It is bundled in the
unmodified pinned OkHttp JAR and copied into the APK. See [MPL-2.0](licenses/MPL-2.0.txt).

## Font and pronunciation

- **Fusion Pixel Font**, Copyright (c) 2022 TakWolf, is distributed under
  [SIL OFL 1.1](licenses/Fusion-Pixel-OFL-1.1.txt), including the bundled
  `app/res/font/fusion_pixel_10.ttf`. This is the approved project font,
  SHA-256 `04de2c9adf78db676bd910fd229e16c1d70230b776579049ce82f7c6a06703f2`.
  [Upstream license](https://github.com/TakWolf/fusion-pixel-font/blob/master/LICENSE-OFL).
  It is not an extracted Nintendo, Android or DraStic system font. The font
  remains OFL licensed and is not covered by TwinGrid's MIT license.
- Generated pinyin fields and the CJK lookup use **pypinyin 0.55.0** and
  **pinyin-data**, under their MIT notices:
  [pypinyin](licenses/pypinyin-MIT.txt), [pinyin-data](licenses/pinyin-data-MIT.txt).
  Their Python runtimes are not bundled in the application.

## Offline catalog

The exact production database and paired manifest are registered in
`catalog/production_catalog.lock.json`. The database is a compilation of public
release facts, mappings, editorial summaries, and explicit source references.
It is not the private database of an installed user's library.

- **libretro-database / No-Intro**: release facts converted to SQLite from the
  recorded Nintendo DS DAT. Database conversion and associated contributions
  retain **CC BY-SA 4.0**, with attribution to libretro and No-Intro.
  [libretro license](https://github.com/libretro/libretro-database/blob/master/LICENSE),
  [No-Intro data terms](https://datomatic.no-intro.org/terms.html),
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **Bangumi and its subject contributors**: derived editorial text and mappings
  retain the recorded **CC BY-SA 3.0** attribution. Original subject URLs,
  reference IDs, versions and source fingerprints remain in the catalog.
  [Copyright policy](https://bgm.tv/about/copyright),
  [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/),
  [local notice](licenses/Bangumi.txt). No user journals, comments or source
  artwork are included.
- **Wikipedia-derived material**: attributed to the linked article contributors
  under **CC BY-SA 4.0** where recorded. Individual evidence retains the URL and
  editorial transformation. [Terms](https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use).
- **Publisher/developer pages and OpenVGDB references**: used for factual
  identification and independent short editorial synthesis. Original webpage
  prose, the OpenVGDB source database and media are not distributed. The source
  rights remain with their owners; no blanket OpenVGDB redistribution license
  is asserted. [OpenVGDB 29.0](https://github.com/OpenVGDB/OpenVGDB/releases/tag/v29.0).

Existing per-entry rights and attribution in `content.evidence`,
`localized_content.evidence` and `source_refs` take precedence over any general
summary here. Share-alike material remains under its respective license; it
must not be relicensed as MIT. `overview_copy.json` contains short editorial
variants bound to source/evidence digests, not scraped article copies.
The runtime's [source notices](app/res/raw/runtime_licenses.txt) are also
accessible within the application. Editorial self-review is not publisher
endorsement or a claim that every fact has been independently verified.

## Artwork index and screenshots

`cover_index.json` contains 7,335 relative paths, byte sizes and Git blob hashes
from [libretro-thumbnails/Nintendo - Nintendo DS](https://github.com/libretro-thumbnails/Nintendo_-_Nintendo_DS),
revision `a119dcab8fa04c9974b9c229eda824318d923322`. The index contains no image
bytes. Artwork is retrieved only at runtime through the configured public
routes. Index access and catalog licenses do not grant artwork redistribution
rights. Game artwork belongs to its respective rights holders.

The repository screenshots are unaltered frontend captures (code135 Chinese
views and explicitly labeled code134 English settings) selected
to show TwinGrid's UI without box-art images or extracted game graphics.
Game titles are identifiers; this does not convey ownership of trademarks.
No Nintendo reference UI images, emulator binaries, ROMs, BIOS, user cover
caches or saves are distributed.
