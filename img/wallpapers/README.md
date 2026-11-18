# Built-in Wallpapers (`img/wallpapers/`)

4K (`3840×2160`) dark OLED wallpapers stored under `img/wallpapers/<category>/<name>/` and packaged into Onyx at build time. Every item uses the `Category: Name` display format in the TV picker across all supported locales.

## Catalog (38 Wallpapers)

- **Carnival**
  - [`carnival/mask.jpg`](carnival/mask.jpg) — *Carnival: Mask*
- **Celebrations**
  - [`celebrations/birthday/`](celebrations/birthday/) (`<lang>-<REGION>.jpg` / `<lang>.jpg`, 44 locale variants) — *Celebrations: Birthday* (dynamically matches Onyx's active language)
- **Children**
  - [`children/balloons.jpg`](children/balloons.jpg) — *Children: Balloons*
- **Christmas**
  - [`christmas/elves.jpg`](christmas/elves.jpg) — *Christmas: Elves*
  - [`christmas/gifts.jpg`](christmas/gifts.jpg) — *Christmas: Gifts*
  - [`christmas/merry_christmas/`](christmas/merry_christmas/) (`<lang>-<REGION>.jpg` / `<lang>.jpg`, 33 locale variants) — *Christmas: Merry Christmas* (dynamically matches Onyx's active language)
  - [`christmas/nativity.jpg`](christmas/nativity.jpg) — *Christmas: Nativity*
  - [`christmas/olentzero.jpg`](christmas/olentzero.jpg) — *Christmas: Basque Tradition*
  - [`christmas/santa_claus.jpg`](christmas/santa_claus.jpg) — *Christmas: Santa Claus*
  - [`christmas/three_wise_men.jpg`](christmas/three_wise_men.jpg) — *Christmas: Three Wise Men*
  - [`christmas/tio_de_nadal.jpg`](christmas/tio_de_nadal.jpg) — *Christmas: Catalan Tradition*
  - [`christmas/trees.jpg`](christmas/trees.jpg) — *Christmas: Trees*
- **Diversity (18 Pride & Flag Wallpapers)**
  - [`diversity/agender.jpg`](diversity/agender.jpg) — *Diversity: Agender*
  - [`diversity/aroace.jpg`](diversity/aroace.jpg) — *Diversity: Aroace*
  - [`diversity/aromantic.jpg`](diversity/aromantic.jpg) — *Diversity: Aromantic*
  - [`diversity/asexual.jpg`](diversity/asexual.jpg) — *Diversity: Asexual*
  - [`diversity/bear.jpg`](diversity/bear.jpg) — *Diversity: Bear*
  - [`diversity/bisexual.jpg`](diversity/bisexual.jpg) — *Diversity: Bisexual*
  - [`diversity/demisexual.jpg`](diversity/demisexual.jpg) — *Diversity: Demisexual*
  - [`diversity/genderfluid.jpg`](diversity/genderfluid.jpg) — *Diversity: Genderfluid*
  - [`diversity/genderqueer.jpg`](diversity/genderqueer.jpg) — *Diversity: Genderqueer*
  - [`diversity/intersex.jpg`](diversity/intersex.jpg) — *Diversity: Intersex*
  - [`diversity/lesbian.jpg`](diversity/lesbian.jpg) — *Diversity: Lesbian*
  - [`diversity/nonbinary.jpg`](diversity/nonbinary.jpg) — *Diversity: Non-binary*
  - [`diversity/omnisexual.jpg`](diversity/omnisexual.jpg) — *Diversity: Omnisexual*
  - [`diversity/pansexual.jpg`](diversity/pansexual.jpg) — *Diversity: Pansexual*
  - [`diversity/polysexual.jpg`](diversity/polysexual.jpg) — *Diversity: Polysexual*
  - [`diversity/pride.jpg`](diversity/pride.jpg) — *Diversity: Pride*
  - [`diversity/rainbow.jpg`](diversity/rainbow.jpg) — *Diversity: Classic Rainbow*
  - [`diversity/trans.jpg`](diversity/trans.jpg) — *Diversity: Trans*
- **Halloween**
  - [`halloween/darkness.jpg`](halloween/darkness.jpg) — *Halloween: Darkness*
- **Love**
  - [`love/hearts.jpg`](love/hearts.jpg) — *Love: Hearts*
- **Men**
  - [`men/father.jpg`](men/father.jpg) — *Men: Father*
- **Religion**
  - [`religion/catholic_procession.jpg`](religion/catholic_procession.jpg) — *Religion: Catholic Procession*
- **Women**
  - [`women/breastfeeding.jpg`](women/breastfeeding.jpg) — *Women: Breastfeeding*
  - [`women/female_symbol.jpg`](women/female_symbol.jpg) — *Women: Female Symbol*
  - [`women/mother.jpg`](women/mother.jpg) — *Women: Mother*
  - [`women/pregnant.jpg`](women/pregnant.jpg) — *Women: Pregnant*

## Adding Built-in Wallpapers

1. **Process full-resolution 4K art** with [`tools/dither_wallpaper.java`](../../tools/dither_wallpaper.java) to smooth 8-bit dark gradient bands and apply 2×2-aligned TPDF dithering (preserved when 1080p TVs decode with `inSampleSize = 2`):
   - Single-image wallpaper:
     ```bash
     java tools/dither_wallpaper.java <source-image> img/wallpapers/<category>/<name>.jpg
     ```
   - Language-aware wallpaper: store per-locale images under `img/wallpapers/<category>/<name>/<locale>.jpg` and register the virtual key in `Config.LOCALIZED_BUILTIN_WALLPAPERS`. Gradle packages `img/wallpapers/` directly into the APK at build time.
2. **Add localized picker labels (`Category: Name`):**
   - Add `<string name="wallpaper_<category>_<name>">...</string>` across all `launcher/app/src/main/res/values*/strings.xml` locales.
   - Register the filename (`<category>_<name>.jpg`) → string resource mapping in [`Config.BUILTIN_WALLPAPER_LABELS`](../../launcher/app/src/main/java/im/marin/onyxtv/Config.java) and verify coverage with [`WallpaperRotationTest.java`](../../launcher/app/src/test/java/im/marin/onyxtv/WallpaperRotationTest.java).
