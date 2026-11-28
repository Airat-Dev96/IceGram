package org.icegram.messenger;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

public final class UltraEconomyInterfaceFeedback {
    private static final WeakHashMap<View, Boolean> roots = new WeakHashMap<>();
    private static final WeakHashMap<View, State> states = new WeakHashMap<>();

    private UltraEconomyInterfaceFeedback() {
    }

    public static boolean isSilent() {
        return UltraEconomyController.isEnabled(UltraEconomyController.Option.SILENT_INTERFACE);
    }

    public static void watch(View root) {
        if (root == null || roots.containsKey(root)) {
            return;
        }
        roots.put(root, true);
        WeakReference<View> reference = new WeakReference<>(root);
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            View view = reference.get();
            if (view != null && isSilent()) {
                applyTree(view);
            }
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
                if (isSilent()) {
                    applyTree(view);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
            }
        });
        if (isSilent()) {
            applyTree(root);
        }
    }

    public static void disable(View view) {
        if (view != null && isSilent() && !states.containsKey(view)) {
            applyTree(view);
        }
    }

    public static void refresh() {
        if (isSilent()) {
            for (View root : new ArrayList<>(roots.keySet())) {
                if (root != null) {
                    applyTree(root);
                }
            }
        } else {
            for (View view : new ArrayList<>(states.keySet())) {
                State state = states.get(view);
                if (view != null && state != null) {
                    view.setHapticFeedbackEnabled(state.haptic);
                    view.setSoundEffectsEnabled(state.sound);
                }
            }
            states.clear();
        }
    }

    private static void applyTree(View view) {
        if (!states.containsKey(view)) {
            states.put(view, new State(view));
        }
        view.setHapticFeedbackEnabled(false);
        view.setSoundEffectsEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                applyTree(group.getChildAt(index));
            }
        }
    }

    private static final class State {
        final boolean haptic;
        final boolean sound;

        State(View view) {
            haptic = view.isHapticFeedbackEnabled();
            sound = view.isSoundEffectsEnabled();
        }
    }
}
