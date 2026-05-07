package com.goguardian.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.goguardian.R;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Helpers for rendering vector drawables as map marker icons.
 *
 * Picks the right enhanced vehicle drawable for each ride type so the driver
 * marker on the map matches the booked vehicle.
 */
public final class MarkerIconUtil {

    private MarkerIconUtil() {}

    /** Returns a marker icon for the given vehicle type. {@code sizeDp} controls
     *  the rendered size — pass ~52dp for a clearly visible marker. */
    public static BitmapDescriptor forVehicle(Context ctx, String vehicleType, int sizeDp) {
        int res;
        switch (vehicleType == null ? "" : vehicleType) {
            case "Bike": res = R.drawable.ic_motorcycle_enhanced; break;
            case "Auto": res = R.drawable.ic_auto_enhanced;       break;
            default:     res = R.drawable.ic_car_enhanced;        break;
        }
        return fromVector(ctx, res, sizeDp);
    }

    public static BitmapDescriptor fromVector(Context ctx, int drawableRes, int sizeDp) {
        int sizePx = (int) (sizeDp * ctx.getResources().getDisplayMetrics().density);
        Drawable d = ContextCompat.getDrawable(ctx, drawableRes);
        if (d == null) return BitmapDescriptorFactory.defaultMarker();
        d.setBounds(0, 0, sizePx, sizePx);
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bm));
        return BitmapDescriptorFactory.fromBitmap(bm);
    }
}
