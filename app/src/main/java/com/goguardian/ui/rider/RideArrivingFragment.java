package com.goguardian.ui.rider;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * "Driver Arriving" phase. Spawns a driver marker ~500m from the pickup point and
 * animates it toward pickup over a randomized arrival window. When the marker
 * reaches the pickup, the active ride begins automatically.
 */
public class RideArrivingFragment extends Fragment implements OnMapReadyCallback {

    public static final String KEY_RIDE_ID      = "ride_id";
    public static final String KEY_PICKUP       = "pickup";
    public static final String KEY_DROPOFF      = "dropoff";
    public static final String KEY_VEHICLE      = "vehicle_type";
    public static final String KEY_FARE         = "fare";
    public static final String KEY_DRIVER_NAME  = "driver_name";
    public static final String KEY_VEH_NUMBER   = "veh_number";
    public static final String KEY_VEH_MODEL    = "veh_model";
    public static final String KEY_RATING       = "driver_rating";
    public static final String KEY_PLAT         = "pickup_lat";
    public static final String KEY_PLNG         = "pickup_lng";
    public static final String KEY_DLAT         = "dropoff_lat";
    public static final String KEY_DLNG         = "dropoff_lng";

    // Roughly 500m at Delhi latitude — 0.0045° ≈ 500m
    private static final double SPAWN_OFFSET_DEG_MIN = 0.0035;
    private static final double SPAWN_OFFSET_DEG_MAX = 0.0055;
    private static final long   ARRIVAL_DURATION_MS  = 18_000L; // ~18s to "drive" 500m
    private static final long   TICK_MS              = 400L;

    public interface OnArrivingActionListener {
        void onDriverArrived(String rideId, String pickup, String dropoff,
                             String vehicleType, int fare,
                             String driverName, String vehicleNumber,
                             String vehicleModel, float driverRating,
                             double pickupLat, double pickupLng,
                             double dropoffLat, double dropoffLng);
        void onSosRequested();
    }

    private OnArrivingActionListener listener;

    private GoogleMap googleMap;
    private Marker driverMarker;
    private TextView textArrivalEta;
    private TextView textArrivalStatus;

    private Handler handler;
    private Runnable tickRunnable;
    private List<LatLng> approachPoints; // driver→pickup path (placeholder, replaced by OSRM route)
    private Polyline approachLine;

    private String rideId, pickup, dropoff, vehicleType, driverName, vehicleNumber, vehicleModel;
    private int fare;
    private float driverRating;
    private LatLng pickupLatLng;
    private LatLng driverStartLatLng;
    private double dropoffLat, dropoffLng;
    private long startTimeMs;
    private boolean arrived = false;

