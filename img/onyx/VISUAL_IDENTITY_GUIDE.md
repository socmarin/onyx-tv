# Unified Visual Identity Guide — ONYX TV

## 1. Brand Concept and Dual Architecture
**ONYX TV** is a minimalist, sober launcher designed for television environments (**Android TV**, **Google TV**, and **FireOS**). Its graphic language follows a *pure-dark OLED* aesthetic: precise geometry, zero superfluous ornamentation, and maximum legibility from across the living room.

The visual system is structured into **two complementary sister kits** sharing the exact same television silhouette, color palette, micro red status LED halo, and proportions:

1. **System 1 — Full Logotype (`01_full_logo_kit/`):**
   * Features the ultra-thin OLED television with the geometric **“ONYX”** wordmark centered on the screen (highlighted by the distinctive **faceted octagonal “O”**).
   * **Recommended use:** Widescreen (`16:9`) Android TV banners, GitHub repository header (`onyx-tv`), splash screens, presentations, and any layout where width is **64 px** or larger.

2. **System 2 — Compact Isotype (`02_isotype_octagonal_o_kit/`):**
   * Keeps the exact same OLED chassis and red status LED, replacing the four letters with an enlarged **faceted octagonal “O”** centered on the screen.
   * **Recommended use:** Application launcher icons (`ic_launcher`), circular Google TV masks, dense app grids, browser favicons (`16×16` to `48×48 px`), and any surface requiring immediate iconic recognition at small sizes.

---

## 2. Official Color Palette (Shared Across Both Systems)

| Role | Name | HEX Code | RGB Values | Usage & Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Background** | Matte Carbon | `#222222` | `34, 34, 34` | Base canvas for icons, banners, UI backgrounds, and the Android `background` layer. |
| **OLED Screen** | Pure Black | `#000000` | `0, 0, 0` | Interior TV display; achieves true black by turning off pixels on OLED panels. |
| **Wordmark / “O” Isotype** | Light Silver | `#E0E0E0` | `224, 224, 224` | “ONYX” lettering and “O” isotype over `#000000` screen (`16.1 : 1` contrast ratio). |
| **TV Chassis** | Graphite Gray | `#3A3A3A` | `58, 58, 58` | Structural body of the television bezel. |
| **TV Bezel Outline** | Slate Gray | `#525252` | `82, 82, 82` | Subtle outer hairline (`1–2 px`) defining the chassis against the `#222222` background. |
| **Stand / Base** | Steel Gray | `#3E3E3E` / `#484848` | `62, 62, 62` / `72, 72, 72` | Vertical neck and trapezoidal tabletop stand base. |
| **LED Core** | Standby Red | `#FF2A2A` | `255, 42, 42` | Center point of the status LED indicator on the bottom bezel. |
| **LED Halo** | Soft Crimson | `#E51C23` | `229, 28, 35` | Soft radial glow (`35% – 85%` opacity) surrounding the LED. |
| **OLED Panel Reflection** | Crystal White | `#FFFFFF` | `255, 255, 255` | Subtle diagonal sheen (`1.2% – 3.2%` opacity; `#08FFFFFF` in XML) adding depth. |
| **Positive Version** | Ink Black | `#181818` | `24, 24, 24` | Silhouette and lettering on white/light backgrounds (`#FFFFFF`), with `#D91E18` (`217, 30, 24`) LED. |

---

## 3. Typography and Geometric Construction

* **Self-contained proprietary paths:** Both the **“ONYX”** logotype and the **“O”** isotype are built using exact vector polygons (pure `<path>` elements in SVG and cubic Bézier curves compatible with AAPT2/Android VectorDrawable XML). They do not depend on external fonts or require font licensing.
  * **Octagonal “O” geometry:** Height-to-width ratio `1 : 0.94`, outer `45°` chamfers (`25%` of height), and softened inner chamfers (`68%` of outer chamfer) to maintain an optically uniform stroke weight.
* **Recommended complementary UI/web/docs typeface:**
  * **Inter** or **Space Grotesk** (*SIL Open Font License 1.1*).
  * **Medium (500)** for titles, app cards, and headers; **Regular (400)** for body copy and documentation.

