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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Active ride screen. Animates a driver marker from pickup to drop-off
 * over a simulated ride duration. Shows live ETA countdown and status badge.
 * Provides cancel (with ₹30 fee) and auto-completes when the ride ends.
 */
import com.google.android.gms.maps.model.MapStyleOptions;

public class RideActiveFragment extends Fragment implements OnMapReadyCallback {

    // ── Bundle keys ──────────────────────────────────────────────────────────
    public static final String KEY_RIDE_ID = "ride_id";
    public static final String KEY_PICKUP = "pickup";
    public static final String KEY_DROPOFF = "dropoff";
    public static final String KEY_VEHICLE = "vehicle_type";
    public static final String KEY_FARE = "fare";
    public static final String KEY_DRIVER_NAME = "driver_name";
    public static final String KEY_VEH_NUMBER = "veh_number";
    public static final String KEY_VEH_MODEL = "veh_model";
    public static final String KEY_RATING = "driver_rating";
    public static final String KEY_ETA = "eta_minutes";
    public static final String KEY_PLAT = "pickup_lat";
    public static final String KEY_PLNG = "pickup_lng";
    public static final String KEY_DLAT = "dropoff_lat";
    public static final String KEY_DLNG = "dropoff_lng";

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final long TICK_MS          = 250L;
    private static final long RIDE_DURATION_MS = 20_000L;   // flat 20s simulated trip
    private static final int  CANCEL_FEE       = 30;
    private static final int  CAM_FOLLOW_TICKS = 8;         // ~2s camera follow cadence

    // ── Interface ─────────────────────────────────────────────────────────────
    public interface OnActiveRideActionListener {
        void onRideCompleted(String rideId, int fare, String driverName, String vehicleModel);
        void onRideCancelled(String rideId, int fare);
        void onSosRequested();
    }

    private OnActiveRideActionListener listener;

    // ── Views ─────────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private Marker driverMarker;
    private TextView textRideStatus;
    private TextView textActiveDriverName;
    private TextView textActiveDriverRating;
    private TextView textActiveVehicleInfo;
    private TextView textActiveEta;
    private TextView textActiveRoutePickup;
    private TextView textActiveRouteDropoff;
    private TextView textActiveFare;
    private ImageView imgActiveVehicleIcon;
    private MaterialButton buttonCancelRide;

    // ── State ─────────────────────────────────────────────────────────────────
    private Handler handler;
    private Runnable tickRunnable;
    private String rideId;
    private String pickup;
    private String dropoff;
    private String vehicleType;
    private int fare;
    private String driverName;
    private String vehicleNumber;
    private String vehicleModel;
    private float driverRating;
    private int etaMinutes;
    private LatLng pickupLatLng;
    private double dropoffLat;
    private double dropoffLng;
    private long startTimeMs;
    private boolean rideCompleted  = false;
    private boolean rideCancelled  = false;
    private final long animDurationMs = RIDE_DURATION_MS;
    private int     camTickCount   = 0;
    private boolean userMovedCamera = false; // once true, stop auto-following the driver
    private List<LatLng> routePoints; // pickup → dropoff path (placeholder, replaced by OSRM route)
    private Polyline routeLine;

