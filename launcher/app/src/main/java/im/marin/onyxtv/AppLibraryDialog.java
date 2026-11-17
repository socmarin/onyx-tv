package im.marin.onyxtv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/** On-demand installed-app picker. Uses the launcher's glass resources; no backdrop capture or blur. */
final class AppLibraryDialog {
    /**
     * The catalogue, the labels and the icons load here, off the UI thread: each label and icon opens the app's
     * resources, and on a TCL G09 that froze Home for 60-140 ms (2 to 4 janky frames) every time the library opened.
     */
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    /** Labels kept between openings (LOADER thread only), until the app is updated. */
    private static final Map<String, Known> KNOWN = new HashMap<>();
    private static final int COLUMNS = 6;

    private static final class Known {
        final long updated;
        final String label;

        Known(long updated, String label) {
            this.updated = updated;
            this.label = label;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    /** Icons loaded for this opening, dropped with it. */
    private final Map<String, Drawable> icons = new HashMap<>();
    private final Set<String> iconsLoading = new HashSet<>();
    private final Runnable dismissed;
    private boolean closed;
    /** Only the latest load fills the catalogue, and it shows the popup if an opening is waiting. */
    private final LibraryLoads loads = new LibraryLoads();
    private final Activity activity;
    private final Predicate<String> onHome;
    private final Runnable changed;
    private final LinearLayout panel;
    private final boolean reducedMotion;
    private final boolean selectionMode;
    /** library_ok_opens without app_library_selection: OK opens every app, Add to Home is in the hold menu. */
    private final boolean okOpens;
    private final boolean animations;
    private final boolean rtl;
    private TextView instructions;
    private int totalApps;
    final PopupWindow popup;
    private final float density;
    private List<ResolveInfo> apps = new ArrayList<>();
    private String selectedPackage;
    private int selectedPosition;
    private int selectedOffset;
    private final Map<String, Long> installTimes = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> customLabels;
    private long catalogTime;
    private PopupWindow actionPopup;
    private RecyclerView catalog;
    private FrameLayout shelf;
    private GlassView highlight;
    private SpringAnimation spring;
    private float scrollOffset;
    private boolean highlightShown;
    private final int catalogWidth;

    private ResolveInfo holdTarget;
    private View holdAnchor;
    private boolean holdFired;
    private final Runnable holdTimer = () -> {
        if (holdTarget != null && holdAnchor != null && !closed && (actionPopup == null || !actionPopup.isShowing())) {
            holdFired = true;
            showActions(holdTarget, holdAnchor);
        }
    };

    private void cancelHold() {
        main.removeCallbacks(holdTimer);
        holdTarget = null;
        holdAnchor = null;
        holdFired = false;
    }

    private static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_BUTTON_SELECT;
    }

    private void stopHighlight() {
        if (spring != null) spring.cancel();
        if (catalog != null) catalog.stopScroll();
        catalog = null;
        highlightShown = false;
    }

    private void highlight(View card, boolean animate) {
    }

    private Drawable makeCardBg(boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(focused ? 0xFF28282C : 0xFF1C1C1E);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x22FFFFFF);
        return bg;
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        bg.setStroke(dp(1), 0x18FFFFFF);
        return bg;
    }

    private void resize(int width, int height) {
        popup.setWidth(width);
        popup.setHeight(height);
        if (popup.isShowing()) popup.update(width, height);
    }

    /** library_excluded_apps (config.xml), never offered in the catalogue; empty by default, so existing
     *  setups are unaffected. See LibraryApps.excludedFromCatalog(). */
    private final Set<String> excludedApps;
    private final Map<String, String> appIcons;
    private final Map<String, String> launchActions;
    private final java.util.function.Consumer<String> onOpenInput;