---

## 4. Color Variants and Usage Matrix

Both kits (`01_full_logo_kit` and `02_isotype_octagonal_o_kit`) include all **6 variants** in vector (`SVG`) and high-resolution raster (`PNG 2048×2048 px`):

| Variant | Technical Description | When to Use |
| :--- | :--- | :--- |
| **1. Primary (`1:1` and `16:9` on `#222222`)** | Matte carbon `#222222` background, `#000000` screen, OLED sheen, and red LED. | Standard usage on Android TV, Google TV, FireOS, Google Play Store, and GitHub. |
| **2. Negative with Transparent Background** | Preserves gray chassis, `#000000` screen, `#E0E0E0` lettering, and red LED; transparent outside. | Overlays on custom dark backgrounds, screenshots, or promotional assets. |
| **3. Positive for Light Backgrounds** | Chassis, stand, and wordmark/isotype in `#181818`, white `#FFFFFF` screen, and `#D91E18` LED. | Print documentation, PDFs, invoices, articles, or light-mode web UIs. |
| **4. Positive on Solid White** | Same as the positive version on an opaque `#FFFFFF` canvas (`2048×2048 px`). | Platforms or forms that disallow alpha transparency and require a solid white background. |
| **5. Monochrome White (1-Ink)** | Silhouette and wordmark/isotype in pure `#FFFFFF` with a cut-out screen, no LED or gradients. | Android 13+ themed monochrome icons (`ic_launcher_monochrome.xml`), watermarks, or dark screen printing. |
| **6. Monochrome Black (1-Ink)** | Silhouette and wordmark/isotype in pure `#000000` with a cut-out screen, no LED or gradients. | Stamps, engravings, black-and-white print, or watermarks on light backgrounds. |

---

## 5. Usage Rules, Safe Zones, and Restrictions

* **General clear space:** Maintain a clear margin around the television silhouette of at least **12% of the total canvas width**.
* **Android adaptive icon safe zone (`108×108 dp`):** The complete television (including stand) is scaled to `84%` inside the grid, fitting cleanly within the **`66×66 dp`** (`61.1%`) central circle. This ensures Google TV (circular mask) and FireOS/mobile (*squircle* mask) never clip the corners of the television.
* **Minimum size threshold:**
  * **Full Logotype (“ONYX”):** Do not render below **64 px** width.
  * **Isotype (Octagonal “O”):** Mandatory below **64 px** (favicons `16×16`, `32×32`, `48×48 px`, and thumbnails).
* **Do not:**
  * Alter the chassis aspect ratio (`720 : 420`).
  * Replace `#222222` with pure black on the outer canvas (it removes visual separation from the bezel and `#000000` screen).
  * Add external drop shadows or manually round the corners of square (`1:1`) PNGs intended for Google Play or the APK.

---

## 6. Package Structure and Technical Integration

This visual identity directory (`img/onyx/`) contains:

```text
img/onyx/
├── VISUAL_IDENTITY_GUIDE.md
├── 01_full_logo_kit/
│   ├── 01_vectors_svg/
│   ├── 02_android_vectordrawable_xml/
│   ├── 03_4k_formats/
│   ├── 04_android_apk_icons/
│   ├── 05_color_variants_2048px/
│   └── 06_web_favicons/
└── 02_isotype_octagonal_o_kit/
    ├── 01_vectors_svg/
    ├── 02_android_vectordrawable_xml/
    ├── 03_4k_formats/
    ├── 04_android_apk_icons/
    ├── 05_color_variants_2048px/
    └── 06_web_favicons/
```

### Android Studio Integration (`app/src/main/res/`)
Choose the desired kit for the app launcher icon (**Isotype** is recommended for `ic_launcher` and **Full Logotype** for `tv_banner`, or the same kit for both) and copy the folders from `04_android_apk_icons/` (`drawable/`, `mipmap-anydpi-v26/`, and `mipmap-mdpi/` through `mipmap-xxxhdpi/`) into `app/src/main/res/`:

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:banner="@drawable/banner">
    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```
