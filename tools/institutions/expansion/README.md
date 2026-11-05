# Adding institutions to the library

How the 2026-11-04 expansion was done (29 added, 7 logo fixes); repeat it for the next gap. The app only ever reads
the generated output of `../build_institutions.py`; nothing here runs in CI or at build time.

Tools: Python 3.11 with Pillow, Node with `svgo@3.3.2` and `sharp@0.33.5` in one `<node-tools>` folder, JDK 17+.
Keep downloads in their own folder outside the repo; run Python with `-I -X utf8`.

1. **Research** (`research.py`): `playsearch PH "<bank>"` → package ids; `play <pkg> PH <dir>` → title, developer and
   the 512px icon (check the developer is the institution — reject look-alikes such as "DBP Finance" by Finbot LTD);
   `imgs <site>` → logo candidates on the official site; `commons "<bank> logo"` / `commonsinfo "File:…" <dir>` →
   Commons file with its licence. Prefer the official site; never trace a raster into a vector.
2. **Check** every SVG on light and dark (`raster.js`, or any viewer) and against the current app icon/site — reject
   outdated logos (OFBank's pre-2025 file), bitmaps wrapped in SVG (BanKo's footer), and redraws that don't match.
   Colours: `node raster.js <logo> out.png` then `python colors.py out.png [--svg <logo.svg>]`.
3. **Spec**: write a JSON like `2026-11-04.json` (new entries, fixes, aliases). Every logo names its source URL and
   page; every gap says why.
4. **Library**: `python add_institutions.py <Global-Banking-Assets> <spec.json> <downloads>` — writes folders,
   `config.json`, `source.md`, registry entries and inventory rows; never overwrites an existing `logo.svg`.
   Back up `registry/` and `documentation/` first (the library is not under version control).
5. **Designs**: copy the registry to `lucento-wallet-design/_inputs/registry/`, then
   `python design_new_institutions.py <lucento-wallet-design> <Global-Banking-Assets> <node-tools> <ids>` (review),
   add an override with a `_reason` for any white/boxed logo the auto rule misreads, and re-run with `--write`.
6. **App**: `python ../build_institutions.py --library … --designs …/registry --node-tools …`, then check the
   fallback table in `docs/design/INSTITUTION_ASSETS.md`; render new drawables with `vd2svg.py` to eyeball them;
   update the count in `InstitutionRegistryTest` and run `./gradlew testDebugUnitTest`.
