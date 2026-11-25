/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.IMapsProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.List;

/** No SDK, tiles, renderer, background threads or network requests. */
public final class NoMapsProvider implements IMapsProvider {
    public static final String PLACEHOLDER_URI = "icegram://maps-disabled";

    public static void showDisabled() {
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext,
                LocaleController.getString(R.string.IcegramMapsDisabled), Toast.LENGTH_SHORT).show());
    }

    @Override public void initializeMaps(Context context) { }
    @Override public IMapView onCreateMapView(Context context) { return new DisabledMapView(context); }
    @Override public String getMapsAppPackageName() { return ""; }
    @Override public int getInstallMapsString() { return R.string.IcegramMapsDisabled; }
    @Override public ICameraUpdate newCameraUpdateLatLng(LatLng point) { return new ICameraUpdate() { }; }
    @Override public ICameraUpdate newCameraUpdateLatLngZoom(LatLng point, float zoom) { return new ICameraUpdate() { }; }
    @Override public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) { return new ICameraUpdate() { }; }
    @Override public IMapStyleOptions loadRawResourceStyle(Context context, int resId) { return new IMapStyleOptions() { }; }

    @Override public IMarkerOptions onCreateMarkerOptions() {
        return new IMarkerOptions() {
            @Override public IMarkerOptions position(LatLng value) { return this; }
            @Override public IMarkerOptions icon(Bitmap value) { return this; }
            @Override public IMarkerOptions icon(int value) { return this; }
            @Override public IMarkerOptions anchor(float lat, float lng) { return this; }
            @Override public IMarkerOptions title(String value) { return this; }
            @Override public IMarkerOptions snippet(String value) { return this; }
            @Override public IMarkerOptions flat(boolean value) { return this; }
        };
    }

    @Override public ICircleOptions onCreateCircleOptions() {
        return new ICircleOptions() {
            @Override public ICircleOptions center(LatLng value) { return this; }
            @Override public ICircleOptions radius(double value) { return this; }
            @Override public ICircleOptions strokeColor(int value) { return this; }
            @Override public ICircleOptions fillColor(int value) { return this; }
            @Override public ICircleOptions strokePattern(List<PatternItem> value) { return this; }
            @Override public ICircleOptions strokeWidth(int value) { return this; }
        };
    }

    @Override public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new ILatLngBoundsBuilder() {
            private LatLng center = new LatLng(0, 0);
            @Override public ILatLngBoundsBuilder include(LatLng value) {
                center = value;
                return this;
            }
            @Override public ILatLngBounds build() {
                final LatLng snapshot = center;
                return () -> snapshot;
            }
        };
    }

    private static final class DisabledMapView extends FrameLayout implements IMapView {
        DisabledMapView(Context context) {
            super(context);
            TextView notice = new TextView(context);
            notice.setText(LocaleController.getString(R.string.IcegramMapsDisabled));
            notice.setGravity(Gravity.CENTER);
            int padding = AndroidUtilities.dp(24);
            notice.setPadding(padding, padding, padding, padding);
            addView(notice, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        @Override public View getView() { return this; }
        // Deliberately no selectable map / fake coordinates. Known map pickers are
        // blocked at entry; this is a defensive placeholder for other callers.
        @Override public void getMapAsync(Consumer<IMap> callback) { }
        @Override public void onCreate(Bundle state) { }
        @Override public void onResume() { }
        @Override public void onPause() { }
        @Override public void onDestroy() { }
        @Override public void onLowMemory() { }
        @Override public void setOnDispatchTouchEventInterceptor(ITouchInterceptor interceptor) { }
        @Override public void setOnInterceptTouchEventInterceptor(ITouchInterceptor interceptor) { }
        @Override public void setOnLayoutListener(Runnable callback) { }
    }
}
