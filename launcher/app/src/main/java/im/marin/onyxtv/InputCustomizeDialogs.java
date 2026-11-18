package im.marin.onyxtv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Remote-friendly Onyx dialogs for assigning custom display names and icons/banners
 * to each discovered hardware TV input.
 */
final class InputCustomizeDialogs {
    private final Activity activity;
    private final SharedPreferences choices;
    private final Supplier<Config> configSupplier;
    private final Runnable onChanged;
    private final Consumer<String> onPickImage;
    private final List<Runnable> activeRefreshes = new ArrayList<>();

    InputCustomizeDialogs(Activity activity, SharedPreferences choices, Supplier<Config> configSupplier,
                          Runnable onChanged, Consumer<String> onPickImage) {
        this.activity = activity;
        this.choices = choices;
        this.configSupplier = configSupplier;
        this.onChanged = onChanged;
        this.onPickImage = onPickImage;
    }

    void refreshActive() {
        for (int i = activeRefreshes.size() - 1; i >= 0; i--) {
            activeRefreshes.get(i).run();
        }
    }

    void showInputList(List<TvInputTiles.TileSpec> inputs) {
        if (inputs == null || inputs.isEmpty()) return;
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        applyRtl(activity, root);
        TextView title = root.findViewById(R.id.inputCustomizeTitle);
        title.setText(R.string.input_customize_label);

        ListView list = root.findViewById(R.id.inputCustomizeList);
        sizeList(activity, list, inputs.size(), 58);

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return inputs.size();
            }

            @Override
            public TvInputTiles.TileSpec getItem(int position) {
                return inputs.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                TvInputTiles.TileSpec spec = inputs.get(position);
                Config cfg = configSupplier.get();
                String customLabel = customLabel(cfg, spec);
                String bannerRef = effectiveBannerRef(cfg, spec);
                String activeName = activeDisplayName(spec, customLabel);

                ImageView preview = row.findViewById(R.id.rowPreview);
                preview.setClipToOutline(true);
                preview.setImageDrawable(resolveInputBanner(activity, spec, activeName, bannerRef));
                preview.setVisibility(View.VISIBLE);
                row.findViewById(R.id.rowActionIcon).setVisibility(View.GONE);
                row.findViewById(R.id.rowCheck).setVisibility(View.GONE);
                row.findViewById(R.id.rowChevron).setVisibility(View.VISIBLE);

                boolean hidden = InputCustom.isHidden(cfg, spec.inputId);
                row.setAlpha(hidden ? 0.55f : 1f);
                ((TextView) row.findViewById(R.id.rowTitle)).setText(activeName);
                ((TextView) row.findViewById(R.id.rowSubtitle)).setText(
                        describeCustomization(spec, customLabel, bannerRef, hidden));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> showInputEditor(inputs.get(pos)));

