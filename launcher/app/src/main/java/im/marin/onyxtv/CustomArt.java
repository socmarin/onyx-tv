package im.marin.onyxtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Loads and manages optional user-supplied input banners ({@code files/banners/}), app banners
 * ({@code files/app_banners/}) and 1:1 app icons ({@code files/app_icons/}), plus shared persistence helpers.
 */
final class CustomArt {
    static final String BANNERS_DIR = "banners";
    static final String APP_BANNERS_DIR = "app_banners";
    static final String APP_ICONS_DIR = "app_icons";
    private static final String BUILTIN_INPUT_BANNERS_DIR = "builtin_input_banners";
    private static final String BUILTIN_INPUT_BANNERS_ASSET = "input_banners";
    static final int BANNER_TARGET_WIDTH = 960;
    static final int BANNER_TARGET_HEIGHT = 540;
    static final int ICON_TARGET_SIZE = 256;
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

    /** Lists image files in {@code files/<folder>/} plus built-in input banners when listing {@link #BANNERS_DIR}. */
    static List<File> listImages(Context ctx, String folder) {
        if (ctx == null || folder == null) return Collections.emptyList();
        List<File> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        File dir = Config.dir(ctx);
        collectImages(dir == null ? null : new File(dir, folder), out, seen);
        if (BANNERS_DIR.equals(folder)) {
            collectImages(syncBuiltinInputBanners(ctx), out, seen);
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private static void collectImages(File artDir, List<File> out, Set<String> seen) {
        if (artDir == null || !artDir.isDirectory()) return;
        File[] files = artDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f != null && f.isFile() && f.length() > 0 && hasImageExtension(f.getName())) {
                if (seen.add(f.getName().toLowerCase(Locale.ROOT))) out.add(f);
            }
        }
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
        if (file == null && BANNERS_DIR.equals(folder)) {
            file = resolveFile(syncBuiltinInputBanners(ctx).getParentFile(), BUILTIN_INPUT_BANNERS_DIR, name);
        }
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

    static long packageUpdateStamp(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return 0;
        }
    }

    static void pruneStaleFiles(File target, Set<String> expected) {
        File[] existing = target.listFiles();
        if (existing == null) return;
        for (File f : existing) {
            if (!expected.contains(f.getName())) f.delete();
        }
    }

    static void copyAssetFile(AssetManager assets, String assetPath, File dest, byte[] buffer) {
        try (InputStream in = assets.open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } catch (IOException e) {
            android.util.Log.w("TvHome.CustomArt", "could not unpack asset " + assetPath + ": " + e.getMessage());
        }
    }

    private static File syncBuiltinInputBanners(Context ctx) {
        File target = new File(ctx.getFilesDir(), BUILTIN_INPUT_BANNERS_DIR);
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        long stamp = packageUpdateStamp(ctx);
        if (target.isDirectory() && stamp > 0 && prefs.getLong("builtin_input_banners_stamp", -1L) == stamp) {
            return target;
        }
        AssetManager assets = ctx.getAssets();
        String[] names;
        try {
            names = assets.list(BUILTIN_INPUT_BANNERS_ASSET);
        } catch (IOException e) {
            return target;
        }
        if (names == null || names.length == 0) return target;
        if (!target.isDirectory() && !target.mkdirs()) return target;
        Set<String> expected = new HashSet<>();
        for (String name : names) {
            if (hasImageExtension(name)) expected.add(name);
        }
        pruneStaleFiles(target, expected);
        byte[] buffer = new byte[8192];
        for (String name : expected) {
            copyAssetFile(assets, BUILTIN_INPUT_BANNERS_ASSET + "/" + name, new File(target, name), buffer);
        }
        if (stamp > 0) prefs.edit().putLong("builtin_input_banners_stamp", stamp).apply();
        return target;
    }

    static String safeSlug(String raw, String fallback) {
        String source = raw == null ? fallback : raw;
        String clean = source.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toLowerCase(Locale.ROOT);
        if (clean.length() > 28) clean = clean.substring(clean.length() - 28);
        CRC32 crc = new CRC32();
        crc.update(source.getBytes(StandardCharsets.UTF_8));
        return (clean.isEmpty() ? fallback : clean) + "_" + Long.toHexString(crc.getValue());
    }

    /** Imports an image URI as a normalized 960x540 16:9 PNG inside {@code files/<folder>/}. */
    static String importBannerImage(Context ctx, String folder, String filename, Uri uri) {
        return importImage(ctx, folder, filename, uri, BANNER_TARGET_WIDTH, BANNER_TARGET_HEIGHT, false);
    }

    /** Imports an image URI as a normalized 256x256 1:1 PNG inside {@code files/app_icons/}. */
    static String importIconImage(Context ctx, String filename, Uri uri) {
        return importImage(ctx, APP_ICONS_DIR, filename, uri, ICON_TARGET_SIZE, ICON_TARGET_SIZE, true);
    }

