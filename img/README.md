# Visual Assets (`img/`)

Canonical repository assets packaged at build time or provided as brand artwork:

- [`onyx/`](onyx/) — Official Onyx TV Launcher visual identity ([`VISUAL_IDENTITY_GUIDE.md`](onyx/VISUAL_IDENTITY_GUIDE.md)):
  - [`01_full_logo_kit/`](onyx/01_full_logo_kit/) — Full "ONYX" logotype kit (used for the TV Home banner)
  - [`02_isotype_octagonal_o_kit/`](onyx/02_isotype_octagonal_o_kit/) — Compact octagonal "O" isotype kit (used for `ic_launcher` / 1:1 app icon)
- [`banners/`](banners/) — Built-in 16:9 TV input banners (`firetv.jpg`, `tv-spain.jpg`, `xbox.jpg`), packaged directly into the APK (`assets/input_banners/`) for **Customize inputs**.
- [`wallpapers/`](wallpapers/) — Built-in 4K dark wallpapers (`img/wallpapers/<category>/<name>`), packaged directly into the APK. See [`wallpapers/README.md`](wallpapers/README.md) for the full catalog and authoring workflow.

## Custom Tile & Input Banners (`960×540`)

- **Recommended size:** **`960×540`** (exact 16:9 aspect ratio). Loaded natively without downsampling and remains sharp on large 4K TVs while keeping memory usage low.
- **Format:** `.jpg` / `.webp` for full-bleed dark tiles, or transparent `.png` when importing logos via the in-app customizer.
