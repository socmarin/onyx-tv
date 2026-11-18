package im.marin.onyxtv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Remote-friendly Onyx dialogs for customizing Android apps:
 * custom name, custom 1:1 icon, custom 16:9 banner, and hiding from Home & App library.
 */
final class AppCustomizeDialogs {
    private final Activity activity;
    private final SharedPreferences choices;
    private final Supplier<Config> configSupplier;
    private final Runnable onChanged;
    private final Consumer<String> onPickIconImage;
    private final Consumer<String> onPickBannerImage;
    private final List<Runnable> activeRefreshes = new ArrayList<>();

    AppCustomizeDialogs(Activity activity, SharedPreferences choices, Supplier<Config> configSupplier,
                        Runnable onChanged, Consumer<String> onPickIconImage,
                        Consumer<String> onPickBannerImage) {
        this.activity = activity;
        this.choices = choices;
        this.configSupplier = configSupplier;
        this.onChanged = onChanged;
        this.onPickIconImage = onPickIconImage;
        this.onPickBannerImage = onPickBannerImage;
    }

    void refreshActive() {
        for (int i = activeRefreshes.size() - 1; i >= 0; i--) {
            activeRefreshes.get(i).run();
        }
    }

    void showAppList(List<AppCustom.AppInfoItem> apps) {
        if (apps == null || apps.isEmpty()) return;
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle)).setText(R.string.app_customize_label);

        List<AppCustom.AppInfoItem> sorted = new ArrayList<>(apps);
        Config initCfg = configSupplier.get();
        sorted.sort((a, b) -> {
            boolean ha = AppCustom.isHidden(initCfg, a.pkg);
            boolean hb = AppCustom.isHidden(initCfg, b.pkg);
            if (ha != hb) return ha ? 1 : -1;
            return activeName(initCfg, a).compareToIgnoreCase(activeName(initCfg, b));
        });

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, sorted.size(), 58);

        PackageManager pm = activity.getPackageManager();
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return sorted.size();
            }

            @Override
            public AppCustom.AppInfoItem getItem(int position) {
                return sorted.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_input_custom_row, parent, false);
                AppCustom.AppInfoItem item = sorted.get(position);
                Config cfg = configSupplier.get();
                boolean hidden = AppCustom.isHidden(cfg, item.pkg);

                setSquarePreviewFrame(row.findViewById(R.id.rowPreviewFrame), true);
                ImageView preview = row.findViewById(R.id.rowPreview);
                preview.setClipToOutline(true);
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setImageDrawable(resolveAppIcon(activity, pm, cfg, item));
                preview.setVisibility(View.VISIBLE);
                row.findViewById(R.id.rowActionIcon).setVisibility(View.GONE);
                row.findViewById(R.id.rowCheck).setVisibility(View.GONE);
                row.findViewById(R.id.rowChevron).setVisibility(View.VISIBLE);

                row.setAlpha(hidden ? 0.55f : 1f);
                ((TextView) row.findViewById(R.id.rowTitle)).setText(activeName(cfg, item));
                ((TextView) row.findViewById(R.id.rowSubtitle)).setText(describeApp(cfg, item, hidden));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> showAppEditor(sorted.get(pos)));

        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 520);
        Runnable refresh = adapter::notifyDataSetChanged;
        activeRefreshes.add(refresh);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(refresh));
        list.requestFocus();
    }

    private void showAppEditor(AppCustom.AppInfoItem item) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        TextView title = root.findViewById(R.id.inputCustomizeTitle);
        View header = root.findViewById(R.id.inputCustomizeHeader);
        ImageView preview = root.findViewById(R.id.inputCustomizePreview);
        TextView headerName = root.findViewById(R.id.inputCustomizeHeaderName);
        TextView headerSub = root.findViewById(R.id.inputCustomizeHeaderPort);
        ListView list = root.findViewById(R.id.inputCustomizeList);

        header.setVisibility(View.VISIBLE);
        preview.setClipToOutline(true);
        InputCustomizeDialogs.sizeList(activity, list, 5, 62);

        PackageManager pm = activity.getPackageManager();
        Runnable[] bindHeader = new Runnable[1];
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return 5;
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
                boolean hidden = AppCustom.isHidden(cfg, item.pkg);
                boolean hasCustom = hasDirectOverride(item.pkg) || hidden;

                View frame = row.findViewById(R.id.rowPreviewFrame);
                ImageView rowPreview = row.findViewById(R.id.rowPreview);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                TextView rowTitle = row.findViewById(R.id.rowTitle);
                TextView rowSub = row.findViewById(R.id.rowSubtitle);
                TextView rowCheck = row.findViewById(R.id.rowCheck);
                View chevron = row.findViewById(R.id.rowChevron);
                rowCheck.setVisibility(View.GONE);

                if (position == 0) {
                    setAlignedPreviewSlot(frame, rowPreview, false);
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(R.drawable.ic_edit);
                    rowTitle.setText(R.string.input_custom_name_label);
                    String customLabel = CustomArt.readMap(choices, Config.APP_LABELS_KEY).get(item.pkg);
                    rowSub.setText(customLabel != null ? customLabel : activity.getString(R.string.input_default));
                    chevron.setVisibility(View.VISIBLE);
                    row.setAlpha(1f);
                } else if (position == 1) {
                    setAlignedPreviewSlot(frame, rowPreview, true);
                    rowPreview.setVisibility(View.VISIBLE);
                    rowPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    rowPreview.setImageDrawable(resolveAppIcon(activity, pm, cfg, item));
                    actionIcon.setVisibility(View.GONE);
                    rowTitle.setText(R.string.app_custom_icon_1x1_label);
                    String iconRef = CustomArt.readMap(choices, Config.APP_ICONS_KEY).get(item.pkg);
                    rowSub.setText(iconRef != null
                            ? AppCustom.formatArtName(iconRef, activity.getString(R.string.input_custom_image))
                            : activity.getString(R.string.input_default));
                    chevron.setVisibility(View.VISIBLE);
                    row.setAlpha(1f);
                } else if (position == 2) {
                    setAlignedPreviewSlot(frame, rowPreview, false);
                    rowPreview.setVisibility(View.VISIBLE);
                    Drawable banner = resolveAppBanner(activity, pm, cfg, item);
                    if (banner != null) {
                        rowPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        rowPreview.setImageDrawable(banner);
                    } else {
                        rowPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        rowPreview.setImageDrawable(resolveAppIcon(activity, pm, cfg, item));
                    }
                    actionIcon.setVisibility(View.GONE);
                    rowTitle.setText(R.string.app_custom_banner_16x9_label);
                    String bannerRef = CustomArt.readMap(choices, Config.APP_BANNERS_KEY).get(item.pkg);
                    rowSub.setText(bannerRef != null
                            ? AppCustom.formatArtName(bannerRef, activity.getString(R.string.input_custom_image))
                            : activity.getString(R.string.input_default));
                    chevron.setVisibility(View.VISIBLE);
                    row.setAlpha(1f);
                } else if (position == 3) {
                    setAlignedPreviewSlot(frame, rowPreview, false);
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(hidden ? R.drawable.ic_lock_open : R.drawable.ic_lock);
                    rowTitle.setText(hidden ? R.string.app_show_action : R.string.app_hide_action);
                    rowSub.setText(hidden ? activity.getString(R.string.app_hidden_state) : item.originalLabel);
                    rowCheck.setVisibility(hidden ? View.VISIBLE : View.GONE);
                    chevron.setVisibility(View.GONE);
                    row.setAlpha(1f);
                } else {
                    setAlignedPreviewSlot(frame, rowPreview, false);
                    rowPreview.setVisibility(View.GONE);
                    actionIcon.setVisibility(View.VISIBLE);
                    actionIcon.setImageResource(R.drawable.ic_reset);
                    rowTitle.setText(R.string.app_reset_one);
                    rowSub.setText(hasCustom ? item.originalLabel : activity.getString(R.string.input_default));
                    chevron.setVisibility(View.GONE);
                    row.setAlpha(hasCustom ? 1f : 0.45f);
                }
                return row;
            }
        };

        bindHeader[0] = () -> {
            Config cfg = configSupplier.get();
            String name = activeName(cfg, item);
            boolean hidden = AppCustom.isHidden(cfg, item.pkg);
            title.setText(name);
            headerName.setText(name);
            headerSub.setText(hidden
                    ? item.originalLabel + " · " + activity.getString(R.string.app_hidden_state)
                    : item.originalLabel);
            Drawable banner = resolveAppBanner(activity, pm, cfg, item);
            if (banner != null) {
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                preview.setImageDrawable(banner);
            } else {
                preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                preview.setImageDrawable(resolveAppIcon(activity, pm, cfg, item));
            }
            adapter.notifyDataSetChanged();
        };

        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos == 0) {
                String existing = CustomArt.readMap(choices, Config.APP_LABELS_KEY).get(item.pkg);
                InputCustomizeDialogs.showRenameDialog(activity, item.originalLabel, existing, null, next -> {
                    CustomArt.setMapEntry(choices, Config.APP_LABELS_KEY, item.pkg, next);
                    onChanged.run();
                    refreshActive();
                });
            } else if (pos == 1) {
                showArtPicker(item, true);
            } else if (pos == 2) {
                showArtPicker(item, false);
            } else if (pos == 3) {
                boolean hidden = AppCustom.isHidden(configSupplier.get(), item.pkg);
                AppCustom.setHidden(choices, item.pkg, !hidden);
                onChanged.run();
                refreshActive();
            } else {
                if (!hasDirectOverride(item.pkg) && !AppCustom.isHidden(configSupplier.get(), item.pkg)) return;
                AppCustom.resetApp(choices, item.pkg);
                onChanged.run();
                refreshActive();
            }
        });

        bindHeader[0].run();
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 500);
        activeRefreshes.add(bindHeader[0]);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(bindHeader[0]));
        list.requestFocus();
    }

    private static final class ArtOption {
        final String ref;
        final String title;
        final String subtitle;
        final boolean pickAction;

        ArtOption(String ref, String title, String subtitle, boolean pickAction) {
            this.ref = ref;
            this.title = title;
            this.subtitle = subtitle;
            this.pickAction = pickAction;
        }
    }

    private void showArtPicker(AppCustom.AppInfoItem item, boolean isSquareIcon) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_input_customize, null);
        InputCustomizeDialogs.applyRtl(activity, root);
        ((TextView) root.findViewById(R.id.inputCustomizeTitle))
                .setText(isSquareIcon ? R.string.app_custom_icon_1x1_label : R.string.app_custom_banner_16x9_label);

        String prefKey = isSquareIcon ? Config.APP_ICONS_KEY : Config.APP_BANNERS_KEY;
        String folder = isSquareIcon ? CustomArt.APP_ICONS_DIR : CustomArt.APP_BANNERS_DIR;

        List<ArtOption> options = new ArrayList<>();
        options.add(new ArtOption(null, activity.getString(R.string.input_pick_image), "", true));
        options.add(new ArtOption("", activity.getString(R.string.input_default), item.originalLabel, false));
        String importedLabel = activity.getString(R.string.input_custom_image);
        for (File file : CustomArt.listImages(activity, folder)) {
            options.add(new ArtOption(file.getName(),
                    AppCustom.formatArtName(file.getName(), importedLabel), "", false));
        }

        ListView list = root.findViewById(R.id.inputCustomizeList);
        InputCustomizeDialogs.sizeList(activity, list, options.size(), 62);
        PackageManager pm = activity.getPackageManager();

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return options.size();
            }

            @Override
            public ArtOption getItem(int position) {
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
                ArtOption option = options.get(position);
                String currentDirect = CustomArt.readMap(choices, prefKey).get(item.pkg);

                ImageView rowPreview = row.findViewById(R.id.rowPreview);
                setAlignedPreviewSlot(row.findViewById(R.id.rowPreviewFrame), rowPreview,
                        isSquareIcon && !option.pickAction);
                ImageView actionIcon = row.findViewById(R.id.rowActionIcon);
                TextView check = row.findViewById(R.id.rowCheck);
                View chevron = row.findViewById(R.id.rowChevron);
                TextView subtitleView = row.findViewById(R.id.rowSubtitle);

                ((TextView) row.findViewById(R.id.rowTitle)).setText(option.title);
                if (option.subtitle.isEmpty()) {
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
                    if (isSquareIcon) {
                        rowPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        Drawable d = option.ref.isEmpty()
                                ? item.resolveInfo.loadIcon(pm)
                                : AppAdapter.customIcon(activity, option.ref);
                        rowPreview.setImageDrawable(d != null ? d : item.resolveInfo.loadIcon(pm));
                    } else {
                        Drawable d = option.ref.isEmpty()
                                ? item.resolveInfo.activityInfo.loadBanner(pm)
                                : AppAdapter.customAppBanner(activity, option.ref);
                        if (d != null) {
                            rowPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            rowPreview.setImageDrawable(d);
                        } else {
                            rowPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            rowPreview.setImageDrawable(item.resolveInfo.loadIcon(pm));
                        }
                    }
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
        Dialog dialog = InputCustomizeDialogs.showStyledDialog(activity, root, 520);
        Runnable refresh = adapter::notifyDataSetChanged;
        activeRefreshes.add(refresh);
        dialog.setOnDismissListener(d -> activeRefreshes.remove(refresh));

        list.setOnItemClickListener((parent, view, pos, id) -> {
            ArtOption option = options.get(pos);
            if (option.pickAction) {
                dialog.dismiss();
                if (isSquareIcon) onPickIconImage.accept(item.pkg);
                else onPickBannerImage.accept(item.pkg);
                return;
            }
            CustomArt.setMapEntry(choices, prefKey, item.pkg, option.ref.isEmpty() ? null : option.ref);
            onChanged.run();
            refreshActive();
            dialog.dismiss();
        });
        list.requestFocus();
    }

    /** Keeps the 80x45 column width consistent across rows while centering 1:1 artwork inside a 45x45 box. */
    private void setAlignedPreviewSlot(View frame, ImageView rowPreview, boolean square) {
        if (frame == null) return;
        ViewGroup.LayoutParams flp = frame.getLayoutParams();
        int fullW = InputCustomizeDialogs.dp(activity, 80);
        if (flp.width != fullW) {
            flp.width = fullW;
            frame.setLayoutParams(flp);
        }
        frame.setBackgroundResource(square ? 0 : R.drawable.input_tile_thumb_bg);
        if (rowPreview == null) return;
        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) rowPreview.getLayoutParams();
        if (square) {
            int side = InputCustomizeDialogs.dp(activity, 45);
            plp.width = side;
            plp.height = side;
            plp.gravity = Gravity.CENTER;
            rowPreview.setBackgroundResource(R.drawable.input_tile_thumb_bg);
        } else {
            plp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            plp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            plp.gravity = Gravity.NO_GRAVITY;
            rowPreview.setBackgroundResource(0);
        }
        rowPreview.setLayoutParams(plp);
    }

    private void setSquarePreviewFrame(View frame, boolean square) {
        if (frame == null) return;
        ViewGroup.LayoutParams lp = frame.getLayoutParams();
        int targetW = InputCustomizeDialogs.dp(activity, square ? 45 : 80);
        if (lp.width != targetW) {
            lp.width = targetW;
            frame.setLayoutParams(lp);
        }
    }

    private boolean hasDirectOverride(String pkg) {
        return CustomArt.readMap(choices, Config.APP_LABELS_KEY).containsKey(pkg)
                || CustomArt.readMap(choices, Config.APP_ICONS_KEY).containsKey(pkg)
                || CustomArt.readMap(choices, Config.APP_BANNERS_KEY).containsKey(pkg);
    }

    private static String activeName(Config cfg, AppCustom.AppInfoItem item) {
        if (cfg != null && cfg.appLabels.containsKey(item.pkg)) {
            String custom = cfg.appLabels.get(item.pkg);
            if (custom != null && !custom.isEmpty()) return custom;
        }
        return item.originalLabel;
    }

    private String describeApp(Config cfg, AppCustom.AppInfoItem item, boolean hidden) {
        if (hidden) return activity.getString(R.string.app_hidden_state) + " · " + item.originalLabel;
        boolean renamed = !activeName(cfg, item).equals(item.originalLabel);
        boolean hasIcon = cfg != null && cfg.appIcons.containsKey(item.pkg);
        boolean hasBanner = cfg != null && cfg.appBanners.containsKey(item.pkg);
        List<String> parts = new ArrayList<>();
        if (renamed) parts.add(item.originalLabel);
        if (hasIcon) parts.add(activity.getString(R.string.app_custom_icon_1x1_label));
        if (hasBanner) parts.add(activity.getString(R.string.app_custom_banner_16x9_label));
        return parts.isEmpty() ? activity.getString(R.string.input_default) : String.join(" · ", parts);
    }

    private static Drawable resolveAppIcon(Context ctx, PackageManager pm, Config cfg, AppCustom.AppInfoItem item) {
        String customRef = cfg != null ? cfg.appIcons.get(item.pkg) : null;
        Drawable d = customRef != null ? AppAdapter.customIcon(ctx, customRef) : null;
        return d != null ? d : item.resolveInfo.loadIcon(pm);
    }

    private static Drawable resolveAppBanner(Context ctx, PackageManager pm, Config cfg, AppCustom.AppInfoItem item) {
        String customRef = cfg != null ? cfg.appBanners.get(item.pkg) : null;
        Drawable d = customRef != null ? AppAdapter.customAppBanner(ctx, customRef) : null;
        if (d != null) return d;
        Drawable stock = item.resolveInfo.activityInfo.loadBanner(pm);
        return stock != null && AppAdapter.isBannerShaped(stock.getIntrinsicWidth(), stock.getIntrinsicHeight())
                ? stock : null;
    }
}