    public static RideArrivingFragment newInstance(
            String rideId, String pickup, String dropoff,
            String vehicleType, int fare,
            String driverName, String vehicleNumber, String vehicleModel,
            float driverRating,
            double pickupLat, double pickupLng,
            double dropoffLat, double dropoffLng) {
        Bundle args = new Bundle();
        args.putString(KEY_RIDE_ID, rideId);
        args.putString(KEY_PICKUP, pickup);
        args.putString(KEY_DROPOFF, dropoff);
        args.putString(KEY_VEHICLE, vehicleType);
        args.putInt(KEY_FARE, fare);
        args.putString(KEY_DRIVER_NAME, driverName);
        args.putString(KEY_VEH_NUMBER, vehicleNumber);
        args.putString(KEY_VEH_MODEL, vehicleModel);
        args.putFloat(KEY_RATING, driverRating);
        args.putDouble(KEY_PLAT, pickupLat);
        args.putDouble(KEY_PLNG, pickupLng);
        args.putDouble(KEY_DLAT, dropoffLat);
        args.putDouble(KEY_DLNG, dropoffLng);
        RideArrivingFragment f = new RideArrivingFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnArrivingActionListener) {
            listener = (OnArrivingActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ride_arriving, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        rideId        = args.getString(KEY_RIDE_ID, "");
        pickup        = args.getString(KEY_PICKUP, "");
        dropoff       = args.getString(KEY_DROPOFF, "");
        vehicleType   = args.getString(KEY_VEHICLE, "Cab");
        fare          = args.getInt(KEY_FARE, 0);
        driverName    = args.getString(KEY_DRIVER_NAME, "Driver");
        vehicleNumber = args.getString(KEY_VEH_NUMBER, "");
        vehicleModel  = args.getString(KEY_VEH_MODEL, "");
        driverRating  = args.getFloat(KEY_RATING, 4.5f);
        pickupLatLng  = new LatLng(args.getDouble(KEY_PLAT, 28.6139),
                                   args.getDouble(KEY_PLNG, 77.2090));
        dropoffLat    = args.getDouble(KEY_DLAT, 28.62);
        dropoffLng    = args.getDouble(KEY_DLNG, 77.24);

        // Random spawn ~500m from pickup, in any compass direction
        double angle  = Math.random() * 2 * Math.PI;
        double radius = SPAWN_OFFSET_DEG_MIN
                + Math.random() * (SPAWN_OFFSET_DEG_MAX - SPAWN_OFFSET_DEG_MIN);
        driverStartLatLng = new LatLng(
                pickupLatLng.latitude  + Math.sin(angle) * radius,
                pickupLatLng.longitude + Math.cos(angle) * radius);

        // Bind UI
        TextView textDriverName    = view.findViewById(R.id.text_arr_driver_name);
        TextView textDriverRating  = view.findViewById(R.id.text_arr_driver_rating);
        TextView textVehicleInfo   = view.findViewById(R.id.text_arr_vehicle_info);
        TextView textPickupAddress = view.findViewById(R.id.text_arr_pickup);
        ImageView imgVehicle       = view.findViewById(R.id.img_arr_vehicle_icon);
        textArrivalEta             = view.findViewById(R.id.text_arr_eta);
        textArrivalStatus          = view.findViewById(R.id.text_arr_status);

        textDriverName.setText(driverName);
        textDriverRating.setText(String.format("%.1f ★", driverRating));
        textVehicleInfo.setText(vehicleModel + "  ·  " + vehicleNumber);
        textPickupAddress.setText(pickup);
        setVehicleIcon(imgVehicle, vehicleType);

        View fabSos = view.findViewById(R.id.fab_sos_arriving);
        if (fabSos != null) {
            fabSos.setOnClickListener(v -> {
                if (listener != null) listener.onSosRequested();
            });
        }

        // Map
        SupportMapFragment mapFrag = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.arriving_map);
        if (mapFrag != null) mapFrag.getMapAsync(this);

        // Start arrival animation
        handler = new Handler(Looper.getMainLooper());
        startTimeMs = System.currentTimeMillis();
        tickRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded() || arrived) return;
                long elapsed = System.currentTimeMillis() - startTimeMs;
                float progress = Math.min(elapsed / (float) ARRIVAL_DURATION_MS, 1f);

                if (driverMarker != null) {
                    LatLng pos = pointAlong(approachPoints, progress);
                    if (pos != null) driverMarker.setPosition(pos);
                }