    AppLibraryDialog(Activity activity, boolean animations, boolean reducedMotion, boolean selectionMode, boolean okOpens, Predicate<String> onHome, Runnable changed, Runnable dismissed, Set<String> excludedApps, Map<String, String> customLabels, Map<String, String> appIcons, Map<String, String> launchActions, java.util.function.Consumer<String> onOpenInput) {
        this.activity = activity;
        this.animations = animations;
        this.rtl = activity.getResources().getBoolean(R.bool.rtl_app_grid);
        this.reducedMotion = reducedMotion;
        this.selectionMode = selectionMode;
        this.okOpens = okOpens && !selectionMode;
        this.onHome = onHome;
        this.changed = changed;
        this.dismissed = dismissed;
        this.excludedApps = excludedApps;
        this.customLabels = customLabels;
        this.appIcons = appIcons;
        this.launchActions = launchActions;
        this.onOpenInput = onOpenInput;
        density = activity.getResources().getDisplayMetrics().density;
        panel = new LinearLayout(activity);
        panel.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF0A0A0A);
        panel.setFocusableInTouchMode(true);
        panel.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                close();
                return true;
            }
            return false;
        });
        catalogWidth = activity.getWindow().getDecorView().getWidth();
        popup = new PopupWindow(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setOnDismissListener(() -> {
            closed = true;
            dismissActions();
            stopHighlight();
            icons.clear();
            dismissed.run();
        });
    }

    void close() {
        dismissActions();
        if (popup.isShowing()) {
            panel.animate().alpha(0f).setDuration(reducedMotion ? Motion.REDUCED_MS : 160).withEndAction(() -> {
                try { popup.dismiss(); } catch (Exception ignored) {}
            }).start();
        } else if (!closed) {
            closed = true;   // still loading: it never shows
            dismissed.run();
        }
    }

    void show() {
        load(true);
    }

    private void reveal() {
        popup.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        popup.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        popup.showAtLocation(activity.getWindow().getDecorView(), Gravity.FILL, 0, 0);
        View container = panel.getRootView();
        if (container.getLayoutParams() instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) container.getLayoutParams();
            params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0f;
            activity.getWindowManager().updateViewLayout(container, params);
        }
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(reducedMotion ? Motion.REDUCED_MS : 200).start();
    }

    /** Called by the activity's existing package receiver; an uninstall cannot leave a stale action. */
    /** Packages with a leanback entry, from the last refresh. */
    private final java.util.Set<String> tvPackages = new java.util.HashSet<>();

    void refresh() {
        load(false);
    }

    /**
     * Reads the catalogue on LOADER, then fills it on the UI thread. reveal: from opening the library; the popup
     * appears once the latest load has filled it, even when a refresh replaced the opening's own load.
     */
    private void load(boolean reveal) {
        dismissActions();
        int load = loads.start(reveal);
        PackageManager pm = activity.getPackageManager();
        String self = activity.getPackageName();
        LOADER.execute(() -> {
            Map<String, ResolveInfo> unique = new LinkedHashMap<>();
            Set<String> tv = new HashSet<>();
            // Prefer the TV entry when an app declares both categories.
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    if (info.activityInfo == null || !info.activityInfo.exported) continue;
                    String pkg = info.activityInfo.packageName;
                    if (Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category)) tv.add(pkg);
                    if (!pkg.equals(self) && !LibraryApps.excludedFromCatalog(excludedApps, pkg) && !unique.containsKey(pkg)) unique.put(pkg, info);
                }
            }
            Map<String, String> names = new HashMap<>();
            Map<String, Long> installed = new HashMap<>();
            for (Map.Entry<String, ResolveInfo> entry : unique.entrySet()) {
                String pkg = entry.getKey();
                long updated = 0;
                try {
                    PackageInfo info = pm.getPackageInfo(pkg, 0);
                    installed.put(pkg, info.firstInstallTime);
                    updated = info.lastUpdateTime;
                } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
                    installed.put(pkg, 0L);
                }
                Known known = KNOWN.get(pkg);
                if (known == null || known.updated != updated) {
                    known = new Known(updated, loadLabel(pm, entry.getValue()));
                    KNOWN.put(pkg, known);
                }
                String custom = customLabels != null ? customLabels.get(pkg) : null;
                names.put(pkg, custom != null ? custom : known.label);
            }
            KNOWN.keySet().retainAll(unique.keySet());
            main.post(() -> {
                if (closed || !loads.isLatest(load)) return;
                tvPackages.clear();
                tvPackages.addAll(tv);
                installTimes.clear();
                installTimes.putAll(installed);
                labels.clear();
                labels.putAll(names);
                fill(new ArrayList<>(unique.values()));
                if (loads.takeReveal()) reveal();
            });
        });
    }

    private void fill(List<ResolveInfo> catalogue) {
        totalApps = catalogue.size();
        apps = catalogue;
        Map<String, Boolean> home = new HashMap<>();
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            home.put(pkg, onHome.test(pkg));
        }
        catalogTime = System.currentTimeMillis();
        Collator collator = Collator.getInstance();
        apps.sort((a, b) -> {
            String pa = a.activityInfo.packageName, pb = b.activityInfo.packageName;
            if (selectionMode) return LibraryApps.compareCatalog(home.get(pa), label(a), pa,
                    home.get(pb), label(b), pb, collator);
            int byLabel = collator.compare(label(a), label(b));
            return byLabel != 0 ? byLabel : pa.compareTo(pb);
        });
        showCatalog();
    }

    private String label(ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        if (customLabels != null && customLabels.containsKey(pkg)) return customLabels.get(pkg);
        String cached = labels.get(pkg);
        if (cached != null) return cached;
        String value = loadLabel(activity.getPackageManager(), app);
        labels.put(pkg, value);
        return value;
    }

    private static String loadLabel(PackageManager pm, ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        try { return Cards.cleanLabel(String.valueOf(app.loadLabel(pm)), pkg); }
        catch (RuntimeException unavailable) { return pkg; }
    }

    private Drawable customIcon(String pkg) {
        String name = appIcons != null ? appIcons.get(pkg) : null;
        if (name == null || name.isEmpty()) return null;
        Drawable external = CustomArt.loadDrawable(activity, CustomArt.ICONS_DIR, name, 256, 256);
        if (external != null) return external;
        if (!name.startsWith("ic_")) return null;
        int id = activity.getResources().getIdentifier(name, "drawable", activity.getPackageName());
        return id != 0 ? activity.getDrawable(id) : null;
    }

    /** The app's icon from this opening's cache, or loaded on LOADER and set once it arrives. */
    private void bindIcon(ImageView icon, ResolveInfo app) {
        String pkg = app.activityInfo.packageName;
        icon.setTag(pkg);
        Drawable custom = customIcon(pkg);
        if (custom != null) {
            icon.setImageDrawable(custom);
            return;
        }
        Drawable cached = icons.get(pkg);
        icon.setImageDrawable(cached);
        if (cached != null || !iconsLoading.add(pkg)) return;
        PackageManager pm = activity.getPackageManager();
        LOADER.execute(() -> {
            Drawable loaded;
            try { loaded = app.loadIcon(pm); }
            catch (RuntimeException unavailable) { loaded = null; }
            Drawable result = loaded;
            main.post(() -> {
                iconsLoading.remove(pkg);
                if (closed) return;
                Drawable shown = result != null ? result : activity.getDrawable(R.drawable.ic_apps);
                icons.put(pkg, shown);
                // The view may show another app by now; a view bound to it later takes it from the cache.
                if (pkg.equals(icon.getTag())) icon.setImageDrawable(shown);
            });
        });
    }

    private void heading(String text) {
        TextView title = text(text, 18, 0xFFFFFFFF);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(12), dp(10), dp(12), dp(8));
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Reuse translated actions instead of shipping another hint in every locale. */
    private String hint(boolean home) {
        if (selectionMode) return activity.getString(android.R.string.ok) + ": "
                + activity.getString(home ? R.string.library_remove_shortcut : R.string.library_add_home);
        if (home) {
            return activity.getString(R.string.library_hint_on_home);
        } else {
            return activity.getString(R.string.library_hint_not_on_home);
        }
    }

    private void showCatalog() {
        stopHighlight();
        View decor = activity.getWindow().getDecorView();
        int w = decor.getWidth() > 0 ? decor.getWidth() : dp(1280);
        int h = decor.getHeight() > 0 ? decor.getHeight() : dp(720);
        resize(w, h);
        panel.removeAllViews();
        panel.setBackgroundColor(0xFF0A0A0A);
        panel.setPadding(0, 0, 0, 0);

        LinearLayout header = new LinearLayout(activity);
        header.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(60), dp(36), dp(60), dp(24));

        TextView title = text(activity.getString(R.string.app_library), 34, Color.WHITE);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(-2, -2));

        TextView count = text(activity.getResources().getQuantityString(R.plurals.library_app_count, totalApps, totalApps), 14, 0x88FFFFFF);
        count.setSingleLine(true);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
        countLp.setMarginStart(dp(16));
        countLp.gravity = Gravity.BOTTOM;
        countLp.bottomMargin = dp(4);
        header.addView(count, countLp);

        View spacer = new View(activity);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        instructions = text(hint(false), 13, 0x88FFFFFF);
        instructions.setSingleLine(true);
        instructions.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(instructions, new LinearLayout.LayoutParams(-2, -2));

        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (apps.isEmpty()) {
            heading(activity.getString(R.string.library_empty));
            panel.setFocusableInTouchMode(true);
            panel.requestFocus();
            return;
        }

        RecyclerView list = new RecyclerView(activity);
        list.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        catalog = list;
        GridLayoutManager glm = new GridLayoutManager(activity, COLUMNS) {
            @Override protected boolean isLayoutRTL() { return rtl; }
        };
        list.setLayoutManager(glm);
        list.setItemAnimator(null);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setClipChildren(false);
        list.setPadding(dp(60), dp(28), dp(60), dp(48));
        list.setAdapter(new Catalog());

        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int position = Math.min(selectedPosition, apps.size() - 1);
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).activityInfo.packageName.equals(selectedPackage)) { position = i; break; }
        }
        final int target = Math.max(0, position);
        list.scrollToPosition(target);
        list.post(() -> {
            if (catalog != list || !popup.isShowing()) return;
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(target);
            if (holder instanceof Row) ((Row) holder).card.requestFocus();
        });
    }

    private void dismissActions() {
        cancelHold();
        PopupWindow menu = actionPopup;
        actionPopup = null;
        if (menu != null) menu.dismiss();
    }

    private void restoreCardFocus() {
        if (catalog == null || !popup.isShowing()) return;
        catalog.post(() -> {
            if (catalog == null || !popup.isShowing() || actionPopup != null) return;
            RecyclerView.ViewHolder holder = catalog.findViewHolderForAdapterPosition(selectedPosition);
            if (holder instanceof Row) ((Row) holder).card.requestFocus();
        });
    }

    private void changeHome(ResolveInfo app, boolean add) {
        if (!LibraryApps.save(activity, app.activityInfo.packageName, add)) {
            Toast.makeText(activity, R.string.library_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        if (selectionMode) {
            updateSelectionFocus(app, add);
            changed.run();
            fill(new ArrayList<>(apps));
            return;
        }
        selectedOffset = 0;
        if (add && catalog != null) {
            RecyclerView.ViewHolder holder = catalog.findViewHolderForAdapterPosition(selectedPosition);
            if (holder != null) {
                View card = holder.itemView;
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) card.getLayoutParams();
                selectedOffset = rtl
                        ? catalog.getWidth() - catalog.getPaddingRight()
                            - catalog.getLayoutManager().getDecoratedRight(card) - params.rightMargin
                        : catalog.getLayoutManager().getDecoratedLeft(card)
                            - params.leftMargin - catalog.getPaddingLeft();
            }
            // Advance through missing shortcuts instead of following the newly checked app
            // into the Home group. Preserve its screen slot when the catalogue is rebuilt.
            int next = LibraryApps.nextFocus(selectedPosition, apps.size(),
                    index -> !onHome.test(apps.get(index).activityInfo.packageName));
            if (next >= 0) {
                selectedPackage = apps.get(next).activityInfo.packageName;
                selectedPosition = next;
            }
        }
        dismissActions();
        changed.run();
        refresh();
    }

    private void updateSelectionFocus(ResolveInfo app, boolean add) {
        int position = apps.indexOf(app);
        int next = add ? -1 : LibraryApps.nextAfterRemoval(position, apps.size());
        selectedOffset = 0;
        if (!add && catalog != null) {
            RecyclerView.ViewHolder holder = catalog.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                View card = holder.itemView;
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) card.getLayoutParams();
                selectedOffset = rtl
                        ? catalog.getWidth() - catalog.getPaddingRight()
                            - catalog.getLayoutManager().getDecoratedRight(card) - params.rightMargin
                        : catalog.getLayoutManager().getDecoratedLeft(card)
                            - params.leftMargin - catalog.getPaddingLeft();
            }
        }
        selectedPackage = next >= 0 ? apps.get(next).activityInfo.packageName : app.activityInfo.packageName;
    }

    private void open(ResolveInfo app) {
        FireHomeRedirect.cancelActive();
        close();
        String pkg = app.activityInfo.packageName;
        String action = launchActions != null ? launchActions.get(pkg) : null;
        String inputId = LaunchActions.inputId(action);
        if (inputId != null && onOpenInput != null) {
            onOpenInput.accept(inputId);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(
                new ComponentName(pkg, app.activityInfo.name));
        // TV apps keep the leanback launch intent, so an app already running resumes instead of restarting.
        if (tvPackages.contains(pkg)) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (action != null) {
            Intent byAction = LaunchActions.intent(pkg, action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activity.getPackageManager().resolveActivity(byAction, 0) != null) intent = byAction;
        }
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show();
        }
    }

    private void showActions(ResolveInfo app, View anchor) {
        dismissActions();
        selectedPackage = app.activityInfo.packageName;
        String pkg = selectedPackage;
        if (spring != null) spring.cancel();
        View menuPanel = activity.getLayoutInflater().inflate(R.layout.popup_inputs, panel, false);
        LinearLayout rows = menuPanel.findViewById(R.id.rows);
        PopupWindow menu = new PopupWindow(menuPanel, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        actionPopup = menu;
        menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        menu.setOutsideTouchable(true);
        menu.setAnimationStyle(0);
        menu.setElevation(0f);
        menu.setOnDismissListener(() -> {
            if (actionPopup == menu) actionPopup = null;
            restoreCardFocus();
        });
        long showTime = SystemClock.uptimeMillis();
        boolean home = onHome.test(pkg);
        addAction(rows, home ? R.string.library_remove_shortcut : R.string.library_add_home,
                () -> changeHome(app, !home), showTime);
        addAction(rows, R.string.library_open, () -> open(app), showTime);
        Intent info = SystemSettings.appInfo(activity, pkg);
        if (info != null) addAction(rows, R.string.library_app_info, () -> {
            close();
            try {
                activity.startActivity(info);
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show();
            }
        }, showTime);
        int menuWidth = dp(220);
        for (int i = 0; i < rows.getChildCount(); i++) {
            TextView title = rows.getChildAt(i).findViewById(R.id.title);
            menuWidth = Math.max(menuWidth, (int) Math.ceil(title.getPaint().measureText(title.getText().toString())) + dp(58));
            title.setEllipsize(TextUtils.TruncateAt.END);
        }
        menuWidth = Math.min(menuWidth, panel.getWidth() - dp(24));
        menu.setWidth(menuWidth);
        menuPanel.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int[] panelLocation = new int[2];
        panel.getLocationOnScreen(panelLocation);
        Rect screen = new Rect(panelLocation[0], panelLocation[1],
                panelLocation[0] + panel.getWidth(), panelLocation[1] + panel.getHeight());
        int x = Math.max(screen.left + dp(12), Math.min(location[0] + anchor.getWidth() - dp(12), screen.right - menuWidth - dp(12)));
        int y = Math.max(screen.top + dp(12), Math.min(location[1] + dp(8), screen.bottom - menuPanel.getMeasuredHeight() - dp(12)));
        menuPanel.setAlpha(0f);
        menuPanel.setPivotY(0f);
        menuPanel.setScaleY(0.92f);
        menuPanel.setTranslationY(-dp(8));
        menu.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP | Gravity.START, x, y);
        menuPanel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(reducedMotion ? Motion.REDUCED_MS : 220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addAction(LinearLayout rows, int label, Runnable action, long showTime) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((TextView) row.findViewById(R.id.title)).setText(label);
        row.setFocusableInTouchMode(true);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            if (SystemClock.uptimeMillis() - showTime < 350) return;
            action.run();
        });
        rows.addView(row);
        row.setOnKeyListener((v, key, event) -> {
            if (SystemClock.uptimeMillis() - showTime < 350) {
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) return true;
            }
            int index = rows.indexOfChild(v);
            return event.getAction() == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP && index == 0
                    || key == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.getChildCount() - 1);
        });
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_FIRST_STRONG);
        view.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(UiFont.get(activity, R.string.font_medium));
        view.setLetterSpacing(0.01f);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(int value) { return Math.round(value * density); }

    private final class Catalog extends RecyclerView.Adapter<Row> {
        @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            FrameLayout cell = new FrameLayout(activity);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            RecyclerView.LayoutParams cellLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cellLp.setMargins(dp(6), dp(8), dp(6), dp(16));
            cell.setLayoutParams(cellLp);

            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            cell.addView(container, new FrameLayout.LayoutParams(-1, -2));

            FrameLayout card = new FrameLayout(activity);
            card.setId(R.id.card);
            int cardSize = dp(96);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardSize, cardSize);
            card.setLayoutParams(cardLp);
            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            card.setClickable(true);
            card.setClipChildren(false);

            ImageView icon = new ImageView(activity);
            icon.setId(R.id.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
                }
            });
            icon.setClipToOutline(true);
            card.addView(icon, new FrameLayout.LayoutParams(-1, -1));

            container.addView(card);

            TextView name = text("", 14, Color.WHITE);
            name.setId(R.id.title);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setAlpha(0.75f);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(dp(130), dp(22));
            nameLp.topMargin = dp(8);
            container.addView(name, nameLp);

            return new Row(cell, card, icon, name);
        }

        @Override public void onBindViewHolder(@NonNull Row holder, int position) {
            ResolveInfo app = apps.get(position);
            String pkg = app.activityInfo.packageName;
            bindIcon(holder.icon, app);
            holder.name.setText(label(app));

            holder.card.setOnClickListener(v -> {
                boolean onHomeNow = onHome.test(pkg);
                if (LibraryApps.okOpens(selectionMode, okOpens, onHomeNow)) open(app);
                else changeHome(app, !onHomeNow);
            });
            holder.card.setOnLongClickListener(selectionMode ? null : v -> { showActions(app, v); return true; });

            boolean isFocused = holder.card.isFocused();
            holder.card.setScaleX(isFocused ? 1.25f : 1f);
            holder.card.setScaleY(isFocused ? 1.25f : 1f);
            holder.card.setTranslationZ(isFocused ? dp(10) : 0f);
            holder.name.setAlpha(isFocused ? 1.0f : 0.75f);

            holder.card.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    cancelHold();
                    selectedPackage = pkg;
                    selectedPosition = holder.getBindingAdapterPosition();
                    instructions.setText(hint(onHome.test(pkg)));
                    v.animate().scaleX(1.25f).scaleY(1.25f).translationZ(dp(10)).setDuration(160).start();
                    holder.name.animate().alpha(1.0f).setDuration(160).start();
                } else {
                    cancelHold();
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(140).start();
                    holder.name.animate().alpha(0.75f).setDuration(140).start();
                }
            });

            holder.card.setOnKeyListener((v, key, event) -> {
                int index = holder.getBindingAdapterPosition();
                int total = apps.size();
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (key == KeyEvent.KEYCODE_DPAD_UP && index < COLUMNS) return true;
                    if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int lastRowStart = total - (total % COLUMNS == 0 ? COLUMNS : total % COLUMNS);
                        if (index >= lastRowStart) return true;
                    }
                    if (isOkKey(key)) {
                        if (selectionMode) return false;
                        if (event.getRepeatCount() == 0) {
                            holdFired = false;
                            main.removeCallbacks(holdTimer);
                            holdTarget = app;
                            holdAnchor = v;
                            main.postDelayed(holdTimer, 350);
                            return false;
                        } else if (!holdFired && event.getRepeatCount() >= 1) {
                            holdFired = true;
                            main.removeCallbacks(holdTimer);
                            showActions(app, v);
                            return true;
                        }
                        return holdFired;
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (isOkKey(key)) {
                        main.removeCallbacks(holdTimer);
                        holdTarget = null;
                        holdAnchor = null;
                        if (holdFired) {
                            holdFired = false;
                            return true;
                        }
                    }
                    if (!selectionMode && (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_WINDOW)) {
                        showActions(app, v);
                        return true;
                    }
                }
                return false;
            });
        }
        @Override public int getItemCount() { return apps.size(); }
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final View card;
        final ImageView icon;
        final TextView name;
        Row(View cell, View card, ImageView icon, TextView name) {
            super(cell);
            this.card = card;
            this.icon = icon;
            this.name = name;
        }
    }
}
