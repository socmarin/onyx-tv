package im.marin.onyxtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Remote-friendly settings owned by Onyx. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "im.marin.onyxtv.extra.REORDER_FROM_SETTINGS";
    private static final String STATE_CHANGED = "changed";
    private static final String STATE_REFOCUS_LANGUAGE = "refocus_language";
    private static final String STATE_PENDING_BANNER_INPUT = "pending_banner_input";
    private static final int REQUEST_PICK_INPUT_BANNER = 104;
    private static final int REQUEST_PICK_APP_ICON = 105;
    private static final int REQUEST_PICK_APP_BANNER = 106;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private View wallpaperChoice;
    private TextView wallpaperValue;
    private View wallpaperReset;
    private View inputTilesChoice;
    private TextView inputTilesValue;
    private View inputCustomizeChoice;
    private TextView inputCustomizeValue;
    private InputCustomizeDialogs inputCustomizeDialogs;
    private View appCustomizeChoice;
    private TextView appCustomizeValue;
    private AppCustomizeDialogs appCustomizeDialogs;
    private View topBarButtonsChoice;
    private TextView topBarButtonsValue;
    private View initialFocusChoice;
    private TextView initialFocusValue;
    private View remoteKeysChoice;
    private TextView remoteKeysValue;
    private RemoteKeyCustomizeDialogs remoteKeyCustomizeDialogs;
    private String pendingBannerInputId;
    private View languageChoice;
    private TextView languageValue;
    private View openOnStartChoice;
    private TextView openOnStartValue;
    private View fireOsModeChoice;
    private TextView fireOsModeSub;
    private TextView fireOsModeValue;
    private View powerOnHomeChoice;
    private TextView powerOnHomeValue;
    private View powerOnHomeChevron;
    private View defaultHomeChoice;
    private TextView defaultHomeValue;
    private View defaultHomeChevron;
    private View defaultHomeHelp;
    private View followThemeChoice;
    private FrameLayout sidebarPreviewFrame;
    private ImageView sidebarWallpaperImage;
    private TextView sidebarWallpaperValue;
    private TextView sidebarLanguageValue;
    private Runnable activePickerRefresh;
    private final List<PreviewTile> previewApps = new ArrayList<>();
    private ExecutorService sidebarLoader;
    private final AtomicInteger sidebarPreviewToken = new AtomicInteger();

    private static final class PreviewTile {
        final Drawable banner;
        final Drawable icon;

        PreviewTile(Drawable banner, Drawable icon) {
            this.banner = banner;
            this.icon = icon;
        }
    }
    private int columns;
    private String clockFormat;
    private List<TvInputTiles.TileSpec> availableInputs = Collections.emptyList();
    private List<TvInputTiles.TileSpec> allInputs = Collections.emptyList();
    private List<AppCustom.AppInfoItem> allApps = Collections.emptyList();
    private Config currentConfig;
    private boolean wallpaperTrueColor;
    private boolean resultChanged;
    private boolean refocusLanguage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            if (state.getBoolean(STATE_CHANGED, false)) {
                resultChanged = true;
                setResult(RESULT_OK);
            }
            pendingBannerInputId = state.getString(STATE_PENDING_BANNER_INPUT, null);
        }
        setContentView(R.layout.activity_launcher_settings);
        sidebarLoader = Executors.newSingleThreadExecutor();
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        clockFormat = config.clockFormat;
        currentConfig = config;
        wallpaperTrueColor = config.wallpaperTrueColor;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperValue = findViewById(R.id.wallpaperValue);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperReset = findViewById(R.id.wallpaperReset);
        wallpaperReset.setOnClickListener(v -> resetWallpaper());
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(clockChoices);
            reverseRow(new View[]{wallpaperChoice, wallpaperReset});
        }
        inputTilesChoice = findViewById(R.id.inputTilesChoice);
        inputTilesValue = findViewById(R.id.inputTilesValue);
        inputCustomizeChoice = findViewById(R.id.inputCustomizeChoice);
        inputCustomizeValue = findViewById(R.id.inputCustomizeValue);
        View inputSection = findViewById(R.id.inputSection);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(new View[]{inputTilesChoice, inputCustomizeChoice});
        }
        allInputs = TvInputTiles.discoverAll(this, config);
        availableInputs = TvInputTiles.filterVisible(allInputs, config.hiddenInputs);
        inputCustomizeDialogs = new InputCustomizeDialogs(this, choices,
                () -> currentConfig,
                () -> {
                    changed();
                    Config updated = Config.load(this);
                    currentConfig = updated;
                    availableInputs = TvInputTiles.filterVisible(allInputs, updated.hiddenInputs);
                    loadPreviewApps(updated);
                    refresh();
                },
                this::launchBannerImagePicker);
        if (!allInputs.isEmpty()) {
            inputSection.setVisibility(View.VISIBLE);
            inputTilesChoice.setOnClickListener(v -> showInputTilesPicker());
            inputCustomizeChoice.setOnClickListener(v -> inputCustomizeDialogs.showInputList(allInputs));
        }
        appCustomizeChoice = findViewById(R.id.appCustomizeChoice);
        appCustomizeValue = findViewById(R.id.appCustomizeValue);
        allApps = AppCustom.discoverApps(this);
        appCustomizeDialogs = new AppCustomizeDialogs(this, choices,
                () -> currentConfig,
                () -> {
                    changed();
                    Config updated = Config.load(this);
                    currentConfig = updated;
                    loadPreviewApps(updated);
                    refresh();
                },
                pkg -> launchImagePicker(pkg, REQUEST_PICK_APP_ICON),
                pkg -> launchImagePicker(pkg, REQUEST_PICK_APP_BANNER));
        appCustomizeChoice.setOnClickListener(v -> appCustomizeDialogs.showAppList(allApps));

        topBarButtonsChoice = findViewById(R.id.topBarButtonsChoice);
        topBarButtonsValue = findViewById(R.id.topBarButtonsValue);
        topBarButtonsChoice.setOnClickListener(v -> showTopBarButtonsDialog());

        initialFocusChoice = findViewById(R.id.initialFocusChoice);
        initialFocusValue = findViewById(R.id.initialFocusValue);
        initialFocusChoice.setOnClickListener(v -> {
            boolean next = !currentConfig.initialFocus;
            choices.edit().putBoolean(Config.INITIAL_FOCUS_KEY, next).apply();
            changed();
            currentConfig = Config.load(this);
            refresh();
        });

        remoteKeysChoice = findViewById(R.id.remoteKeysChoice);
        remoteKeysValue = findViewById(R.id.remoteKeysValue);
        remoteKeyCustomizeDialogs = new RemoteKeyCustomizeDialogs(this, choices,
                () -> currentConfig,
                () -> allInputs,
                () -> allApps,
                () -> {
                    changed();
                    currentConfig = Config.load(this);
                    refresh();
                });
        remoteKeysChoice.setOnClickListener(v -> remoteKeyCustomizeDialogs.showShortcutList());

        languageChoice = findViewById(R.id.languageChoice);
        languageValue = findViewById(R.id.languageValue);
        languageChoice.setOnClickListener(v -> showLanguagePicker());
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        openOnStartValue = findViewById(R.id.openOnStartValue);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        fireOsModeChoice = findViewById(R.id.fireOsModeChoice);
        fireOsModeSub = findViewById(R.id.fireOsModeSub);
        fireOsModeValue = findViewById(R.id.fireOsModeValue);
        fireOsModeChoice.setOnClickListener(v -> {
            if (!currentConfig.fireOsDetected) return;
            boolean next = !currentConfig.fireOsMode;
            choices.edit().putBoolean(Config.FIRE_OS_MODE_KEY, next).apply();
            changed();
            currentConfig = Config.load(this);
            HomeKeyService.reloadIfRunning();
            refresh();
        });
        powerOnHomeChoice = findViewById(R.id.powerOnHomeChoice);
        powerOnHomeValue = findViewById(R.id.powerOnHomeValue);
        powerOnHomeChevron = findViewById(R.id.powerOnHomeChevron);
        powerOnHomeChoice.setOnClickListener(v -> {
            if (!currentConfig.fireOsDetected) showPowerOnHomePicker();
        });
        defaultHomeChoice = findViewById(R.id.defaultHomeChoice);
        defaultHomeValue = findViewById(R.id.defaultHomeValue);
        defaultHomeChevron = findViewById(R.id.defaultHomeChevron);
        defaultHomeHelp = findViewById(R.id.defaultHomeHelp);
        defaultHomeChoice.setOnClickListener(v -> {
            if (canUseSystemHomePicker()) open(new Intent(this, HomeSettingsActivity.class));
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> confirmReset());
        sidebarPreviewFrame = findViewById(R.id.sidebarPreviewFrame);
        sidebarWallpaperImage = sidebarPreviewFrame.findViewById(R.id.previewWallpaperImage);
        sidebarWallpaperImage.setClipToOutline(true);
        sidebarWallpaperValue = findViewById(R.id.sidebarWallpaperValue);
        sidebarLanguageValue = findViewById(R.id.sidebarLanguageValue);
        TextView versionLabel = findViewById(R.id.versionLabel);
        String version = appVersion();
        if (version != null) versionLabel.setText(version);
        else versionLabel.setVisibility(View.GONE);
        applyFocusLift(reorderChoice, wallpaperChoice, wallpaperReset, inputTilesChoice, inputCustomizeChoice,
                appCustomizeChoice, topBarButtonsChoice, initialFocusChoice, remoteKeysChoice,
                languageChoice, openOnStartChoice, fireOsModeChoice, powerOnHomeChoice, defaultHomeChoice, followThemeChoice);
        applyFocusLift(columnChoices);
        applyFocusLift(clockChoices);
        loadPreviewApps(config);
        refresh();
        if (state != null && state.getBoolean(STATE_REFOCUS_LANGUAGE, false)) {
            languageChoice.requestFocus();
        } else if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sidebarLoader != null) sidebarLoader.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_CHANGED, resultChanged);
        outState.putBoolean(STATE_REFOCUS_LANGUAGE, refocusLanguage);
        outState.putString(STATE_PENDING_BANNER_INPUT, pendingBannerInputId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != REQUEST_PICK_INPUT_BANNER
                && requestCode != REQUEST_PICK_APP_ICON
                && requestCode != REQUEST_PICK_APP_BANNER)
                || resultCode != RESULT_OK || data == null) return;
        android.net.Uri uri = data.getData();
        String targetId = pendingBannerInputId;
        pendingBannerInputId = null;
        if (uri == null || targetId == null || sidebarLoader == null) return;
        sidebarLoader.execute(() -> {
            final String filename;
            if (requestCode == REQUEST_PICK_APP_ICON) {
                filename = AppCustom.importIconImage(this, targetId, uri);
            } else if (requestCode == REQUEST_PICK_APP_BANNER) {
                filename = AppCustom.importBannerImage(this, targetId, uri);
            } else {
                filename = InputCustom.importBannerImage(this, targetId, uri);
            }
            sidebarPreviewFrame.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (filename == null) {
                    Toast.makeText(this, R.string.input_pick_image_error, Toast.LENGTH_LONG).show();
                    return;
                }
                if (requestCode == REQUEST_PICK_APP_ICON) {
                    CustomArt.setMapEntry(choices, Config.APP_ICONS_KEY, targetId, filename);
                } else if (requestCode == REQUEST_PICK_APP_BANNER) {
                    CustomArt.setMapEntry(choices, Config.APP_BANNERS_KEY, targetId, filename);
                } else {
                    InputCustom.setEntry(choices, Config.INPUT_BANNERS_KEY, targetId, filename);
                }
                changed();
                Config updated = Config.load(this);
                currentConfig = updated;
                loadPreviewApps(updated);
                refresh();
                if (requestCode == REQUEST_PICK_INPUT_BANNER && inputCustomizeDialogs != null) {
                    inputCustomizeDialogs.refreshActive();
                } else if (appCustomizeDialogs != null) {
                    appCustomizeDialogs.refreshActive();
                }
            });
        });
    }

    private void launchBannerImagePicker(String inputId) {
        launchImagePicker(inputId, REQUEST_PICK_INPUT_BANNER);
    }

    private void launchImagePicker(String key, int requestCode) {
        pendingBannerInputId = key;
        Intent openDoc = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        Intent intent = openDoc.resolveActivity(getPackageManager()) != null
                ? openDoc
                : new Intent(Intent.ACTION_GET_CONTENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("image/*");
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException | SecurityException e) {
            File baseDir = Config.dir(this);
            String folder = requestCode == REQUEST_PICK_APP_ICON
                    ? CustomArt.APP_ICONS_DIR
                    : (requestCode == REQUEST_PICK_APP_BANNER
                    ? CustomArt.APP_BANNERS_DIR : CustomArt.BANNERS_DIR);
            String target = baseDir != null
                    ? new File(baseDir, folder).getAbsolutePath()
                    : folder + "/";
            Toast.makeText(this, getString(R.string.input_pick_image_unavailable, target), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmReset() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.follow_theme)
                .setMessage(R.string.reset_settings_confirm)
                .setPositiveButton(R.string.confirm_yes, (d, which) -> resetSettings())
                .setNegativeButton(R.string.confirm_no, null)
                .show();
        Button noButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (noButton != null) noButton.requestFocus();
    }

    private void resetSettings() {
        String beforeLocale = Config.load(this).locale;
        SharedPreferences.Editor editor = choices.edit();
        Config.clearOnTvChoices(editor, true);
        editor.commit();
        Config current = Config.load(this);
        currentConfig = current;
        availableInputs = TvInputTiles.filterVisible(allInputs, current.hiddenInputs);
        columns = current.columns;
        clockFormat = current.clockFormat;
        changed();
        HomeKeyService.reloadIfRunning();
        if (!Objects.equals(beforeLocale, current.locale)) {
            recreate();
            return;
        }
        loadPreviewApps(current);
        refresh();
    }

    private static void applyFocusLift(View... views) {
        for (View view : views) {
            if (view == null) continue;
            view.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.015f : 1f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(110).start();
            });
        }
    }

    private void reverseRow(View[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void resetWallpaper() {
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        if (current == null || WallpaperRotation.CHOICE_NONE.equals(current)) return;
        choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
        changed();
        Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private List<File> availableWallpapers() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(Config.allWallpapers(this, dir));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        Collator collator = Collator.getInstance();
        images.sort((a, b) -> collator.compare(
                Config.wallpaperLabel(this, a.getName()),
                Config.wallpaperLabel(this, b.getName())));
        return images;
    }

    private void showWallpaperPicker() {
        List<File> images = availableWallpapers();
        String[] labels = new String[images.size() + 1];
        labels[0] = getString(R.string.wallpaper_theme_default);
        for (int i = 0; i < images.size(); i++) {
            labels[i + 1] = Config.wallpaperLabel(this, images.get(i).getName());
        }
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int found = indexOf(images, current);
        int checked = found >= 0 ? found + 1 : 0;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_wallpaper_picker, null);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        ListView listView = view.findViewById(R.id.wallpaperList);
        FrameLayout previewFrame = view.findViewById(R.id.wallpaperPreviewFrame);
        ImageView previewImage = previewFrame.findViewById(R.id.previewWallpaperImage);
        previewImage.setClipToOutline(true);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dialogWidth = Math.min(dp(860), Math.round(metrics.widthPixels * 0.88f));
        int listWidth = Math.min(dp(310), Math.round(dialogWidth * 0.37f));
        int listHeight = Math.min(dp(320), Math.round(metrics.heightPixels * 0.59f));
        int previewW = dialogWidth - listWidth - dp(80);
        int previewH = Math.round(previewW * 9f / 16f);
        int maxPreviewH = Math.min(dp(290), Math.round(metrics.heightPixels * 0.54f));
        if (previewH > maxPreviewH) {
            previewH = maxPreviewH;
            previewW = Math.round(previewH * 16f / 9f);
        }
        ViewGroup.LayoutParams listParams = listView.getLayoutParams();
        listParams.width = listWidth;
        listParams.height = listHeight;
        listView.setLayoutParams(listParams);
        ViewGroup.LayoutParams previewParams = previewFrame.getLayoutParams();
        previewParams.width = previewW;
        previewParams.height = previewH;
        previewFrame.setLayoutParams(previewParams);

        File defaultPreview = themeDefaultWallpaper();

        ExecutorService loader = Executors.newSingleThreadExecutor();
        LruCache<String, Bitmap> cache = new LruCache<>(8);
        AtomicInteger previewToken = new AtomicInteger();
        final int targetW = previewW;
        final int targetH = previewH;
        bindHomePreview(previewFrame, targetW, targetH);

        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        activePickerRefresh = () -> {
            if (dialog.isShowing()) bindHomePreview(previewFrame, targetW, targetH);
        };

        IntConsumer showPreview = position -> {
            if (position < 0 || position >= labels.length) return;
            File target = position == 0 ? defaultPreview : images.get(position - 1);
            if (target == null) {
                previewToken.incrementAndGet();
                previewImage.setImageDrawable(null);
                return;
            }
            String key = target.getAbsolutePath();
            Bitmap cached = cache.get(key);
            if (cached != null) {
                previewToken.incrementAndGet();
                previewImage.setImageBitmap(cached);
                return;
            }
            int token = previewToken.incrementAndGet();
            loader.execute(() -> {
                if (token != previewToken.get()) return;
                Bitmap decoded = decodeWallpaperPreview(target, targetW, targetH, wallpaperTrueColor);
                if (decoded == null) return;
                previewImage.post(() -> {
                    cache.put(key, decoded);
                    if (token == previewToken.get() && dialog.isShowing()) {
                        previewImage.setImageBitmap(decoded);
                    }
                });
            });
        };

        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, labels));
        listView.setItemChecked(checked, true);
        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                showPreview.accept(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        listView.setOnItemClickListener((parent, clickedView, which, id) -> {
            dialog.dismiss();
            if (which == 0) {
                choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
            } else {
                choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                        images.get(which - 1).getName()).apply();
            }
            changed();
            Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            refresh();
        });
        dialog.setOnShowListener(d -> {
            listView.requestFocus();
            listView.setSelection(checked);
            showPreview.accept(checked);
        });
        dialog.setOnDismissListener(d -> {
            activePickerRefresh = null;
            previewToken.incrementAndGet();
            previewImage.setImageDrawable(null);
            loader.shutdownNow();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showInputTilesPicker() {
        if (availableInputs.isEmpty()) return;
        String[] labels = new String[availableInputs.size()];
        for (int i = 0; i < availableInputs.size(); i++) {
            labels[i] = AppAdapter.inputLabel(currentConfig, availableInputs.get(i));
        }
        Set<String> selected = new HashSet<>(currentSelectedInputIds());

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_input_tiles, null);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        ListView listView = view.findViewById(R.id.inputTilesList);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dialogWidth = Math.min(dp(460), Math.round(metrics.widthPixels * 0.62f));
        int listHeight = Math.min(dp(300), dp(Math.max(120, availableInputs.size() * 52 + 16)));
        ViewGroup.LayoutParams lp = listView.getLayoutParams();
        lp.height = Math.min(listHeight, Math.round(metrics.heightPixels * 0.56f));
        listView.setLayoutParams(lp);

        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, labels));
        for (int i = 0; i < availableInputs.size(); i++) {
            listView.setItemChecked(i, selected.contains(availableInputs.get(i).inputId));
        }
        listView.setOnItemClickListener((parent, clickedView, pos, id) -> {
            String inputId = availableInputs.get(pos).inputId;
            if (listView.isItemChecked(pos)) selected.add(inputId);
            else selected.remove(inputId);
            choices.edit().putStringSet(Config.INPUT_TILES_SELECTED_KEY, new HashSet<>(selected)).apply();
            changed();
            Config updated = Config.load(this);
            currentConfig = updated;
            loadPreviewApps(updated);
            refresh();
        });

        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setOnShowListener(d -> listView.requestFocus());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private Set<String> currentSelectedInputIds() {
        if (choices.contains(Config.INPUT_TILES_SELECTED_KEY)) {
            Set<String> raw = choices.getStringSet(Config.INPUT_TILES_SELECTED_KEY, Collections.emptySet());
            return raw == null ? Collections.emptySet() : raw;
        }
        if (currentConfig.inputTiles) {
            Set<String> all = new HashSet<>();
            for (TvInputTiles.TileSpec spec : availableInputs) all.add(spec.inputId);
            return all;
        }
        return Collections.emptySet();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Bitmap decodeWallpaperPreview(File file, int targetW, int targetH, boolean trueColor) {
        if (file == null || targetW <= 0 || targetH <= 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, targetW, targetH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            options.inPreferredConfig = trueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private void showLanguagePicker() {
        List<LauncherLocale.LanguageOption> languages = LauncherLocale.supportedLanguages();
        String[] labels = new String[languages.size() + 1];
        labels[0] = getString(R.string.clock_format_system);
        String current = Config.load(this).locale;
        int checked = 0;
        for (int i = 0; i < languages.size(); i++) {
            labels[i + 1] = languages.get(i).label;
            if (languages.get(i).tag.equalsIgnoreCase(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String next = which == 0 ? "" : languages.get(which - 1).tag;
                    if (next.equalsIgnoreCase(current == null ? "" : current)) return;
                    choices.edit().putString(Config.LOCALE_KEY, next).commit();
                    changed();
                    refocusLanguage = true;
                    recreate();
                })
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        resultChanged = true;
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        List<File> images = availableWallpapers();
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int found = WallpaperRotation.CHOICE_NONE.equals(wallpaperCurrent)
                ? -1 : indexOf(images, wallpaperCurrent);
        boolean customWallpaper = found >= 0;
        String wallpaperText = customWallpaper
                ? Config.wallpaperLabel(this, wallpaperCurrent)
                : getString(R.string.wallpaper_theme_default);
        wallpaperValue.setText(wallpaperText);
        sidebarWallpaperValue.setText(wallpaperText);
        wallpaperReset.setAlpha(customWallpaper ? 1f : 0.45f);
        refreshSidebarWallpaper(customWallpaper ? images.get(found) : themeDefaultWallpaper());

        if (!allInputs.isEmpty()) {
            inputTilesChoice.setVisibility(availableInputs.isEmpty() ? View.GONE : View.VISIBLE);
            Set<String> active = currentSelectedInputIds();
            StringBuilder summary = new StringBuilder();
            for (TvInputTiles.TileSpec spec : availableInputs) {
                if (active.contains(spec.inputId)) {
                    if (summary.length() > 0) summary.append(", ");
                    summary.append(AppAdapter.inputLabel(currentConfig, spec));
                }
            }
            inputTilesValue.setText(summary.length() > 0
                    ? summary.toString()
                    : getString(R.string.input_tiles_count, getString(R.string.off), availableInputs.size()));

            java.util.Map<String, String> customLabels = InputCustom.readMap(choices, Config.INPUT_LABELS_KEY);
            java.util.Map<String, String> customBanners = InputCustom.readMap(choices, Config.INPUT_BANNERS_KEY);
            int customizedCount = 0;
            for (TvInputTiles.TileSpec spec : allInputs) {
                if (customLabels.containsKey(spec.inputId)
                        || customBanners.containsKey(spec.inputId)
                        || InputCustom.isHidden(currentConfig, spec.inputId)) {
                    customizedCount++;
                }
            }
            inputCustomizeValue.setText(customizedCount > 0
                    ? getString(R.string.input_customize_summary, customizedCount)
                    : getString(R.string.input_customize_hint));
        }

        String localeLabel = LauncherLocale.nativeLabel(Config.load(this).locale);
        String languageText = localeLabel != null ? localeLabel : getString(R.string.clock_format_system);
        languageValue.setText(languageText);
        sidebarLanguageValue.setText(languageText);

        int appCount = AppCustom.customizedCount(choices, currentConfig, allApps);
        appCustomizeValue.setText(appCount > 0
                ? getString(R.string.app_customize_summary, appCount)
                : getString(R.string.app_customize_hint));

        List<String> shownPills = new ArrayList<>();
        shownPills.add(getString(R.string.app_library));
        if (currentConfig.showInputsPill) shownPills.add(getString(R.string.top_bar_inputs));
        if (currentConfig.showNetworkPill) shownPills.add(getString(R.string.top_bar_network));
        if (currentConfig.showSettingsPill || currentConfig.topMenu) {
            shownPills.add(getString(R.string.top_bar_settings));
        }
        topBarButtonsValue.setText(String.join(" · ", shownPills));

        initialFocusValue.setText(getString(currentConfig.initialFocus ? R.string.on : R.string.off));

        int shortcutCount = RemoteKeyCustom.readShortcuts(choices).size();
        remoteKeysValue.setText(shortcutCount > 0
                ? getString(R.string.remote_keys_summary, shortcutCount)
                : getString(R.string.remote_keys_hint));

        openOnStartValue.setText(openOnStartLabel());

        boolean isFireOs = currentConfig.fireOsDetected;
        bindAvailabilityRow(fireOsModeChoice, null, isFireOs);
        fireOsModeSub.setText(R.string.fire_os_mode_desc);
        fireOsModeValue.setText(isFireOs
                ? getString(currentConfig.fireOsMode ? R.string.on : R.string.off)
                : getString(R.string.fire_os_only));

        bindAvailabilityRow(powerOnHomeChoice, powerOnHomeChevron, !isFireOs);
        powerOnHomeValue.setText(isFireOs
                ? getString(R.string.fire_os_unavailable)
                : powerOnHomeModeLabel(currentConfig.powerOnHomeMode));

        boolean homePickerAvailable = canUseSystemHomePicker();
        bindAvailabilityRow(defaultHomeChoice, defaultHomeChevron, homePickerAvailable);
        defaultHomeValue.setVisibility(homePickerAvailable ? View.GONE : View.VISIBLE);
        if (!homePickerAvailable) {
            defaultHomeValue.setText(isFireOs ? R.string.fire_os_unavailable : R.string.not_available);
        }
        defaultHomeHelp.setVisibility(homePickerAvailable ? View.VISIBLE : View.GONE);

        followThemeChoice.setSelected(!hasOnTvChoices());
        refreshSidebarHomePreview();
    }

    private boolean canUseSystemHomePicker() {
        return currentConfig != null && !currentConfig.fireOsDetected
                && SystemSettings.hasHomePicker(this, "");
    }

    private static void bindAvailabilityRow(View row, View chevron, boolean enabled) {
        row.setEnabled(enabled);
        row.setFocusable(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
        if (chevron != null) chevron.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private String powerOnHomeModeLabel(String mode) {
        if (Config.POWER_ON_ALWAYS.equals(mode)) return getString(R.string.power_on_home_always);
        if (Config.POWER_ON_LIVE_TV.equals(mode)) return getString(R.string.power_on_home_live_tv);
        return getString(R.string.off);
    }

    private void showPowerOnHomePicker() {
        String[] modes = {Config.POWER_ON_OFF, Config.POWER_ON_LIVE_TV, Config.POWER_ON_ALWAYS};
        String[] labels = {
                getString(R.string.off),
                getString(R.string.power_on_home_live_tv),
                getString(R.string.power_on_home_always)
        };
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(currentConfig.powerOnHomeMode)) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.power_on_home_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putString(Config.POWER_ON_HOME_MODE_KEY, modes[which]).apply();
                    changed();
                    currentConfig = Config.load(this);
                    HomeKeyService.reloadIfRunning();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTopBarButtonsDialog() {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(this, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.top_bar_buttons_label);

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(this, list, 4, 58);

        int[] icons = {
                R.drawable.ic_apps_pill,
                R.drawable.ic_wifi,
                R.drawable.ic_input,
                R.drawable.ic_settings
        };
        int[] titles = {
                R.string.app_library,
                R.string.top_bar_network,
                R.string.top_bar_inputs,
                R.string.top_bar_settings
        };
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return 4; }
            @Override public Object getItem(int p) { return p; }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(LauncherSettingsActivity.this)
                                .inflate(R.layout.item_input_custom_row, parent, false);
                row.findViewById(R.id.rowPreviewFrame).getLayoutParams().width =
                        InputCustomizeDialogs.dp(LauncherSettingsActivity.this, 45);
                row.findViewById(R.id.rowPreview).setVisibility(View.GONE);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                actionIcon.setVisibility(View.VISIBLE);
                actionIcon.setImageResource(icons[p]);
                ((TextView) row.findViewById(R.id.rowTitle)).setText(titles[p]);
                TextView sub = row.findViewById(R.id.rowSubtitle);
                TextView check = row.findViewById(R.id.rowCheck);
                row.findViewById(R.id.rowChevron).setVisibility(View.GONE);
                boolean checked = p == 0
                        || (p == 1 && currentConfig.showNetworkPill)
                        || (p == 2 && currentConfig.showInputsPill)
                        || (p == 3 && currentConfig.showSettingsPill);
                check.setVisibility(checked ? View.VISIBLE : View.GONE);
                if (p == 0) {
                    sub.setText(R.string.top_bar_library_locked);
                    sub.setVisibility(View.VISIBLE);
                    row.setAlpha(0.75f);
                } else {
                    sub.setVisibility(View.GONE);
                    row.setAlpha(1f);
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(this, root, 460);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos == 0) return;   // Bóveda de aplicaciones is mandatory so settings/apps remain reachable.
            String key = pos == 1 ? Config.SHOW_NETWORK_PILL_KEY
                    : (pos == 2 ? Config.SHOW_INPUTS_PILL_KEY : Config.SHOW_SETTINGS_PILL_KEY);
            boolean current = pos == 1 ? currentConfig.showNetworkPill
                    : (pos == 2 ? currentConfig.showInputsPill : currentConfig.showSettingsPill);
            choices.edit().putBoolean(key, !current).apply();
            changed();
            currentConfig = Config.load(this);
            refresh();
            adapter.notifyDataSetChanged();
        });
        list.requestFocus();
    }

    private File themeDefaultWallpaper() {
        File dir = Config.dir(this);
        File file = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        return file != null && file.isFile() && file.length() > 0 ? file : null;
    }

    private void refreshSidebarWallpaper(File file) {
        int token = sidebarPreviewToken.incrementAndGet();
        if (file == null) {
            sidebarWallpaperImage.setImageDrawable(null);
            return;
        }
        int targetW = dp(228);
        int targetH = dp(128);
        sidebarLoader.execute(() -> {
            if (token != sidebarPreviewToken.get()) return;
            Bitmap decoded = decodeWallpaperPreview(file, targetW, targetH, wallpaperTrueColor);
            if (decoded == null) return;
            sidebarWallpaperImage.post(() -> {
                if (token == sidebarPreviewToken.get() && !isDestroyed() && !isFinishing()) {
                    sidebarWallpaperImage.setImageBitmap(decoded);
                }
            });
        });
    }

    private void loadPreviewApps(Config config) {
        sidebarLoader.execute(() -> {
            List<AppAdapter.Entry> entries = AppAdapter.loadHomePreviewEntries(this, config, 14);
            List<PreviewTile> tiles = new ArrayList<>(entries.size());
            for (AppAdapter.Entry e : entries) {
                tiles.add(new PreviewTile(e.banner, e.icon));
            }
            sidebarPreviewFrame.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                previewApps.clear();
                previewApps.addAll(tiles);
                refreshSidebarHomePreview();
                if (activePickerRefresh != null) activePickerRefresh.run();
            });
        });
    }

    private void refreshSidebarHomePreview() {
        bindHomePreview(sidebarPreviewFrame, dp(228), dp(128));
    }

    private void bindHomePreview(View container, int boxW, int boxH) {
        float density = getResources().getDisplayMetrics().density;
        int innerW = Math.max(1, boxW - dp(2));
        int innerH = Math.max(1, boxH - dp(2));
        float ratio = innerH / (540f * density);

        RelativeLayout overlay = container.findViewById(R.id.previewOverlay);
        int padH = Math.max(dp(8), Math.round(56 * density * ratio));
        int padTop = Math.max(dp(6), Math.round(26 * density * ratio));
        overlay.setPadding(padH, padTop, padH, padTop);

        TextView greeting = container.findViewById(R.id.previewGreeting);
        if (currentConfig.showGreeting) {
            greeting.setVisibility(View.VISIBLE);
            greeting.setText(currentGreeting());
            greeting.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(9f, 12f * density * ratio));
        } else {
            greeting.setVisibility(View.GONE);
        }

        boolean is24 = ClockFormat.is24(this, clockFormat);
        String timePattern = is24 ? "HH:mm" : "h:mm";
        TextClock clock = container.findViewById(R.id.previewClock);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(22f, 52f * density * ratio));
        clock.setFormat12Hour(timePattern);
        clock.setFormat24Hour(timePattern);

        TextClock ampm = container.findViewById(R.id.previewAmPm);
        ampm.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(8f, 15f * density * ratio));
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("a");
        LinearLayout.LayoutParams ampmLp = (LinearLayout.LayoutParams) ampm.getLayoutParams();
        ampmLp.setMarginStart(Math.max(dp(2), Math.round(6 * density * ratio)));
        ampmLp.bottomMargin = Math.max(dp(2), Math.round(8 * density * ratio));
        ampm.setLayoutParams(ampmLp);
        ampm.setVisibility(is24 ? View.GONE : View.VISIBLE);

        View divider = container.findViewById(R.id.previewDivider);
        LinearLayout.LayoutParams divLp = (LinearLayout.LayoutParams) divider.getLayoutParams();
        divLp.width = Math.max(1, Math.round(density * ratio));
        divLp.height = Math.max(dp(7), Math.round(26 * density * ratio));
        int divGap = Math.max(dp(4), Math.round(16 * density * ratio));
        divLp.setMarginStart(divGap);
        divLp.setMarginEnd(divGap);
        divLp.bottomMargin = Math.max(dp(2), Math.round(8 * density * ratio));
        divider.setLayoutParams(divLp);

        TextClock date = container.findViewById(R.id.previewDate);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(8f, 14f * density * ratio));
        date.setLineSpacing(6f * density * ratio, 1f);
        LinearLayout.LayoutParams dateLp = (LinearLayout.LayoutParams) date.getLayoutParams();
        dateLp.bottomMargin = Math.max(dp(2), Math.round(9 * density * ratio));
        date.setLayoutParams(dateLp);

        LinearLayout pills = container.findViewById(R.id.previewPills);
        RelativeLayout.LayoutParams pillsLp = (RelativeLayout.LayoutParams) pills.getLayoutParams();
        pillsLp.bottomMargin = Math.max(dp(2), Math.round(6 * density * ratio));
        pills.setLayoutParams(pillsLp);
        renderPreviewPills(pills, density, ratio);

        LinearLayout dock = container.findViewById(R.id.previewDock);
        renderPreviewDock(dock, innerW, innerH, density, ratio, padH, padTop);
    }

    private void renderPreviewPills(LinearLayout pills, float density, float ratio) {
        pills.removeAllViews();
        if (currentConfig.libraryButton && currentConfig.appLibrary) {
            addPreviewPill(pills, R.drawable.ic_apps_pill, null, density, ratio);
        }
        if (currentConfig.showInputsPill) {
            addPreviewPill(pills, R.drawable.ic_input,
                    (currentConfig.iconOnlyButtons || currentConfig.iconOnlyInputs)
                            ? null : getString(R.string.choose_input), density, ratio);
        }
        if (currentConfig.showNetworkPill) {
            addPreviewPill(pills, R.drawable.ic_wifi,
                    currentConfig.iconOnlyButtons ? null : getString(R.string.wifi), density, ratio);
        }
        if (currentConfig.reorderButton) {
            addPreviewPill(pills, R.drawable.ic_swap_vert, null, density, ratio);
        }
        if (currentConfig.showSettingsPill || currentConfig.topMenu) {
            int settingsIcon = currentConfig.launcherSettingsButton
                    ? R.drawable.ic_home_settings_pill : R.drawable.ic_settings;
            addPreviewPill(pills, settingsIcon,
                    currentConfig.iconOnlyButtons ? null : getString(R.string.settings), density, ratio);
        }
    }

    private void addPreviewPill(LinearLayout pills, int iconRes, String text, float density, float ratio) {
        int size = Math.max(dp(10), Math.round(42 * density * ratio));
        LinearLayout pill = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                text == null ? size : LinearLayout.LayoutParams.WRAP_CONTENT, size);
        if (pills.getChildCount() > 0) {
            lp.setMarginStart(Math.max(dp(3), Math.round(10 * density * ratio)));
        }
        pill.setLayoutParams(lp);
        pill.setBackgroundResource(R.drawable.mini_home_pill);
        pill.setGravity(Gravity.CENTER);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        if (text != null) {
            int pStart = Math.max(dp(4), Math.round(16 * density * ratio));
            int pEnd = Math.max(dp(5), Math.round(20 * density * ratio));
            pill.setPadding(pStart, 0, pEnd, 0);
        }

        ImageView icon = new ImageView(this);
        int iconSize = Math.max(dp(6), Math.round(22 * density * ratio));
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xE6FFFFFF);
        pill.addView(icon);

        if (text != null) {
            TextView label = new TextView(this);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.setMarginStart(Math.max(dp(2), Math.round(9 * density * ratio)));
            label.setLayoutParams(labelLp);
            label.setText(text);
            label.setTextColor(0xE6FFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(8f, 15f * density * ratio));
            label.setSingleLine(true);
            pill.addView(label);
        }
        pills.addView(pill);
    }

    private void renderPreviewDock(LinearLayout dock, int innerW, int innerH,
                                   float density, float ratio, int padH, int padTop) {
        int count = Math.max(4, Math.min(columns, 7));
        int tvGridH = 426;
        int tvInnerW = 828;
        int tvTileW = AppAdapter.tileWidthPx(tvInnerW, tvGridH, count, currentConfig.rowsBelowDock,
                currentConfig.dockTitle, currentConfig.dockRow, 1f);
        int tvTileH = AppAdapter.tileHeightPx(tvTileW);
        int tvItemH = AppAdapter.itemHeightPx(tvTileH, currentConfig.dockRow, 1f);
        int dockRowDp = tvItemH + (currentConfig.dockRow ? Math.round(AppAdapter.DOCK_EXTRA_DP) : 0);
        int gridRowsDp = tvItemH * currentConfig.rowsBelowDock;
        int topPaddingDp = DockSlots.topPaddingPx(tvGridH, dockRowDp, gridRowsDp, 0,
                currentConfig.rowsBelowDock, currentConfig.dockRow, currentConfig.dockTitle,
                currentConfig.dockMarginTop, currentConfig.singleRow, currentConfig.alwaysShowNames, 1f);
        int startYDp = 114 + topPaddingDp;

        float scaleY = innerH / 540f;
        int gap = Math.max(dp(3), Math.round(14 * density * ratio));
        int dockInnerW = Math.max(1, innerW - padH * 2);
        int tileH = Math.max(dp(11), Math.round((dockInnerW - gap * (count - 1)) / (float) count * 9f / 16f));
        int minTop = Math.max(dp(22), Math.round(96 * scaleY));
        int topMargin = Math.max(minTop, Math.min(innerH - padTop - tileH - dp(2),
                Math.round(startYDp * scaleY) - padTop));
        int rowGap = Math.max(dp(4), Math.round((tvItemH - tvTileH) * scaleY));
        boolean showSecondRow = !currentConfig.singleRow
                && (!currentConfig.dockRow || currentConfig.rowsBelowDock > 0)
                && (topMargin + padTop + tileH * 2 + rowGap <= innerH - dp(4));

        RelativeLayout.LayoutParams dockLp = (RelativeLayout.LayoutParams) dock.getLayoutParams();
        dockLp.topMargin = topMargin;
        dock.setLayoutParams(dockLp);
        dock.removeAllViews();

        int rows = showSecondRow ? 2 : 1;
        for (int row = 0; row < rows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row > 0) rowLp.topMargin = rowGap;
            rowLayout.setLayoutParams(rowLp);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < count; col++) {
                int idx = row * count + col;
                FrameLayout tile = new FrameLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tileH, 1f);
                if (col < count - 1) lp.setMarginEnd(gap);
                tile.setLayoutParams(lp);
                tile.setBackgroundResource(idx == 0 && currentConfig.initialFocus
                        ? R.drawable.mini_home_tile_focused : R.drawable.mini_home_tile);
                tile.setClipToOutline(true);
                int strokePad = Math.max(1, Math.round(density * ratio));
                tile.setPadding(strokePad, strokePad, strokePad, strokePad);
                if (idx < previewApps.size()) {
                    PreviewTile app = previewApps.get(idx);
                    ImageView img = new ImageView(this);
                    if (app.banner != null) {
                        img.setLayoutParams(new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        img.setImageDrawable(app.banner);
                    } else if (app.icon != null) {
                        int iconSize = Math.round(tileH * 0.65f);
                        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
                        iconLp.gravity = Gravity.CENTER;
                        img.setLayoutParams(iconLp);
                        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        img.setImageDrawable(app.icon);
                    }
                    tile.addView(img);
                }
                rowLayout.addView(tile);
            }
            dock.addView(rowLayout);
        }
    }

    private String currentGreeting() {
        int hour = LocalTime.now().getHour();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        int res;
        if (("es".equals(lang) && "ES".equals(country)) || "ca".equals(lang)) {
            if (hour < 6) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 21) res = R.string.good_afternoon;
            else res = R.string.good_night;
        } else {
            if (hour < 5) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 17) res = R.string.good_afternoon;
            else if (hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        }
        return getString(res);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (choices != null) {
            currentConfig = Config.load(this);
            refresh();
            if (remoteKeyCustomizeDialogs != null) remoteKeyCustomizeDialogs.refreshActive();
        }
    }

    private boolean hasOnTvChoices() {
        return Config.hasOnTvChoices(choices);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private String appVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null && !info.versionName.isEmpty()) {
                return "v" + info.versionName;
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        return null;
    }
}