        Dialog dialog = showStyledDialog(activity, root, 520);
        Runnable refresh = adapter::notifyDataSetChanged;
        activeRefreshes.add(refresh);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(refresh));
        list.requestFocus();
    }

    private void showInputEditor(TvInputTiles.TileSpec spec) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        applyRtl(activity, root);
        TextView title = root.findViewById(R.id.inputCustomizeTitle);
        View header = root.findViewById(R.id.inputCustomizeHeader);
        ImageView preview = root.findViewById(R.id.inputCustomizePreview);
        TextView headerName = root.findViewById(R.id.inputCustomizeHeaderName);
        TextView headerPort = root.findViewById(R.id.inputCustomizeHeaderPort);
        ListView list = root.findViewById(R.id.inputCustomizeList);

        header.setVisibility(View.VISIBLE);
        preview.setClipToOutline(true);
        sizeList(activity, list, 4, 62);

        Runnable[] bindHeader = new Runnable[1];
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return 4;
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
                Config cfg = configSupplier.get();
                String customLabel = customLabel(cfg, spec);
                String bannerRef = effectiveBannerRef(cfg, spec);
                boolean hidden = InputCustom.isHidden(cfg, spec.inputId);
                boolean hasAnyCustom = hasDirectOverride(spec) || hidden;

                ImageView rowPreview = row.findViewById(R.id.rowPreview);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                TextView rowTitle = row.findViewById(R.id.rowTitle);
                TextView rowSub = row.findViewById(R.id.rowSubtitle);
                TextView rowCheck = row.findViewById(R.id.rowCheck);
                View chevron = row.findViewById(R.id.rowChevron);
                rowCheck.setVisibility(View.GONE);

                if (position == 0) {
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(R.drawable.ic_edit);
                    rowTitle.setText(R.string.input_custom_name_label);
                    rowSub.setText(customLabel != null ? customLabel : spec.label);
                    chevron.setVisibility(View.VISIBLE);
                    row.setAlpha(1f);
                } else if (position == 1) {
                    rowPreview.setVisibility(View.VISIBLE);
                    rowPreview.setClipToOutline(true);
                    rowPreview.setImageDrawable(resolveInputBanner(
                            activity, spec, activeDisplayName(spec, customLabel), bannerRef));
                    actionIcon.setVisibility(View.GONE);
                    rowTitle.setText(R.string.input_custom_icon_label);
                    rowSub.setText(describeBanner(activity, bannerRef));
                    chevron.setVisibility(View.VISIBLE);
                    row.setAlpha(1f);
                } else if (position == 2) {
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(hidden ? R.drawable.ic_lock_open : R.drawable.ic_lock);
                    rowTitle.setText(hidden ? R.string.input_show_action : R.string.input_hide_action);
                    rowSub.setText(hidden ? activity.getString(R.string.input_hidden_state)
                            : spec.label);
                    rowCheck.setVisibility(hidden ? View.VISIBLE : View.GONE);
                    chevron.setVisibility(View.GONE);
                    row.setAlpha(1f);
                } else {
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(R.drawable.ic_reset);
                    rowTitle.setText(R.string.input_reset_one);
                    rowSub.setText(hasAnyCustom ? spec.label : activity.getString(R.string.input_default));
                    chevron.setVisibility(View.GONE);
                    row.setAlpha(hasAnyCustom ? 1f : 0.45f);
                }
                return row;
            }
        };

        bindHeader[0] = () -> {
            Config cfg = configSupplier.get();
            String customLabel = customLabel(cfg, spec);
            String activeName = activeDisplayName(spec, customLabel);
            String bannerRef = effectiveBannerRef(cfg, spec);
            boolean hidden = InputCustom.isHidden(cfg, spec.inputId);
            title.setText(activeName);
            headerName.setText(activeName);
            headerPort.setText(hidden
                    ? spec.label + " · " + activity.getString(R.string.input_hidden_state)
                    : spec.label);
            preview.setImageDrawable(resolveInputBanner(activity, spec, activeName, bannerRef));
            adapter.notifyDataSetChanged();
        };

        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos == 0) {
                showRenameDialog(spec);
            } else if (pos == 1) {
                showIconPicker(spec);
            } else if (pos == 2) {
                boolean hidden = InputCustom.isHidden(configSupplier.get(), spec.inputId);
                InputCustom.setHidden(choices, spec.inputId, !hidden);
                onChanged.run();
                refreshActive();
            } else {
                boolean hidden = InputCustom.isHidden(configSupplier.get(), spec.inputId);
                if (!hasDirectOverride(spec) && !hidden) return;
                InputCustom.setEntry(choices, Config.INPUT_LABELS_KEY, spec.inputId, null);
                InputCustom.setEntry(choices, Config.INPUT_BANNERS_KEY, spec.inputId, null);
                InputCustom.setHidden(choices, spec.inputId, false);
                onChanged.run();
                refreshActive();
            }
        });

        bindHeader[0].run();
        Dialog dialog = showStyledDialog(activity, root, 500);
        activeRefreshes.add(bindHeader[0]);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(bindHeader[0]));
        list.requestFocus();
    }

    private void showRenameDialog(TvInputTiles.TileSpec spec) {
        String existing = InputCustom.readMap(choices, Config.INPUT_LABELS_KEY).get(spec.inputId);
        showRenameDialog(activity, spec.label, existing, InputCustom.QUICK_NAME_PRESETS, next -> {
            InputCustom.setEntry(choices, Config.INPUT_LABELS_KEY, spec.inputId, next);
            onChanged.run();
            refreshActive();
        });
    }

    static void showRenameDialog(Activity activity, String originalLabel, String existingLabel,
                                 String[] quickPresets, Consumer<String> onCommit) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_rename, null);
        applyRtl(activity, root);
        TextView original = root.findViewById(R.id.renameOriginalPort);
        original.setText(originalLabel);

        EditText edit = root.findViewById(R.id.renameEdit);
        if (existingLabel != null && !existingLabel.isEmpty()) {
            edit.setText(existingLabel);
            edit.setSelection(existingLabel.length());
        }

        TextView save = root.findViewById(R.id.renameSave);
        TextView cancel = root.findViewById(R.id.renameCancel);
        TextView restore = root.findViewById(R.id.renameRestore);

        LinearLayout row1 = root.findViewById(R.id.renameChipsRow1);
        LinearLayout row2 = root.findViewById(R.id.renameChipsRow2);
        if (quickPresets == null || quickPresets.length == 0) {
            row1.setVisibility(View.GONE);
            row2.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < quickPresets.length; i++) {
                String chipText = quickPresets[i];
                TextView chip = new TextView(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i % 4 > 0) lp.setMarginStart(dp(activity, 6));
                chip.setLayoutParams(lp);
                chip.setBackgroundResource(R.drawable.settings_row_bg);
                chip.setFocusable(true);
                chip.setClickable(true);
                chip.setGravity(Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setEllipsize(TextUtils.TruncateAt.END);
                chip.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
                chip.setText(chipText);
                chip.setTextColor(activity.getColorStateList(R.color.settings_choice_text));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                chip.setOnClickListener(v -> {
                    edit.setText(chipText);
                    edit.setSelection(chipText.length());
                    save.requestFocus();
                });
                (i < 4 ? row1 : row2).addView(chip);
            }
        }

        Dialog dialog = showStyledDialog(activity, root, 520);
        Runnable commitName = () -> {
            String next = edit.getText() == null ? "" : edit.getText().toString().trim();
            onCommit.accept(next.isEmpty() ? null : next);
            dialog.dismiss();
        };

        save.setOnClickListener(v -> commitName.run());
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP)) {
                commitName.run();
                return true;
            }
            return false;
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        restore.setOnClickListener(v -> {
            onCommit.accept(null);
            dialog.dismiss();
        });
        if (row1.getVisibility() == View.VISIBLE && row1.getChildCount() > 0) {
            row1.getChildAt(0).requestFocus();
        } else {
            edit.requestFocus();
        }
    }

    private static final class IconOption {
        final String ref;
        final String title;
        final String subtitle;
        final boolean pickAction;

        IconOption(String ref, String title, String subtitle, boolean pickAction) {
            this.ref = ref;
            this.title = title;
            this.subtitle = subtitle;
            this.pickAction = pickAction;
        }
    }

    private void showIconPicker(TvInputTiles.TileSpec spec) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        applyRtl(activity, root);
        TextView title = root.findViewById(R.id.inputCustomizeTitle);
        title.setText(R.string.input_custom_icon_label);

        List<IconOption> options = new ArrayList<>();
        options.add(new IconOption(null, activity.getString(R.string.input_pick_image), "", true));
        options.add(new IconOption("", activity.getString(R.string.input_default), spec.label, false));
        for (InputCustom.Preset preset : InputCustom.presets()) {
            options.add(new IconOption(preset.id, InputCustom.presetLabel(activity, preset.id), "", false));
        }
        String importedLabel = activity.getString(R.string.input_custom_image);
        for (File file : InputCustom.listBannerFiles(activity)) {
            options.add(new IconOption(file.getName(),
                    InputCustom.formatBannerFileName(activity, file.getName(), importedLabel), "", false));
        }

        ListView list = root.findViewById(R.id.inputCustomizeList);
        sizeList(activity, list, options.size(), 62);

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return options.size();
            }

            @Override
            public IconOption getItem(int position) {
                return options.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                IconOption option = options.get(position);
                Config cfg = configSupplier.get();
                String activeName = activeDisplayName(spec, customLabel(cfg, spec));
                String currentDirect = InputCustom.readMap(choices, Config.INPUT_BANNERS_KEY).get(spec.inputId);

                ImageView rowPreview = row.findViewById(R.id.rowPreview);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                TextView check = row.findViewById(R.id.rowCheck);
                View chevron = row.findViewById(R.id.rowChevron);
                TextView subtitleView = row.findViewById(R.id.rowSubtitle);

                ((TextView) row.findViewById(R.id.rowTitle)).setText(option.title);
                if (option.subtitle == null || option.subtitle.isEmpty()) {
                    subtitleView.setVisibility(View.GONE);
                } else {
                    subtitleView.setVisibility(View.VISIBLE);
                    subtitleView.setText(option.subtitle);
                }

                if (option.pickAction) {
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(R.drawable.ic_picture);
                    check.setVisibility(View.GONE);
                    chevron.setVisibility(View.VISIBLE);
                } else {
                    rowPreview.setVisibility(View.VISIBLE);
                    rowPreview.setClipToOutline(true);
                    rowPreview.setImageDrawable(option.ref.isEmpty()
                            ? TvInputTiles.renderBanner(activity, spec, activeName, null)
                            : resolveInputBanner(activity, spec, activeName, option.ref));
                    actionIcon.setVisibility(View.GONE);
                    boolean selected = option.ref.isEmpty()
                            ? (currentDirect == null || currentDirect.isEmpty())
                            : option.ref.equals(currentDirect);
                    check.setVisibility(selected ? View.VISIBLE : View.GONE);
                    chevron.setVisibility(View.GONE);
                }
                return row;
            }
        };

        list.setAdapter(adapter);
        Dialog dialog = showStyledDialog(activity, root, 520);
        Runnable refresh = adapter::notifyDataSetChanged;
        activeRefreshes.add(refresh);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(refresh));

        list.setOnItemClickListener((parent, view, pos, id) -> {
            IconOption option = options.get(pos);
            if (option.pickAction) {
                dialog.dismiss();
                onPickImage.accept(spec.inputId);
                return;
            }
            InputCustom.setEntry(choices, Config.INPUT_BANNERS_KEY, spec.inputId,
                    option.ref.isEmpty() ? null : option.ref);
            onChanged.run();
            refreshActive();
            dialog.dismiss();
        });
        list.requestFocus();
    }

    private boolean hasDirectOverride(TvInputTiles.TileSpec spec) {
        Map<String, String> labels = InputCustom.readMap(choices, Config.INPUT_LABELS_KEY);
        Map<String, String> banners = InputCustom.readMap(choices, Config.INPUT_BANNERS_KEY);
        return labels.containsKey(spec.inputId) || banners.containsKey(spec.inputId);
    }

    private static String customLabel(Config cfg, TvInputTiles.TileSpec spec) {
        if (cfg == null) return null;
        String direct = cfg.appLabels.get(spec.pkg);
        return direct != null && !direct.isEmpty() ? direct : null;
    }

    private static String effectiveBannerRef(Config cfg, TvInputTiles.TileSpec spec) {
        if (cfg == null) return null;
        return cfg.appBanners.get(spec.pkg);
    }

    private static String activeDisplayName(TvInputTiles.TileSpec spec, String customLabel) {
        return customLabel != null && !customLabel.isEmpty() ? customLabel : spec.label;
    }

    private String describeCustomization(TvInputTiles.TileSpec spec, String customLabel,
                                         String bannerRef, boolean hidden) {
        if (hidden) {
            return activity.getString(R.string.input_hidden_state) + " · " + spec.label;
        }
        boolean renamed = customLabel != null && !customLabel.isEmpty() && !customLabel.equals(spec.label);
        if (renamed && bannerRef != null && !bannerRef.isEmpty()) {
            return spec.label + " · " + describeBanner(activity, bannerRef);
        }
        if (renamed) return spec.label;
        if (bannerRef != null && !bannerRef.isEmpty()) return describeBanner(activity, bannerRef);
        return activity.getString(R.string.input_default);
    }

    private static String describeBanner(Context ctx, String bannerRef) {
        if (bannerRef == null || bannerRef.isEmpty()) return ctx.getString(R.string.input_default);
        if (InputCustom.isPreset(bannerRef)) return InputCustom.presetLabel(ctx, bannerRef);
        return InputCustom.formatBannerFileName(ctx, bannerRef, ctx.getString(R.string.input_custom_image));
    }

    private static Drawable resolveInputBanner(Context ctx, TvInputTiles.TileSpec spec,
                                               String activeName, String bannerRef) {
        if (bannerRef != null && !bannerRef.isEmpty() && !InputCustom.isPreset(bannerRef)) {
            Drawable external = CustomArt.loadDrawable(ctx, CustomArt.BANNERS_DIR, bannerRef, 320, 180);
            if (external != null) return external;
            if (bannerRef.startsWith("banner_")) {
                int id = ctx.getResources().getIdentifier(bannerRef, "drawable", ctx.getPackageName());
                if (id != 0) return ctx.getDrawable(id);
            }
        }
        return TvInputTiles.renderBanner(ctx, spec, activeName, bannerRef);
    }

    static Dialog showStyledDialog(Activity activity, View content, int maxWidthDp) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.show();
        if (dialog.getWindow() != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            int width = Math.min(dp(activity, maxWidthDp), Math.round(metrics.widthPixels * 0.68f));
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    static void sizeList(Activity activity, ListView list, int itemCount, int rowHeightDp) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int desired = dp(activity, Math.max(110, itemCount * rowHeightDp + 16));
        ViewGroup.LayoutParams lp = list.getLayoutParams();
        lp.height = Math.min(dp(activity, 340), Math.min(desired, Math.round(metrics.heightPixels * 0.54f)));
        list.setLayoutParams(lp);
    }

    static void applyRtl(Activity activity, View view) {
        if (activity.getResources().getBoolean(R.bool.rtl_menu_bar)) {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
    }

    static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