    private static String importImage(Context ctx, String folder, String filename, Uri uri,
                                      int targetW, int targetH, boolean squareIcon) {
        if (ctx == null || filename == null || filename.isEmpty() || uri == null) return null;
        File baseDir = Config.dir(ctx);
        if (baseDir == null) return null;
        File outDir = new File(baseDir, folder);
        if (!outDir.isDirectory() && !outDir.mkdirs()) return null;

        byte[] bytes = readUriBytes(ctx, uri, 12 * 1024 * 1024);
        if (bytes == null || bytes.length == 0) return null;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH);
        Bitmap raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
        if (raw == null) return null;

        File out = new File(outDir, filename);
        Bitmap target = squareIcon ? prepareIconBitmap(raw, targetW) : prepareBannerBitmap(raw, targetW, targetH);
        if (target != raw) raw.recycle();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!target.compress(Bitmap.CompressFormat.PNG, 92, fos)) return null;
            return filename;
        } catch (IOException ignored) {
            return null;
        } finally {
            target.recycle();
        }
    }

    private static Bitmap prepareBannerBitmap(Bitmap src, int outW, int outH) {
        if (AppAdapter.isBannerShaped(src.getWidth(), src.getHeight()) && !src.hasAlpha()) {
            float scale = Math.min(1f, Math.min(outW / (float) src.getWidth(), outH / (float) src.getHeight()));
            int w = Math.max(1, Math.round(src.getWidth() * scale));
            int h = Math.max(1, Math.round(src.getHeight() * scale));
            return Bitmap.createScaledBitmap(src, w, h, true);
        }
        Bitmap canvasBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, outH, 0xFF1D2430, 0xFF0E1218, Shader.TileMode.CLAMP));
        canvas.drawRect(new RectF(0, 0, outW, outH), bg);

        float scale = Math.min((outW * 0.72f) / src.getWidth(), (outH * 0.68f) / src.getHeight());
        float dw = src.getWidth() * scale;
        float dh = src.getHeight() * scale;
        float left = (outW - dw) / 2f;
        float top = (outH - dh) / 2f;
        Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, null, new RectF(left, top, left + dw, top + dh), imgPaint);
        return canvasBmp;
    }

    private static Bitmap prepareIconBitmap(Bitmap src, int side) {
        Bitmap canvasBmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBmp);
        float scale = Math.min(side / (float) src.getWidth(), side / (float) src.getHeight());
        float dw = src.getWidth() * scale;
        float dh = src.getHeight() * scale;
        float left = (side - dw) / 2f;
        float top = (side - dh) / 2f;
        Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, null, new RectF(left, top, left + dw, top + dh), imgPaint);
        return canvasBmp;
    }

    private static byte[] readUriBytes(Context ctx, Uri uri, int maxBytes) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0, read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) return null;
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    static Map<String, String> readMap(SharedPreferences prefs, String key) {
        if (prefs == null) return Collections.emptyMap();
        return parseMap(prefs.getString(key, null));
    }

    static Map<String, String> parseMap(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, String> out = new HashMap<>();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab >= line.length() - 1) continue;
            String k = unescape(line.substring(0, tab)).trim();
            String v = unescape(line.substring(tab + 1)).trim();
            if (!k.isEmpty() && !v.isEmpty()) out.put(k, v);
        }
        return out;
    }

    static String encodeMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim();
            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (k.isEmpty() || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(escape(k)).append('\t').append(escape(v));
        }
        return sb.toString();
    }

    static void setMapEntry(SharedPreferences prefs, String key, String itemKey, String value) {
        if (prefs == null || itemKey == null || itemKey.trim().isEmpty()) return;
        Map<String, String> updated = new HashMap<>(readMap(prefs, key));
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) updated.remove(itemKey);
        else updated.put(itemKey, clean);
        SharedPreferences.Editor editor = prefs.edit();
        String encoded = encodeMap(updated);
        if (encoded.isEmpty()) editor.remove(key);
        else editor.putString(key, encoded);
        editor.apply();
    }

    static List<String> readList(SharedPreferences prefs, String key) {
        if (prefs == null) return Collections.emptyList();
        return parseList(prefs.getString(key, null));
    }

    static List<String> parseList(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String item = unescape(line).trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    static String encodeList(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            String clean = item == null ? "" : item.trim();
            if (clean.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(escape(clean));
        }
        return sb.toString();
    }

    static void writeList(SharedPreferences prefs, String key, List<String> items) {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        String encoded = encodeList(items);
        if (encoded.isEmpty()) editor.remove(key);
        else editor.putString(key, encoded);
        editor.apply();
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                if (next == 't') out.append('\t');
                else if (next == 'n') out.append('\n');
                else out.append(next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
