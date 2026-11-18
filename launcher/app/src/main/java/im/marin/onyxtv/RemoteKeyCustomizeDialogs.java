package im.marin.onyxtv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Live-learning remote shortcut dialogs under Onyx settings:
 * capture any remote button press, choose tap or hold, and bind it to an app, TV input, or action.
 */
final class RemoteKeyCustomizeDialogs {
    private final Activity activity;
    private final SharedPreferences choices;
    private final Supplier<Config> configSupplier;
    private final Supplier<List<TvInputTiles.TileSpec>> inputsSupplier;
    private final Supplier<List<AppCustom.AppInfoItem>> appsSupplier;
    private final Runnable onChanged;
    private final List<Runnable> activeRefreshes = new ArrayList<>();

    RemoteKeyCustomizeDialogs(Activity activity, SharedPreferences choices,
                              Supplier<Config> configSupplier,
                              Supplier<List<TvInputTiles.TileSpec>> inputsSupplier,
                              Supplier<List<AppCustom.AppInfoItem>> appsSupplier,
                              Runnable onChanged) {
        this.activity = activity;
        this.choices = choices;
        this.configSupplier = configSupplier;
        this.inputsSupplier = inputsSupplier;
        this.appsSupplier = appsSupplier;
        this.onChanged = onChanged;
    }

    void refreshActive() {
        for (int i = activeRefreshes.size() - 1; i >= 0; i--) {
            activeRefreshes.get(i).run();
        }
    }

    void showShortcutList() {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.remote_keys_label);

        List<RemoteKeyCustom.Shortcut> shortcuts = new ArrayList<>();
        ListView list = root.findViewById(R.id.inputCustomizeList);

        Runnable reloadList = () -> {
            shortcuts.clear();
            shortcuts.addAll(RemoteKeyCustom.readShortcuts(choices));
            InputCustomizeDialogs.sizeList(activity, list, shortcuts.size() + 2, 62);
        };
        reloadList.run();

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return 2 + shortcuts.size();
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                View frame = row.findViewById(R.id.rowPreviewFrame);
                ViewGroup.LayoutParams flp = frame.getLayoutParams();
                int targetW = InputCustomizeDialogs.dp(activity, 45);
                if (flp.width != targetW) {
                    flp.width = targetW;
                    frame.setLayoutParams(flp);
                }
                ImageView preview = row.findViewById(R.id.rowPreview);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                TextView rowTitle = row.findViewById(R.id.rowTitle);
                TextView rowSub = row.findViewById(R.id.rowSubtitle);
                TextView check = row.findViewById(R.id.rowCheck);
                View chevron = row.findViewById(R.id.rowChevron);
                check.setVisibility(View.GONE);
                row.setAlpha(1f);

