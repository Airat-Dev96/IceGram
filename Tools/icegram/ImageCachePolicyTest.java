import org.icegram.messenger.ImageCachePolicy;

/** Plain JVM tests: no Android SDK, accounts or network required. */
public final class ImageCachePolicyTest {
    private static final int MIB = 1024 * 1024;
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int memory = 16; memory <= 4096; memory++) {
            int compact = ImageCachePolicy.bitmapCacheBytes(memory, true);
            int original = ImageCachePolicy.bitmapCacheBytes(memory, false);
            int total = compact + ImageCachePolicy.wallpaperCacheBytes(compact, true)
                    + ImageCachePolicy.animationCacheBytes(true);
            check(compact > 0, "Positive bitmap cache: " + memory);
            check(compact <= original, "Do not grow the upstream cache: " + memory);
            check(total <= 20 * MIB, "Combined cache budget: " + memory);
            int expectedOriginal = Math.min(memory >= 192 ? 30 : 15, memory / 7) * MIB;
            check(original == expectedOriginal, "Upstream fallback: " + memory);
            check(ImageCachePolicy.wallpaperCacheBytes(original, false) == original / 4,
                    "Upstream wallpaper cache: " + memory);
        }
        check(ImageCachePolicy.bitmapCacheBytes(256, true) == 16 * MIB, "High-end cap");
        check(ImageCachePolicy.bitmapCacheBytes(64, true) == 5 * MIB, "Low-memory budget");
        check(ImageCachePolicy.animationCacheBytes(false) == 10 * MIB, "Upstream animation budget");
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
            check(ImageCachePolicy.bitmapCacheBytes(invalid, true) > 0, "Safe edge case");
            check(ImageCachePolicy.bitmapCacheBytes(invalid, true) <= 16 * MIB, "Bounded edge case");
        }
        System.out.println("ImageCachePolicy: " + assertions + " checks passed");
    }
}
