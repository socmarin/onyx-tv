package im.marin.onyxtv;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

/**
 * Local Fire OS adapter: bring our existing task forward, or use an alarm for
 * first launch. A non-interactive accessibility overlay hides Amazon's home.
 * Requires {@code alarm_manager_constants min_futurity=0} on Fire OS 7 devices.
 * Does not intercept media, read screen contents, disable Amazon Home or run a poller.
 */
final class FireHomeRedirect {
    private static FireHomeRedirect active;
    private static long lastHomeFocus;
    private static volatile boolean canceled;
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WindowManager windows;
    private final AlarmManager alarms;
    private final PendingIntent target;
    private View cover;
    private int retriesRemaining;
    private final Runnable timeout = this::hide;
    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            if (canceled) return;
            if (MainActivity.isWindowFocused()) {
                shown();
            } else {
                launch();
                if (--retriesRemaining > 0) {
                    handler.postDelayed(this, 160);
                }
            }
        }
    };

    FireHomeRedirect(AccessibilityService service, int coverColor) {
        this.service = service;
        windows = service.getSystemService(WindowManager.class);
        alarms = service.getSystemService(AlarmManager.class);
        Intent home = new Intent(service, MainActivity.class)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        target = PendingIntent.getActivity(service, 7421, home,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                ActivityOptions.makeCustomAnimation(service, 0, 0).toBundle());
        View view = new View(service);
        view.setBackgroundColor(coverColor != 0 ? (coverColor | 0xFF000000) : Color.parseColor("#0E0F12"));
        view.setVisibility(View.GONE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.windowAnimations = 0;
        windows.addView(view, params);
        cover = view;
        active = this;
    }

    void request() {
        android.util.Log.i("FireHomeRedirect", "cover requested");
        canceled = false;
        retriesRemaining = 4;
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(retry);
        handler.postDelayed(timeout, 1800);
        if (!MainActivity.isWindowFocused() && cover != null && cover.getVisibility() != View.VISIBLE) {
            cover.setVisibility(View.VISIBLE);
        }
        launch();
        handler.postDelayed(retry, 150);
    }

    private void launch() {
        // Reusing our own live task avoids an activity launch entirely and is faster
        // on Fire OS 7 than the alarm path.
        ActivityManager manager = service.getSystemService(ActivityManager.class);
        try {
            if (manager != null) {
                for (ActivityManager.AppTask task : manager.getAppTasks()) {
                    ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                    if (info.baseActivity != null && info.topActivity != null
                            && MainActivity.class.getName().equals(info.baseActivity.getClassName())
                            && MainActivity.class.getName().equals(info.topActivity.getClassName())) {
                        task.moveToFront();
                        android.util.Log.i("FireHomeRedirect", "moved existing home task");
                        return;
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException unavailable) {
            android.util.Log.w("FireHomeRedirect", "task move unavailable", unavailable);
        }
        if (alarms != null) {
            android.util.Log.i("FireHomeRedirect", "dispatch alarm");
            alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), target);
        }
    }

    private void hide() {
        retriesRemaining = 0;
        handler.removeCallbacks(retry);
        handler.removeCallbacks(timeout);
        if (alarms != null) alarms.cancel(target);
        if (cover != null) cover.setVisibility(View.GONE);
    }

    /** The launcher owns focus now; reveal its first frame rather than Amazon's. */
    static boolean wasShownAfter(long eventTime) {
        return eventTime <= lastHomeFocus;
    }

    static void shown() {
        lastHomeFocus = SystemClock.uptimeMillis();
        if (active != null) {
            android.util.Log.i("FireHomeRedirect", "home focused");
            active.hide();
        }
    }

    static void cancelActive() {
        if (active != null) {
            active.cancel();
        }
    }

    /** Do not pull the user back if another app or a settings window opened meanwhile. */
    void cancel() {
        canceled = true;
        hide();
    }

    void destroy() {
        canceled = true;
        hide();
        if (cover != null) {
            windows.removeView(cover);
            cover = null;
        }
        if (active == this) active = null;
    }
}