                if (position == 0) {
                    preview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(R.drawable.ic_remote);
                    rowTitle.setText(R.string.remote_key_add);
                    rowSub.setText(R.string.remote_key_capture_title);
                    chevron.setVisibility(View.VISIBLE);
                } else if (position == 1) {
                    boolean active = RemoteKeyCustom.isAccessibilityServiceEnabled(activity);
                    preview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(active ? R.drawable.ic_lock_open : R.drawable.ic_settings);
                    rowTitle.setText(R.string.remote_key_service_label);
                    rowSub.setText(active ? R.string.remote_key_service_on : R.string.remote_key_service_off);
                    rowSub.setSingleLine(false);
                    rowSub.setMaxLines(2);
                    check.setVisibility(active ? View.VISIBLE : View.GONE);
                    chevron.setVisibility(active ? View.GONE : View.VISIBLE);
                } else {
                    rowSub.setSingleLine(true);
                    rowSub.setMaxLines(1);
                    RemoteKeyCustom.Shortcut item = shortcuts.get(position - 2);
                    bindShortcutArt(item, preview, actionIcon);
                    rowTitle.setText(RemoteKeyCustom.prettyKeyName(activity, item.keyToken));
                    String modeLabel = activity.getString(
                            item.hold ? R.string.remote_key_mode_hold : R.string.remote_key_mode_tap);
                    rowSub.setText(modeLabel + " · " + describeTarget(item.target));
                    chevron.setVisibility(View.VISIBLE);
                }
                return row;
            }
        };

        list.setAdapter(adapter);
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 540);
        Runnable refresh = () -> {
            reloadList.run();
            adapter.notifyDataSetChanged();
        };
        activeRefreshes.add(refresh);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(refresh));

        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos == 0) {
                showKeyCaptureDialog();
            } else if (pos == 1) {
                openAccessibilitySettings();
            } else {
                showEditShortcutDialog(pos - 2, shortcuts.get(pos - 2));
            }
        });
        list.requestFocus();
    }

    private void showKeyCaptureDialog() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.wallpaper_picker_dialog_bg);
        int pad = InputCustomizeDialogs.dp(activity, 26);
        content.setPadding(pad, pad, pad, pad);
        InputCustomizeDialogs.applyRtl(activity, content);

        TextView title = new TextView(activity);
        title.setText(R.string.remote_key_capture_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f);
        content.addView(title);

        ImageView icon = new ImageView(activity);
        int iconSize = InputCustomizeDialogs.dp(activity, 52);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconLp.topMargin = InputCustomizeDialogs.dp(activity, 20);
        iconLp.bottomMargin = InputCustomizeDialogs.dp(activity, 14);
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_remote);
        icon.setColorFilter(0xE6FFFFFF);
        content.addView(icon);

        TextView hint = new TextView(activity);
        hint.setText(R.string.remote_key_capture_hint);
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(hint);

        TextView cancel = new TextView(activity);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.END;
        cancelLp.topMargin = InputCustomizeDialogs.dp(activity, 22);
        cancel.setLayoutParams(cancelLp);
        cancel.setBackgroundResource(R.drawable.settings_row_bg);
        cancel.setFocusable(true);
        cancel.setClickable(true);
        cancel.setText(android.R.string.cancel);
        cancel.setTextColor(activity.getColorStateList(R.color.settings_choice_text));
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        int hPad = InputCustomizeDialogs.dp(activity, 18);
        int vPad = InputCustomizeDialogs.dp(activity, 10);
        cancel.setPadding(hPad, vPad, hPad, vPad);
        content.addView(cancel);

        final boolean[] sawBackDown = new boolean[1];
        final boolean[] sawCaptureDown = new boolean[1];
        final boolean[] completed = new boolean[1];
        final long[] shownAt = {android.os.SystemClock.uptimeMillis()};
        final long[] lastDownTime = {-1L};
        final Dialog[] holder = new Dialog[1];

        RemoteKeyCustom.KeyCaptureHandler captureHandler = event -> {
            int code = event.getKeyCode();
            if (RemoteKeyCustom.isReservedNavigationKey(code)) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                sawCaptureDown[0] = true;
                lastDownTime[0] = event.getDownTime();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean valid = sawCaptureDown[0] || (event.getEventTime() - shownAt[0] > 250);
                if (valid && !completed[0]) {
                    String token = RemoteKeyCustom.formatCapturedKeyToken(code, event.getScanCode());
                    if (!token.isEmpty()) {
                        completed[0] = true;
                        RemoteKeyCustom.activeCapture = null;
                        HomeKeyService.setWindowCapture(null);
                        activity.runOnUiThread(() -> {
                            if (holder[0] != null && holder[0].isShowing()) holder[0].dismiss();
                            showModePicker(token);
                        });
                    }
                }
                return true;
            }
            return true;
        };

        RemoteKeyCustom.WindowCaptureHandler windowHandler = (pkg, cls, detailsTitle) -> {
            if (completed[0]) return false;
            String token;
            if (detailsTitle != null && !detailsTitle.isEmpty()) {
                token = RemoteKeyCustom.WINDOW_TOKEN_PREFIX + "details:" + detailsTitle;
            } else if (HomeKeyService.VENEZIA_PKG.equals(pkg) && HomeKeyService.VENEZIA_APPS_GRID.equals(cls)) {
                token = RemoteKeyCustom.WINDOW_TOKEN_PREFIX + pkg + "/" + cls;
            } else {
                token = RemoteKeyCustom.WINDOW_TOKEN_PREFIX + pkg;
            }
            completed[0] = true;
            RemoteKeyCustom.activeCapture = null;
            HomeKeyService.setWindowCapture(null);
            activity.runOnUiThread(() -> {
                if (holder[0] != null && holder[0].isShowing()) holder[0].dismiss();
                Intent bringBack = new Intent(activity, LauncherSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(bringBack);
                showTargetCategoryPicker(token, false);
            });
            return true;
        };

        Dialog captureDialog = new Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                int code = event.getKeyCode();
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) sawBackDown[0] = true;
                    else if (event.getAction() == KeyEvent.ACTION_UP && sawBackDown[0]) dismiss();
                    return true;
                }
                if (RemoteKeyCustom.isReservedNavigationKey(code)) {
                    if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                            || code == KeyEvent.KEYCODE_NUMPAD_ENTER) && cancel.isFocused()) {
                        return super.dispatchKeyEvent(event);
                    }
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        Toast.makeText(activity, R.string.remote_key_reserved, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getDownTime() == lastDownTime[0]) {
                    return true;
                }
                return captureHandler.onKeyEvent(event);
            }
        };
        holder[0] = captureDialog;
        RemoteKeyCustom.activeCapture = captureHandler;
        HomeKeyService.setWindowCapture(windowHandler);
        captureDialog.setOnDismissListener(d -> {
            if (RemoteKeyCustom.activeCapture == captureHandler) {
                RemoteKeyCustom.activeCapture = null;
            }
            if (RemoteKeyCustom.activeWindowCapture == windowHandler) {
                HomeKeyService.setWindowCapture(null);
            }
        });
        captureDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        captureDialog.setContentView(content);
        captureDialog.show();
        if (captureDialog.getWindow() != null) {
            int width = Math.min(InputCustomizeDialogs.dp(activity, 460),
                    Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.64f));
            captureDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            captureDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        cancel.setOnClickListener(v -> captureDialog.dismiss());
        cancel.requestFocus();
    }

    private void showModePicker(String keyToken) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(
                RemoteKeyCustom.prettyKeyName(activity, keyToken) + " · "
                        + activity.getString(R.string.remote_key_mode_title));

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, 2, 62);

        String[] titles = {
                activity.getString(R.string.remote_key_mode_tap),
                activity.getString(R.string.remote_key_mode_hold)
        };
        list.setAdapter(simpleRowsAdapter(titles, new String[]{"", ""},
                new int[]{R.drawable.ic_remote, R.drawable.ic_clock}));
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 460);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            dialog.dismiss();
            showTargetCategoryPicker(keyToken, pos == 1);
        });
        list.requestFocus();
    }

    private void showTargetCategoryPicker(String keyToken, boolean hold) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.remote_key_target_title);

        List<TvInputTiles.TileSpec> inputs = inputsSupplier.get();
        boolean hasInputs = inputs != null && !inputs.isEmpty();

        List<Integer> kinds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        kinds.add(0);
        titles.add(activity.getString(R.string.remote_key_tab_apps));
        icons.add(R.drawable.ic_apps);
        if (hasInputs) {
            kinds.add(1);
            titles.add(activity.getString(R.string.remote_key_tab_inputs));
            icons.add(R.drawable.ic_input);
        }
        kinds.add(2);
        titles.add(activity.getString(R.string.remote_key_tab_actions));
        icons.add(R.drawable.ic_settings);

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, kinds.size(), 62);
        int[] iconArr = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) iconArr[i] = icons.get(i);
        list.setAdapter(simpleRowsAdapter(titles.toArray(new String[0]), new String[titles.size()], iconArr));
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 460);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            dialog.dismiss();
            int kind = kinds.get(pos);
            if (kind == 0) showAppTargetPicker(keyToken, hold);
            else if (kind == 1) showInputTargetPicker(keyToken, hold, inputs);
            else showActionTargetPicker(keyToken, hold);
        });
        list.requestFocus();
    }

    private void showAppTargetPicker(String keyToken, boolean hold) {
        List<AppCustom.AppInfoItem> apps = appsSupplier.get();
        if (apps == null || apps.isEmpty()) return;
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.remote_key_tab_apps);

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, apps.size(), 58);
        PackageManager pm = activity.getPackageManager();
        Config cfg = configSupplier.get();

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public AppCustom.AppInfoItem getItem(int p) { return apps.get(p); }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                AppCustom.AppInfoItem app = apps.get(p);
                View frame = row.findViewById(R.id.rowPreviewFrame);
                frame.getLayoutParams().width = InputCustomizeDialogs.dp(activity, 45);
                ImageView preview = row.findViewById(R.id.rowPreview);
                preview.setClipToOutline(true);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setVisibility(View.VISIBLE);
                String customIcon = cfg != null ? cfg.appIcons.get(app.pkg) : null;
                android.graphics.drawable.Drawable d = customIcon != null ? AppAdapter.customIcon(activity, customIcon) : null;
                preview.setImageDrawable(d != null ? d : app.resolveInfo.loadIcon(pm));
                row.findViewById(R.id.rowActionIcon).setVisibility(View.GONE);
                row.findViewById(R.id.rowCheck).setVisibility(View.GONE);
                row.findViewById(R.id.rowChevron).setVisibility(View.GONE);
                String label = cfg != null && cfg.appLabels.containsKey(app.pkg)
                        ? cfg.appLabels.get(app.pkg) : app.originalLabel;
                ((TextView) row.findViewById(R.id.rowTitle)).setText(label);
                row.findViewById(R.id.rowSubtitle).setVisibility(View.GONE);
                return row;
            }
        };
        list.setAdapter(adapter);
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 500);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            saveShortcut(keyToken, hold, apps.get(pos).pkg);
            dialog.dismiss();
        });
        list.requestFocus();
    }

    private void showInputTargetPicker(String keyToken, boolean hold, List<TvInputTiles.TileSpec> inputs) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.remote_key_tab_inputs);

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, inputs.size(), 58);
        Config cfg = configSupplier.get();

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return inputs.size(); }
            @Override public TvInputTiles.TileSpec getItem(int p) { return inputs.get(p); }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                TvInputTiles.TileSpec spec = inputs.get(p);
                String label = AppAdapter.inputLabel(cfg, spec);
                String bannerRef = cfg != null ? cfg.appBanners.get(spec.pkg) : null;
                ImageView preview = row.findViewById(R.id.rowPreview);
                preview.setClipToOutline(true);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setVisibility(View.VISIBLE);
                preview.setImageDrawable(TvInputTiles.renderBanner(activity, spec, label, bannerRef));
                row.findViewById(R.id.rowActionIcon).setVisibility(View.GONE);
                row.findViewById(R.id.rowCheck).setVisibility(View.GONE);
                row.findViewById(R.id.rowChevron).setVisibility(View.GONE);
                ((TextView) row.findViewById(R.id.rowTitle)).setText(label);
                TextView sub = row.findViewById(R.id.rowSubtitle);
                if (!label.equals(spec.label)) {
                    sub.setText(spec.label);
                    sub.setVisibility(View.VISIBLE);
                } else {
                    sub.setVisibility(View.GONE);
                }
                return row;
            }
        };
        list.setAdapter(adapter);
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 500);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            saveShortcut(keyToken, hold, TvInputTiles.INPUT_PREFIX + inputs.get(pos).inputId);
            dialog.dismiss();
        });
        list.requestFocus();
    }

    private void showActionTargetPicker(String keyToken, boolean hold) {
        List<String[]> actions = new ArrayList<>();
        actions.add(new String[]{"library", activity.getString(R.string.remote_key_action_library)});
        actions.add(new String[]{RemoteKeys.INPUTS, activity.getString(R.string.remote_key_action_inputs)});
        actions.add(new String[]{"mute", activity.getString(R.string.remote_key_action_mute)});
        actions.add(new String[]{"screensaver", activity.getString(R.string.remote_key_action_screensaver)});
        actions.add(new String[]{"settings", activity.getString(R.string.remote_key_action_settings)});
        actions.add(new String[]{"wifi", activity.getString(R.string.remote_key_action_wifi)});
        actions.add(new String[]{"home", activity.getString(R.string.remote_key_action_home)});
        if (Build.VERSION.SDK_INT >= 28) {
            actions.add(new String[]{"sleep", activity.getString(R.string.remote_key_action_sleep)});
        }
        actions.add(new String[]{"ignore", activity.getString(R.string.remote_key_action_ignore)});

        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.remote_key_tab_actions);

        String[] titles = new String[actions.size()];
        int[] icons = new int[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            titles[i] = actions.get(i)[1];
            icons[i] = iconForAction(actions.get(i)[0]);
        }
        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, actions.size(), 58);
        list.setAdapter(simpleRowsAdapter(titles, new String[titles.length], icons));
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 480);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            saveShortcut(keyToken, hold, actions.get(pos)[0]);
            dialog.dismiss();
        });
        list.requestFocus();
    }

    private void showEditShortcutDialog(int index, RemoteKeyCustom.Shortcut item) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle))
                .setText(RemoteKeyCustom.prettyKeyName(activity, item.keyToken));

        boolean windowShortcut = RemoteKeyCustom.isWindowToken(item.keyToken);
        String[] titles = windowShortcut
                ? new String[]{
                        activity.getString(R.string.remote_key_edit_change_target),
                        activity.getString(R.string.remote_key_edit_delete)
                }
                : new String[]{
                        activity.getString(R.string.remote_key_edit_change_target),
                        activity.getString(R.string.remote_key_edit_switch_mode),
                        activity.getString(R.string.remote_key_edit_delete)
                };
        String[] subs = windowShortcut
                ? new String[]{describeTarget(item.target), ""}
                : new String[]{
                        describeTarget(item.target),
                        activity.getString(item.hold ? R.string.remote_key_mode_hold : R.string.remote_key_mode_tap),
                        ""
                };
        int[] icons = windowShortcut
                ? new int[]{R.drawable.ic_edit, R.drawable.ic_reset}
                : new int[]{R.drawable.ic_edit, R.drawable.ic_swap_vert, R.drawable.ic_reset};
        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, titles.length, 62);
        list.setAdapter(simpleRowsAdapter(titles, subs, icons));
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 480);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            dialog.dismiss();
            if (pos == 0) {
                showTargetCategoryPicker(item.keyToken, item.hold);
            } else if (!windowShortcut && pos == 1) {
                RemoteKeyCustom.removeShortcutAt(choices, index);
                saveShortcut(item.keyToken, !item.hold, item.target);
            } else {
                RemoteKeyCustom.removeShortcutAt(choices, index);
                onChanged.run();
                refreshActive();
            }
        });
        list.requestFocus();
    }

    private void saveShortcut(String keyToken, boolean hold, String target) {
        RemoteKeyCustom.upsertShortcut(choices, hold, keyToken, target);
        onChanged.run();
        refreshActive();
    }

    private BaseAdapter simpleRowsAdapter(String[] titles, String[] subs, int[] icons) {
        return new BaseAdapter() {
            @Override public int getCount() { return titles.length; }
            @Override public Object getItem(int p) { return titles[p]; }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                row.findViewById(R.id.rowPreviewFrame).getLayoutParams().width =
                        InputCustomizeDialogs.dp(activity, 45);
                row.findViewById(R.id.rowPreview).setVisibility(View.GONE);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                actionIcon.setVisibility(View.VISIBLE);
                actionIcon.setImageResource(icons[p]);
                ((TextView) row.findViewById(R.id.rowTitle)).setText(titles[p]);
                TextView sub = row.findViewById(R.id.rowSubtitle);
                if (subs[p] == null || subs[p].isEmpty()) {
                    sub.setVisibility(View.GONE);
                } else {
                    sub.setVisibility(View.VISIBLE);
                    sub.setText(subs[p]);
                }
                row.findViewById(R.id.rowCheck).setVisibility(View.GONE);
                row.findViewById(R.id.rowChevron).setVisibility(View.VISIBLE);
                return row;
            }
        };
    }

    private void bindShortcutArt(RemoteKeyCustom.Shortcut item, ImageView preview, ImageView actionIcon) {
        String inputId = RemoteKeys.inputId(item.target);
        if (inputId != null) {
            List<TvInputTiles.TileSpec> inputs = inputsSupplier.get();
            if (inputs != null) {
                for (TvInputTiles.TileSpec spec : inputs) {
                    if (spec.inputId.equals(inputId)) {
                        Config cfg = configSupplier.get();
                        String label = AppAdapter.inputLabel(cfg, spec);
                        preview.setVisibility(View.VISIBLE);
                        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        preview.setImageDrawable(TvInputTiles.renderBanner(activity, spec, label,
                                cfg != null ? cfg.appBanners.get(spec.pkg) : null));
                        actionIcon.setVisibility(View.GONE);
                        return;
                    }
                }
            }
        }
        List<AppCustom.AppInfoItem> apps = appsSupplier.get();
        if (apps != null) {
            Config cfg = configSupplier.get();
            PackageManager pm = activity.getPackageManager();
            for (AppCustom.AppInfoItem app : apps) {
                if (app.pkg.equals(item.target)) {
                    preview.setVisibility(View.VISIBLE);
                    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    String customIcon = cfg != null ? cfg.appIcons.get(app.pkg) : null;
                    android.graphics.drawable.Drawable d = customIcon != null ? AppAdapter.customIcon(activity, customIcon) : null;
                    preview.setImageDrawable(d != null ? d : app.resolveInfo.loadIcon(pm));
                    actionIcon.setVisibility(View.GONE);
                    return;
                }
            }
        }
        preview.setVisibility(View.GONE);
        actionIcon.setVisibility(View.VISIBLE);
        actionIcon.setImageResource(iconForAction(item.target));
    }

    private String describeTarget(String target) {
        if (target == null) return "";
        switch (target) {
            case "library": return activity.getString(R.string.remote_key_action_library);
            case RemoteKeys.INPUTS: return activity.getString(R.string.remote_key_action_inputs);
            case "mute": return activity.getString(R.string.remote_key_action_mute);
            case "screensaver": return activity.getString(R.string.remote_key_action_screensaver);
            case "settings": return activity.getString(R.string.remote_key_action_settings);
            case "wifi": return activity.getString(R.string.remote_key_action_wifi);
            case "home": return activity.getString(R.string.remote_key_action_home);
            case "sleep": return activity.getString(R.string.remote_key_action_sleep);
            case "ignore": return activity.getString(R.string.remote_key_action_ignore);
        }
        String inputId = RemoteKeys.inputId(target);
        Config cfg = configSupplier.get();
        if (inputId != null) {
            if (cfg != null && cfg.appLabels.containsKey(target)) return cfg.appLabels.get(target);
            List<TvInputTiles.TileSpec> inputs = inputsSupplier.get();
            if (inputs != null) {
                for (TvInputTiles.TileSpec spec : inputs) {
                    if (spec.inputId.equals(inputId)) return spec.label;
                }
            }
            return inputId;
        }
        if (cfg != null && cfg.appLabels.containsKey(target)) return cfg.appLabels.get(target);
        List<AppCustom.AppInfoItem> apps = appsSupplier.get();
        if (apps != null) {
            for (AppCustom.AppInfoItem app : apps) {
                if (app.pkg.equals(target)) return app.originalLabel;
            }
        }
        return target;
    }

    private static int iconForAction(String target) {
        if ("library".equals(target)) return R.drawable.ic_apps;
        if (RemoteKeys.INPUTS.equals(target)) return R.drawable.ic_input;
        if ("screensaver".equals(target)) return R.drawable.ic_clock;
        if ("wifi".equals(target)) return R.drawable.ic_wifi;
        if ("ignore".equals(target)) return R.drawable.ic_lock;
        if ("settings".equals(target)) return R.drawable.ic_settings;
        return R.drawable.ic_remote;
    }

    private void openAccessibilitySettings() {
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
