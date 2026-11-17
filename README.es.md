# Onyx TV Launcher

[English](README.md) · [Español](README.es.md)

**Onyx** es un launcher y tema minimalista en negro puro, sin distracciones, para Android TV, Google TV y Fire OS 7, basado en [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv).

![Pantalla principal Onyx TV](themes/onyx/screenshot.jpg)

## Características

- **Fondo negro puro (`#0E0F12`):** sin fondos de pantalla ni brillos ambientales, ideal para televisores OLED y de alto contraste.
- **Home minimalista de 5 columnas:** `dock_margin_top` (`145`) y `focus_scale` (`1.25x`) ajustables, resaltado de foco fino (`glass_style: "rim"`) y arranque silencioso opcional (`initial_focus: false`).
- **Bóveda de aplicaciones en cuadrícula (6 columnas):** cajón de apps limpio (`AppLibraryDialog`) con indicador de apps fijadas en la Home y menú contextual al mantener pulsado OK.
- **Barra superior solo con iconos:** botones flotantes (Aplicaciones, Entradas, Ajustes) sin píldoras ni texto extra.
- **Soporte Fire OS 7:** gestión genérica con `stock_home_redirect` (`BootReceiver`, `GuardRevival`, `GuardKeep`, retorno tras reposo), apertura directa de Appstore (`FEATURED_LAUNCH`), intercepción del grid de Apps hacia la bóveda (`library`), resincronización del nombre Bluetooth, guarda opcional `fire_remote_bt_power_mac` y `window_redirects` configurables.
- **Arte local personalizado (`img/`):** banners de tiles (`files/banners/`) e iconos en la bóveda (`files/icons/`) mediante `app_banners` y `app_icons`, sin necesitar permiso `INTERNET`.
- **`launch_actions` flexibles:** soporta acciones entre paquetes (`pkg:action`), actividades explícitas (`pkg/.Activity`), entradas TV (`input:<id>`) y URIs (`view:<uri>`).
- **Multiidioma:** hereda el soporte completo de más de 30 idiomas de CleanYourTV (español, inglés, alemán, francés, italiano, portugués, neerlandés, polaco, ucraniano, turco, japonés, chino, etc.).

![Navegación Onyx TV](themes/onyx/preview.gif)

## Instalación (Android 8.0+ / API 26+)

### Opción 1: APK ya compilado (Recomendada)

1. Descarga `app-release.apk` desde la sección [Releases](../../releases/latest).
2. Instálalo en tu tele por ADB (`adb install -r app-release.apk`), o pásalo directamente a la tele con un gestor de archivos / LocalSend / USB.
3. Aplica la configuración del tema Onyx de este repositorio (ver más abajo), o ajústalo directamente desde **Ajustes → Ajustes del launcher** en la tele.

### Opción 2: Compilar e instalar desde el código fuente

```bash
TV_HOST=<ip-de-la-tele> tools/install.sh
```

O en Windows (PowerShell):

```powershell
$env:TV_HOST = "<ip-de-la-tele>"
.\tools\install.ps1
```

### Aplicar el tema Onyx

```bash
TV_HOST=<ip-de-la-tele> tools/theme.sh themes/onyx
```

Para volver a la configuración por defecto:

```bash
TV_HOST=<ip-de-la-tele> tools/theme.sh --reset
```

## Créditos

Basado en [CleanYourTV](https://github.com/Gathin23/cleanyourtv) de Gathin23. Licencia MIT — ver [LICENSE](LICENSE).