                long remainingMs    = (long) ((1f - progress) * ARRIVAL_DURATION_MS);
                int  remainingSecs  = Math.max(1, (int) Math.ceil(remainingMs / 1000.0));
                if (progress >= 1f) {
                    textArrivalEta.setText("Arrived");
                    textArrivalStatus.setText("Driver is here — starting your ride");
                    arrive();
                    return;
                } else if (progress >= 0.85f) {
                    textArrivalEta.setText("Arriving");
                    textArrivalStatus.setText("Almost at pickup");
                } else {
                    textArrivalEta.setText(remainingSecs + "s");
                    textArrivalStatus.setText("Driver is on the way to pickup");
                }
                handler.postDelayed(this, TICK_MS);
            }
        };
        handler.post(tickRunnable);
    }

    private void arrive() {
        if (arrived || !isAdded()) return;
        arrived = true;
        // Brief pause on "Arrived" so the user sees the state before transitioning
        handler.postDelayed(() -> {
            if (!isAdded()) return;
            if (listener != null) {
                listener.onDriverArrived(rideId, pickup, dropoff, vehicleType, fare,
                        driverName, vehicleNumber, vehicleModel, driverRating,
                        pickupLatLng.latitude, pickupLatLng.longitude,
                        dropoffLat, dropoffLng);
            }
        }, 1500L);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        try {
            MapsInitializer.initialize(requireContext().getApplicationContext());
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
        } catch (Exception ignored) {}
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setRotateGesturesEnabled(false);

        // Pickup marker (green)
        map.addMarker(new MarkerOptions()
                .position(pickupLatLng).title("Pickup")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Driver starts at the spawn point — show the booked vehicle's icon so
        // the rider sees a car/bike/auto on the map instead of a generic pin.
        driverMarker = map.addMarker(new MarkerOptions()
                .position(driverStartLatLng).title(driverName)
                .icon(com.goguardian.util.MarkerIconUtil.forVehicle(requireContext(), vehicleType, 48))
                .anchor(0.5f, 0.5f)
                .flat(true));

        // Placeholder curve so the driver can start animating immediately;
        // swapped for the real OSRM road path as soon as it arrives. The
        // animation tick reads `approachPoints` each frame, so updating the
        // list reroutes the marker along real streets without restarting.
        approachPoints = com.goguardian.util.RouteFetcher.curvedFallback(driverStartLatLng, pickupLatLng);
        approachLine = map.addPolyline(new PolylineOptions()
                .addAll(approachPoints)
                .width(8f).color(0xFF00E5FF).geodesic(false)
                .jointType(com.google.android.gms.maps.model.JointType.ROUND));

        com.goguardian.util.RouteFetcher.fetch(driverStartLatLng, pickupLatLng, path -> {
            if (!isAdded() || googleMap == null) return;
            approachPoints = path;
            if (approachLine != null) approachLine.setPoints(path);
        });

        // Frame both points with bottom padding for the info card
        map.setPadding(0, 0, 0, dpToPx(280));
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(pickupLatLng).include(driverStartLatLng).build();
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dpToPx(80)));
    }

    /** Build a 5-point gently curved path between two points. */
    private List<LatLng> buildCurvedRoute(LatLng from, LatLng to) {
        List<LatLng> pts = new ArrayList<>();
        double dLat = to.latitude - from.latitude;
        double dLng = to.longitude - from.longitude;
        double pLat = -dLng;
        double pLng =  dLat;
        double mag  = Math.sqrt(pLat * pLat + pLng * pLng);
        if (mag < 1e-9) mag = 1e-9;
        pLat /= mag;
        pLng /= mag;
        double bend = Math.min(0.0015, 0.18 * Math.hypot(dLat, dLng));
        pts.add(from);
        pts.add(new LatLng(from.latitude + dLat * 0.25 + pLat * bend * 0.6,
                           from.longitude + dLng * 0.25 + pLng * bend * 0.6));
        pts.add(new LatLng(from.latitude + dLat * 0.5  + pLat * bend,
                           from.longitude + dLng * 0.5  + pLng * bend));
        pts.add(new LatLng(from.latitude + dLat * 0.75 + pLat * bend * 0.6,
                           from.longitude + dLng * 0.75 + pLng * bend * 0.6));
        pts.add(to);
        return pts;
    }

    /** Walk the curved path at the given fractional progress. */
    private LatLng pointAlong(List<LatLng> pts, float progress) {
        if (pts == null || pts.size() < 2) return null;
        if (progress <= 0f) return pts.get(0);
        if (progress >= 1f) return pts.get(pts.size() - 1);
        int segments = pts.size() - 1;
        float scaled = progress * segments;
        int idx = (int) scaled;
        float local = scaled - idx;
        LatLng a = pts.get(idx);
        LatLng b = pts.get(idx + 1);
        return new LatLng(
            a.latitude  + (b.latitude  - a.latitude)  * local,
            a.longitude + (b.longitude - a.longitude) * local);
    }

    private void setVehicleIcon(ImageView img, String type) {
        switch (type) {
            case "Bike": img.setImageResource(R.drawable.ic_motorcycle);    break;
            case "Auto": img.setImageResource(R.drawable.ic_auto_rickshaw); break;
            default:     img.setImageResource(R.drawable.ic_car_side);      break;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }
}
