package org.icegram.messenger;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class UltraEconomyRefreshRate {
    private static final WeakHashMap<View, Preference> preferences = new WeakHashMap<>();

    private UltraEconomyRefreshRate() {
    }

    public static synchronized void set(Window window, float rate) {
        if (window == null) {
            return;
        }
        set(window.getWindowManager(), window.getDecorView(), window.getAttributes(), window, rate);
    }

    public static synchronized void set(WindowManager manager, View view, WindowManager.LayoutParams params, Window window, float rate) {
        if (manager == null || view == null || params == null) {
            return;
        }
        UltraEconomyInterfaceFeedback.watch(view);
        Preference preference = preferences.get(view);
        if (preference == null) {
            preference = new Preference();
            preference.mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? params.preferredDisplayModeId : 0;
            preferences.put(view, preference);
        } else if (!preference.limited && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preference.mode = params.preferredDisplayModeId;
        }
        preference.manager = new WeakReference<>(manager);
        preference.window = new WeakReference<>(window);
        preference.params = params;
        preference.rate = rate;
        apply(view, preference);
    }

    public static synchronized void refresh() {
        for (View view : new ArrayList<>(preferences.keySet())) {
            Preference preference = preferences.get(view);
            if (view != null && preference != null) {
                apply(view, preference);
            }
        }
    }

    private static void apply(View view, Preference preference) {
        boolean limited = AndroidUtilities.screenMaxRefreshRate > 60.1f
                && UltraEconomyController.isEnabled(UltraEconomyController.Option.REFRESH_RATE);
        float rate = limited ? (preference.rate > 0 ? Math.min(60f, preference.rate) : 60f) : preference.rate;
        preference.params.preferredRefreshRate = rate;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            preference.params.preferredDisplayModeId = limited ? 0 : preference.mode;
        }
        preference.limited = limited;
        try {
            Window window = preference.window.get();
            WindowManager manager = preference.manager.get();
            if (window != null) {
                window.setAttributes(preference.params);
            } else if (manager != null && view.isAttachedToWindow()) {
                manager.updateViewLayout(view, preference.params);
            }
        } catch (Exception exception) {
            FileLog.e(exception);
        }
    }

    private static final class Preference {
        WeakReference<WindowManager> manager;
        WeakReference<Window> window;
        WindowManager.LayoutParams params;
        float rate;
        int mode;
        boolean limited;
    }
}