    // ── Factory ───────────────────────────────────────────────────────────────
    public static RideActiveFragment newInstance(String rideId, String pickup, String dropoff,
            String vehicleType, int fare,
            String driverName, String vehicleNumber,
            String vehicleModel, float driverRating,
            int etaMinutes,
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
        args.putInt(KEY_ETA, etaMinutes);
        args.putDouble(KEY_PLAT, pickupLat);
        args.putDouble(KEY_PLNG, pickupLng);
        args.putDouble(KEY_DLAT, dropoffLat);
        args.putDouble(KEY_DLNG, dropoffLng);
        RideActiveFragment f = new RideActiveFragment();
        f.setArguments(args);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnActiveRideActionListener) {
            listener = (OnActiveRideActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ride_active, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Parse args
        Bundle args = requireArguments();
        rideId = args.getString(KEY_RIDE_ID, "");
        pickup = args.getString(KEY_PICKUP, "");
        dropoff = args.getString(KEY_DROPOFF, "");
        vehicleType = args.getString(KEY_VEHICLE, "Cab");
        fare = args.getInt(KEY_FARE, 0);
        driverName = args.getString(KEY_DRIVER_NAME, "Driver");
        vehicleNumber = args.getString(KEY_VEH_NUMBER, "DL XX AB0000");
        vehicleModel = args.getString(KEY_VEH_MODEL, "");
        driverRating = args.getFloat(KEY_RATING, 4.5f);
        etaMinutes = args.getInt(KEY_ETA, 10);
        pickupLatLng = new LatLng(args.getDouble(KEY_PLAT, 28.6139), args.getDouble(KEY_PLNG, 77.2090));
        dropoffLat = args.getDouble(KEY_DLAT, 28.62);
        dropoffLng = args.getDouble(KEY_DLNG, 77.24);

        // Bind views
        textRideStatus = view.findViewById(R.id.text_ride_status);
        textActiveDriverName = view.findViewById(R.id.text_active_driver_name);
        textActiveDriverRating = view.findViewById(R.id.text_active_driver_rating);
        textActiveVehicleInfo = view.findViewById(R.id.text_active_vehicle_info);
        textActiveEta = view.findViewById(R.id.text_active_eta);
        textActiveRoutePickup = view.findViewById(R.id.text_active_route_pickup);
        textActiveRouteDropoff = view.findViewById(R.id.text_active_route_dropoff);
        textActiveFare = view.findViewById(R.id.text_active_fare);
        imgActiveVehicleIcon = view.findViewById(R.id.img_active_vehicle_icon);
        buttonCancelRide = view.findViewById(R.id.button_cancel_ride);

        // Populate UI
        textActiveDriverName.setText(driverName);
        textActiveDriverRating.setText(String.format("%.1f ★", driverRating));
        textActiveVehicleInfo.setText(vehicleModel + "  ·  " + vehicleNumber);
        textActiveEta.setText(etaMinutes + " min");
        textActiveRoutePickup.setText(pickup);
        textActiveRouteDropoff.setText(dropoff);
        textActiveFare.setText("₹" + fare);
        setVehicleIcon(vehicleType);

        // Map
        SupportMapFragment mapFrag = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.active_ride_map);
        if (mapFrag != null)
            mapFrag.getMapAsync(this);

        // SOS button
        View fabSos = view.findViewById(R.id.fab_sos_active);
        if (fabSos != null) {
            fabSos.setOnClickListener(v -> {
                if (listener != null) listener.onSosRequested();
            });
        }

        // ── Safety panel ─────────────────────────────────────────────────────
        MaterialSwitch shareSwitch = view.findViewById(R.id.switch_live_share);
        TextView shareSubtitle     = view.findViewById(R.id.text_share_subtitle);
        TextView shareLink         = view.findViewById(R.id.text_share_link);
        View shareCard             = view.findViewById(R.id.card_share_link);
        if (shareSwitch != null) {
            shareSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    shareSubtitle.setText("On — your contact can see live location");
                    shareLink.setText("goguardian.app/trip/"
                            + (rideId.isEmpty() ? "demo" : rideId));
                    if (shareCard != null) {
                        shareCard.setAlpha(0f);
                        shareCard.setVisibility(View.VISIBLE);
                        shareCard.animate().alpha(1f).setDuration(200).start();
                    }
                    Snackbar.make(view,
                            "Trip link sent to your trusted contact",
                            Snackbar.LENGTH_SHORT).show();
                } else {
                    shareSubtitle.setText("Off — share trip with a trusted contact");
                    if (shareCard != null) shareCard.setVisibility(View.GONE);
                }
            });
        }

        // Cancel button with confirmation dialog
        buttonCancelRide.setOnClickListener(v -> promptCancelRide());

        // Intercept back press: never silently lose an active ride
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        promptCancelRide();
                    }
                });

        // Start ride animation
        startTimeMs = System.currentTimeMillis();
        startRideAnimation();
    }

    // ── Ride animation ────────────────────────────────────────────────────────
    private void startRideAnimation() {
        handler = new Handler(Looper.getMainLooper());

        // Update Firebase status to in_progress
        if (!rideId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("rides")
                    .child(rideId).child("status").setValue("in_progress");
        }

        tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || rideCompleted || rideCancelled)
                    return;

                long elapsed = System.currentTimeMillis() - startTimeMs;
                float progress = Math.min(elapsed / (float) animDurationMs, 1f);

                // Animate driver marker along curved route
                LatLng driverPos = pointAlongRoute(progress);
                if (driverMarker != null && driverPos != null) {
                    driverMarker.setPosition(driverPos);

                    camTickCount++;
                    if (googleMap != null && !userMovedCamera && camTickCount >= CAM_FOLLOW_TICKS) {
                        camTickCount = 0;
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(driverPos, 15f),
                            800, null);
                    }
                }

                // ETA from remaining distance (driver → dropoff), not raw progress %
                if (driverPos != null) {
                    float[] rem = new float[1];
                    android.location.Location.distanceBetween(
                        driverPos.latitude, driverPos.longitude,
                        dropoffLat, dropoffLng, rem);
                    double remainingMin = (rem[0] / 1000f) * 1.5; // ~1.5 min/km
                    int displayedMinutes = (int) Math.ceil(remainingMin);
                    if (progress >= 0.95f || displayedMinutes <= 0) {
                        textActiveEta.setText("Arriving!");
                    } else {
                        textActiveEta.setText(displayedMinutes + " min");
                    }
                }

                // Status badge progression (compressed for 20s ride)
                if (progress < 0.15f) {
                    textRideStatus.setText("Starting Trip");
                    textRideStatus.setBackgroundResource(R.drawable.bg_pill_amber);
                } else if (progress < 0.75f) {
                    textRideStatus.setText("On Your Way \uD83C\uDF89");
                    textRideStatus.setBackgroundResource(R.drawable.bg_pill_blue);
                } else {
                    textRideStatus.setText(getString(R.string.status_almost_there));
                    textRideStatus.setBackgroundResource(R.drawable.bg_pill_green);
                }

                if (progress >= 1f) {
                    completeRide();
                    return;
                }
                handler.postDelayed(this, TICK_MS);
            }
        };
        handler.post(tickRunnable);
    }

    // ── Complete ──────────────────────────────────────────────────────────────
    private void completeRide() {
        if (rideCompleted || rideCancelled)
            return;
        rideCompleted = true;
        handler.removeCallbacksAndMessages(null);

        if (!rideId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("rides")
                    .child(rideId).child("status").setValue("completed");
        }

        if (listener != null)
            listener.onRideCompleted(rideId, fare, driverName, vehicleModel);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────
    private void promptCancelRide() {
        if (rideCancelled || rideCompleted) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Cancel Ride?")
                .setMessage("A cancellation fee of ₹" + CANCEL_FEE + " will be charged. "
                        + "You'll receive a refund of ₹" + Math.max(fare - CANCEL_FEE, 0) + ".")
                .setPositiveButton("Yes, Cancel", (d, w) -> performCancel())
                .setNegativeButton("Keep Ride", null)
                .show();
    }

    private void performCancel() {
        if (rideCompleted || rideCancelled)
            return;
        rideCancelled = true;
        handler.removeCallbacksAndMessages(null);

        if (!rideId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("rides")
                    .child(rideId).child("status").setValue("cancelled");
        }

        if (listener != null)
            listener.onRideCancelled(rideId, fare);
    }

    // ── Map ───────────────────────────────────────────────────────────────────
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        try {
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
        } catch (Exception e) {
            e.printStackTrace();
        }
        map.getUiSettings().setZoomControlsEnabled(false);
        // Let the rider freely zoom and pan while the trip is in progress.
        map.getUiSettings().setScrollGesturesEnabled(true);
        map.getUiSettings().setZoomGesturesEnabled(true);
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.getUiSettings().setTiltGesturesEnabled(false);

        // The animation tick re-centers on the driver every ~2s. Once the user
        // touches the map, stop snapping back so their zoom/pan sticks.
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                userMovedCamera = true;
            }
        });

        LatLng dropoffLatLng = new LatLng(dropoffLat, dropoffLng);

        // Pickup marker (green)
        map.addMarker(new MarkerOptions()
                .position(pickupLatLng).title("Pickup")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Drop-off marker (red)
        map.addMarker(new MarkerOptions()
                .position(dropoffLatLng).title("Drop-off")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Placeholder curve so the driver marker can start animating immediately;
        // swapped for a real OSRM road route as soon as it arrives. The animation
        // tick reads `routePoints` each frame, so updating the list reroutes the
        // driver along actual streets without restarting the animation.
        routePoints = com.goguardian.util.RouteFetcher.curvedFallback(pickupLatLng, dropoffLatLng);
        routeLine = map.addPolyline(new PolylineOptions()
                .addAll(routePoints)
                .width(10f).color(0xFF00E5FF).geodesic(false)
                .jointType(com.google.android.gms.maps.model.JointType.ROUND));

        com.goguardian.util.RouteFetcher.fetch(pickupLatLng, dropoffLatLng, path -> {
            if (!isAdded() || googleMap == null) return;
            routePoints = path;
            if (routeLine != null) routeLine.setPoints(path);
        });

        // Driver marker — starts at pickup, shows the booked vehicle's icon.
        driverMarker = map.addMarker(new MarkerOptions()
                .position(pickupLatLng)
                .title(driverName)
                .icon(com.goguardian.util.MarkerIconUtil.forVehicle(requireContext(), vehicleType, 48))
                .anchor(0.5f, 0.5f)
                .flat(true));

        // Camera padding so bottom card doesn't cover route
        map.setPadding(0, 0, 0, dpToPx(340));
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(pickupLatLng).include(dropoffLatLng).build();
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60));
    }

    // ── Route helpers ─────────────────────────────────────────────────────────
    /** Build a 5-point curved path between two points with mild perpendicular offset
     *  so the polyline (and the driver marker following it) don't look perfectly straight. */
    private List<LatLng> buildCurvedRoute(LatLng from, LatLng to) {
        List<LatLng> pts = new ArrayList<>();
        double dLat = to.latitude - from.latitude;
        double dLng = to.longitude - from.longitude;
        // perpendicular unit-ish offset
        double pLat = -dLng;
        double pLng =  dLat;
        double mag  = Math.sqrt(pLat * pLat + pLng * pLng);
        if (mag < 1e-9) mag = 1e-9;
        pLat /= mag;
        pLng /= mag;
        double bend = Math.min(0.0025, 0.18 * Math.hypot(dLat, dLng)); // softer for short trips
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

    /** Sample the route at fractional progress [0,1], walking segment by segment. */
    private LatLng pointAlongRoute(float progress) {
        if (routePoints == null || routePoints.size() < 2) return null;
        if (progress <= 0f) return routePoints.get(0);
        if (progress >= 1f) return routePoints.get(routePoints.size() - 1);
        int segments = routePoints.size() - 1;
        float scaled = progress * segments;
        int idx = (int) scaled;
        float local = scaled - idx;
        LatLng a = routePoints.get(idx);
        LatLng b = routePoints.get(idx + 1);
        return new LatLng(
            a.latitude  + (b.latitude  - a.latitude)  * local,
            a.longitude + (b.longitude - a.longitude) * local);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void setVehicleIcon(String type) {
        switch (type) {
            case "Bike":
                imgActiveVehicleIcon.setImageResource(R.drawable.ic_motorcycle);
                break;
            case "Auto":
                imgActiveVehicleIcon.setImageResource(R.drawable.ic_auto_rickshaw);
                break;
            default:
                imgActiveVehicleIcon.setImageResource(R.drawable.ic_car_side);
                break;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null)
            handler.removeCallbacksAndMessages(null);
    }
}
