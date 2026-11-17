package im.marin.onyxtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.Locale;

/**
 * Loads optional user-supplied tile banners and library icons from {@code files/banners/} and
 * {@code files/icons/} in the app's external files directory (populated from {@code onyx-tv/img/}),
 * keeping regional or device-specific logos outside the base APK.
 */
final class CustomArt {
    static final String BANNERS_DIR = "banners";
    static final String ICONS_DIR = "icons";
    private static final String[] EXTENSIONS = { ".png", ".jpg", ".jpeg", ".webp" };

    private CustomArt() {
    }

    /** Resolves a safe file inside {@code baseDir/folder} for {@code name}, trying common image extensions. */
    static File resolveFile(File baseDir, String folder, String name) {
        if (baseDir == null || folder == null || name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            return null;
        }
        File dir = new File(baseDir, folder);
        if (!dir.isDirectory()) return null;
        if (hasImageExtension(trimmed)) {
            File exact = new File(dir, trimmed);
            return exact.isFile() && exact.length() > 0 ? exact : null;
        }
        for (String ext : EXTENSIONS) {
            File candidate = new File(dir, trimmed + ext);
            if (candidate.isFile() && candidate.length() > 0) return candidate;
        }
        return null;
    }

    static boolean hasImageExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /** Power-of-two subsampling factor so decoded art fits comfortably within {@code maxWidth x maxHeight}. */
    static int sampleSize(int rawWidth, int rawHeight, int maxWidth, int maxHeight) {
        int sample = 1;
        if (rawWidth <= 0 || rawHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) return sample;
        while ((rawWidth / (sample * 2)) >= maxWidth && (rawHeight / (sample * 2)) >= maxHeight) {
            sample *= 2;
        }
        return sample;
    }

    static Drawable loadDrawable(Context ctx, String folder, String name, int maxWidth, int maxHeight) {
        File file = resolveFile(Config.dir(ctx), folder, name);
        if (file == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight);
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
            return bmp != null ? new BitmapDrawable(ctx.getResources(), bmp) : null;
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }
}
