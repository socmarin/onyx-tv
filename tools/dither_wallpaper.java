import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.SplittableRandom;

/**
 * Smooths 8-bit quantization steps in dark wallpaper gradients while preserving sharp details
 * (stars, moon, trees, horizon) and applies 2x2-aligned TPDF dithering so 4K wallpapers stay
 * band-free even when decoded with inSampleSize=2 on 1080p TVs.
 *
 * Usage:
 *   java tools/dither_wallpaper.java <input-image> <output.jpg> [jpeg-quality]
 */
public class dither_wallpaper {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java tools/dither_wallpaper.java <input> <output.jpg> [quality=0.96]");
            System.exit(1);
        }
        File src = new File(args[0]);
        File dst = new File(args[1]);
        float quality = args.length >= 3 ? Float.parseFloat(args[2]) : 0.96f;

        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();

        BufferedImage img = ImageIO.read(src);
        if (img == null) {
            throw new IllegalArgumentException("Could not read image: " + src);
        }
        int w = img.getWidth();
        int h = img.getHeight();

        float[] r = new float[w * h];
        float[] g = new float[w * h];
        float[] b = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int idx = y * w + x;
                r[idx] = (rgb >> 16) & 0xff;
                g[idx] = (rgb >> 8) & 0xff;
                b[idx] = rgb & 0xff;
            }
        }

        int radius = Math.max(24, Math.round(64f * w / 3840f));
        float[] sr = smoothFlat(r, w, h, radius, 6.5f);
        float[] sg = smoothFlat(g, w, h, radius, 6.5f);
        float[] sb = smoothFlat(b, w, h, radius, 6.5f);

        int bw = (w + 1) / 2;
        int bh = (h + 1) / 2;
        float[] noise = new float[bw * bh];
        SplittableRandom rng = new SplittableRandom(20261118L);
        for (int i = 0; i < noise.length; i++) {
            noise[i] = (float) ((rng.nextDouble() + rng.nextDouble() - 1.0) * 2.2);
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            int by = y / 2;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                float origLuma = Math.max(r[idx], Math.max(g[idx], b[idx]));
                float diff = Math.abs(sr[idx] - r[idx]) + Math.abs(sg[idx] - g[idx]) + Math.abs(sb[idx] - b[idx]);
                float dither = (origLuma < 85f && diff < 16f) ? noise[by * bw + (x / 2)] : 0f;
                int cr = clamp(Math.round(sr[idx] + dither));
                int cg = clamp(Math.round(sg[idx] + dither));
                int cb = clamp(Math.round(sb[idx] + dither));
                out.setRGB(x, y, (cr << 16) | (cg << 8) | cb);
            }
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (FileImageOutputStream fios = new FileImageOutputStream(dst)) {
            writer.setOutput(fios);
            writer.write(null, new IIOImage(out, null, null), param);
        }
        writer.dispose();
        System.out.printf("Wrote %s (%dx%d, %.1f KB)%n", dst.getPath(), w, h, dst.length() / 1024.0);
    }

    private static float[] smoothFlat(float[] src, int w, int h, int radius, float maxDiff) {
        float[] tmp = new float[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float center = src[row + x];
                if (center > 85f) {
                    tmp[row + x] = center;
                    continue;
                }
                double sum = 0;
                int count = 0;
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w - 1, x + radius);
                for (int nx = x0; nx <= x1; nx++) {
                    float v = src[row + nx];
                    if (Math.abs(v - center) <= maxDiff) {
                        sum += v;
                        count++;
                    }
                }
                tmp[row + x] = count > 0 ? (float) (sum / count) : center;
            }
        }
        float[] dst = new float[w * h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float center = tmp[y * w + x];
                if (center > 85f) {
                    dst[y * w + x] = center;
                    continue;
                }
                double sum = 0;
                int count = 0;
                int y0 = Math.max(0, y - radius);
                int y1 = Math.min(h - 1, y + radius);
                for (int ny = y0; ny <= y1; ny++) {
                    float v = tmp[ny * w + x];
                    if (Math.abs(v - center) <= maxDiff) {
                        sum += v;
                        count++;
                    }
                }
                dst[y * w + x] = count > 0 ? (float) (sum / count) : center;
            }
        }
        return dst;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
