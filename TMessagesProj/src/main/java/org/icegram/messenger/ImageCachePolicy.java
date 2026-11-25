/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

/** Budgets for reusable image caches, not a limit on the process's total RAM. */
public final class ImageCachePolicy {
    private static final int MIB = 1024 * 1024;

    private ImageCachePolicy() {
    }

    public static int bitmapCacheBytes(int memoryClassMb, boolean compact) {
        final int memory = Math.max(16, memoryClassMb);
        final int upstreamMb = Math.min(memory >= 192 ? 30 : 15, Math.max(1, memory / 7));
        final int compactMb = Math.min(upstreamMb, Math.max(2, Math.min(16, memory / 12)));
        return (compact ? compactMb : upstreamMb) * MIB;
    }

    public static int wallpaperCacheBytes(int bitmapCacheBytes, boolean compact) {
        final int upstreamBytes = Math.max(1, bitmapCacheBytes / 4);
        return compact ? Math.min(2 * MIB, upstreamBytes) : upstreamBytes;
    }

    public static int animationCacheBytes(boolean compact) {
        return compact ? 2 * MIB : 512 * 512 * 2 * 4 * 5;
    }
}
