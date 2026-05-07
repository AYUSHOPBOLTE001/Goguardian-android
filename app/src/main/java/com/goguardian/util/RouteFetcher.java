package com.goguardian.util;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches a road-following route between two points from OSRM's public router
 * and decodes its polyline-5 geometry. Falls back to a synthetic curve so
 * callers can always render *something* without null-checking.
 *
 * Note: router.project-osrm.org is a rate-limited demo service and not for
 * production. For real deployment swap the URL for self-hosted OSRM or another
 * routing provider.
 */
public final class RouteFetcher {

    public interface Callback {
        /** Invoked on the main thread. {@code path} is never null nor empty. */
        void onRoute(List<LatLng> path);
    }

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RouteFetcher() {}

    public static void fetch(LatLng origin, LatLng destination, Callback cb) {
        String url = "https://router.project-osrm.org/route/v1/driving/"
                + origin.longitude + "," + origin.latitude + ";"
                + destination.longitude + "," + destination.latitude
                + "?overview=full&geometries=polyline";
        Request request = new Request.Builder().url(url).build();
        CLIENT.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, java.io.IOException e) {
                deliver(cb, curvedFallback(origin, destination));
            }
            @Override public void onResponse(Call call, Response response) {
                List<LatLng> path = null;
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject json = new JSONObject(r.body().string());
                        if ("Ok".equals(json.optString("code"))) {
                            JSONArray routes = json.getJSONArray("routes");
                            if (routes.length() > 0) {
                                String geo = routes.getJSONObject(0).getString("geometry");
                                List<LatLng> decoded = decodePolyline(geo);
                                if (decoded.size() >= 2) path = decoded;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (path == null) path = curvedFallback(origin, destination);
                deliver(cb, path);
            }
        });
    }

    private static void deliver(Callback cb, List<LatLng> path) {
        MAIN.post(() -> cb.onRoute(path));
    }

    /** Five-point gently curved path — used as an immediate placeholder and as
     *  the offline fallback when routing fails. */
    public static List<LatLng> curvedFallback(LatLng from, LatLng to) {
        List<LatLng> pts = new ArrayList<>();
        double dLat = to.latitude - from.latitude;
        double dLng = to.longitude - from.longitude;
        double pLat = -dLng, pLng = dLat;
        double mag = Math.sqrt(pLat * pLat + pLng * pLng);
        if (mag < 1e-9) mag = 1e-9;
        pLat /= mag;
        pLng /= mag;
        double bend = Math.min(0.0015, 0.18 * Math.hypot(dLat, dLng));
        pts.add(from);
        pts.add(new LatLng(from.latitude + dLat * 0.25 + pLat * bend * 0.6,
                           from.longitude + dLng * 0.25 + pLng * bend * 0.6));
        pts.add(new LatLng(from.latitude + dLat * 0.5 + pLat * bend,
                           from.longitude + dLng * 0.5 + pLng * bend));
        pts.add(new LatLng(from.latitude + dLat * 0.75 + pLat * bend * 0.6,
                           from.longitude + dLng * 0.75 + pLng * bend * 0.6));
        pts.add(to);
        return pts;
    }

    private static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0; result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            poly.add(new LatLng(lat / 1E5, lng / 1E5));
        }
        return poly;
    }
}
