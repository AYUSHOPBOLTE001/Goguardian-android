package com.goguardian.ui.rider;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

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
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Shows an animated "searching for driver" screen.
 * After ~7 seconds, reveals a simulated driver card then
 * auto-transitions to RideActiveFragment.
 */
public class RideSearchingFragment extends Fragment implements OnMapReadyCallback {

    // ── Bundle keys ──────────────────────────────────────────────────────────
    public static final String KEY_RIDE_ID     = "ride_id";
    public static final String KEY_PICKUP      = "pickup";
    public static final String KEY_DROPOFF     = "dropoff";
    public static final String KEY_VEHICLE     = "vehicle_type";
    public static final String KEY_FARE        = "fare";
    public static final String KEY_PLAT        = "pickup_lat";
    public static final String KEY_PLNG        = "pickup_lng";
    public static final String KEY_DLAT        = "dropoff_lat";
    public static final String KEY_DLNG        = "dropoff_lng";

    // ── Simulation data ───────────────────────────────────────────────────────
    private static final String[] DRIVER_NAMES = {
        "Ravi Kumar", "Suresh Sharma", "Amit Patel",
        "Vikram Singh", "Deepak Yadav", "Rajesh Gupta"
    };
    private static final String[] VEHICLE_NUMBERS = {
        "DL 3C AB1234", "DL 7S CD5678", "DL 2A EF9012",
        "DL 5F GH3456", "DL 8B IJ7890", "UP 14 AZ7654"
    };
    private static final String[] CAB_MODELS  = {
        "Maruti Swift Dzire", "Honda Amaze", "Hyundai Xcent", "Toyota Etios"
    };
    private static final String[] BIKE_MODELS = {
        "Honda Activa 6G", "TVS Jupiter", "Bajaj Pulsar 125", "Hero Splendor+"
    };
    private static final String[] AUTO_MODELS = {
        "Bajaj RE Auto", "Piaggio Ape", "TVS King Electric"
    };
    private static final float[] RATINGS = { 4.2f, 4.5f, 4.7f, 4.8f, 4.9f, 5.0f };

    private LatLng pickupLatLng;
    private static final long    SEARCH_MIN_MS      = 15000L; // 15s minimum
    private static final long    SEARCH_MAX_MS      = 45000L; // 45s maximum
    private static final long    FOUND_PAUSE_MS     = 2500L;  // 2.5 seconds on "found" card

    // ── Interface ─────────────────────────────────────────────────────────────
    public interface OnSearchingActionListener {
        void onDriverFound(String rideId, String pickup, String dropoff,
                           String vehicleType, int fare,
                           String driverName, String vehicleNumber,
                           String vehicleModel, float driverRating,
                           int etaMinutes,
                           double pickupLat, double pickupLng,
                           double dropoffLat, double dropoffLng);
        void onSearchCancelled(String rideId, int fare);
    }

    private OnSearchingActionListener listener;

    // ── Views ─────────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private Marker driverMarker;
    private ProgressBar progressSearching;
    private ImageView imgFoundTick;
    private TextView textStatusTitle;
    private TextView textStatusSub;
    private ImageView imgVehicleIcon;
    private View cardDriverFound;
    private View dividerDriver;
    private TextView textDriverInitials;
    private TextView textDriverName;
    private TextView textDriverRating;
    private TextView textVehicleDetails;
    private TextView textDriverEta;
    private TextView textRoutePickup;
    private TextView textRouteDropoff;
    private TextView textSearchFare;
    private MaterialButton buttonCancelSearch;

    // ── State ─────────────────────────────────────────────────────────────────
    private Handler handler;
    private Runnable circleRunnable;
    private android.animation.AnimatorSet vehicleIconAnim;
    private String rideId;
    private String pickup;
    private String dropoff;
    private String vehicleType;
    private int fare;
    private double dropoffLat;
    private double dropoffLng;
    private boolean driverFound = false;
    private boolean cancelled   = false;
    private boolean dispatched  = false;

    // Simulated driver data (generated once)
    private String simDriverName;
    private String simVehicleNumber;
    private String simVehicleModel;
    private float  simRating;
    private int    simEtaMinutes;

    // ── Factory ───────────────────────────────────────────────────────────────
    public static RideSearchingFragment newInstance(String rideId, String pickup, String dropoff,
                                                    String vehicleType, int fare,
                                                    double pickupLat, double pickupLng,
                                                    double dropoffLat, double dropoffLng) {
        Bundle args = new Bundle();
        args.putString(KEY_RIDE_ID, rideId);
        args.putString(KEY_PICKUP,  pickup);
        args.putString(KEY_DROPOFF, dropoff);
        args.putString(KEY_VEHICLE, vehicleType);
        args.putInt(KEY_FARE,       fare);
        args.putDouble(KEY_PLAT,    pickupLat);
        args.putDouble(KEY_PLNG,    pickupLng);
        args.putDouble(KEY_DLAT,    dropoffLat);
        args.putDouble(KEY_DLNG,    dropoffLng);
        RideSearchingFragment f = new RideSearchingFragment();
        f.setArguments(args);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSearchingActionListener) {
            listener = (OnSearchingActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ride_searching, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Parse arguments
        Bundle args = requireArguments();
        rideId      = args.getString(KEY_RIDE_ID, "");
        pickup      = args.getString(KEY_PICKUP,  "");
        dropoff     = args.getString(KEY_DROPOFF, "");
        vehicleType = args.getString(KEY_VEHICLE, "Cab");
        fare        = args.getInt(KEY_FARE, 0);
        pickupLatLng= new LatLng(args.getDouble(KEY_PLAT, 28.6139), args.getDouble(KEY_PLNG, 77.2090));
        dropoffLat  = args.getDouble(KEY_DLAT, 28.62);
        dropoffLng  = args.getDouble(KEY_DLNG, 77.24);

        progressSearching = view.findViewById(R.id.progress_searching);
        imgFoundTick      = view.findViewById(R.id.img_driver_found_tick);
        textStatusTitle   = view.findViewById(R.id.text_status_title);
        textStatusSub     = view.findViewById(R.id.text_status_sub);
        imgVehicleIcon    = view.findViewById(R.id.img_vehicle_icon);
        cardDriverFound   = view.findViewById(R.id.card_driver_found);
        dividerDriver     = view.findViewById(R.id.divider_driver);
        textDriverInitials= view.findViewById(R.id.text_driver_initials);
        textDriverName    = view.findViewById(R.id.text_driver_name);
        textDriverRating  = view.findViewById(R.id.text_driver_rating);
        textVehicleDetails= view.findViewById(R.id.text_vehicle_details);
        textDriverEta     = view.findViewById(R.id.text_driver_eta);
        textRoutePickup   = view.findViewById(R.id.text_route_pickup);
        textRouteDropoff  = view.findViewById(R.id.text_route_dropoff);
        textSearchFare    = view.findViewById(R.id.text_search_fare);
        buttonCancelSearch= view.findViewById(R.id.button_cancel_search);

        // Fill static info
        textRoutePickup.setText(pickup);
        textRouteDropoff.setText(dropoff);
        textSearchFare.setText("₹" + fare);
        setVehicleIcon(vehicleType);

        // Map
        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.searching_map);
        if (mapFrag != null) mapFrag.getMapAsync(this);

        // Cancel button
        buttonCancelSearch.setOnClickListener(v -> promptCancelSearch());

        // Intercept back: don't quietly drop the ride
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        promptCancelSearch();
                    }
                });

        // Generate simulated driver ahead of time
        generateDriverData();

        // Start the search simulation
        handler = new Handler(Looper.getMainLooper());
        startVehicleIconAnimation();
        startCircleAnimation();
        long searchDelay = SEARCH_MIN_MS
                + (long) (Math.random() * (SEARCH_MAX_MS - SEARCH_MIN_MS));
        handler.postDelayed(this::revealDriver, searchDelay);
    }

    // ── Search animation ──────────────────────────────────────────────────────
    private void startVehicleIconAnimation() {
        if (imgVehicleIcon == null) return;
        float drift = dpToPx(8);
        android.animation.ObjectAnimator translate = android.animation.ObjectAnimator.ofFloat(
                imgVehicleIcon, "translationX", -drift, drift);
        translate.setDuration(900);
        translate.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        translate.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        translate.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        android.animation.ObjectAnimator pulseX = android.animation.ObjectAnimator.ofFloat(
                imgVehicleIcon, "scaleX", 1f, 1.12f);
        pulseX.setDuration(900);
        pulseX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulseX.setRepeatMode(android.animation.ValueAnimator.REVERSE);

        android.animation.ObjectAnimator pulseY = android.animation.ObjectAnimator.ofFloat(
                imgVehicleIcon, "scaleY", 1f, 1.12f);
        pulseY.setDuration(900);
        pulseY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulseY.setRepeatMode(android.animation.ValueAnimator.REVERSE);

        vehicleIconAnim = new android.animation.AnimatorSet();
        vehicleIconAnim.playTogether(translate, pulseX, pulseY);
        vehicleIconAnim.start();
    }

    private void stopVehicleIconAnimation() {
        if (vehicleIconAnim != null) {
            vehicleIconAnim.cancel();
            vehicleIconAnim = null;
        }
        if (imgVehicleIcon != null) {
            imgVehicleIcon.setTranslationX(0f);
            imgVehicleIcon.setScaleX(1f);
            imgVehicleIcon.setScaleY(1f);
        }
    }

    private void startCircleAnimation() {
        final float[] angle = {0f};
        circleRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || cancelled) return;
                angle[0] += 10f;
                if (driverMarker != null) {
                    double lat = pickupLatLng.latitude  + 0.013 * Math.sin(Math.toRadians(angle[0]));
                    double lng = pickupLatLng.longitude + 0.013 * Math.cos(Math.toRadians(angle[0]));
                    driverMarker.setPosition(new LatLng(lat, lng));
                }
                handler.postDelayed(this, 180);
            }
        };
        handler.post(circleRunnable);
    }

    // ── Driver revealed ───────────────────────────────────────────────────────
    private void revealDriver() {
        if (!isAdded() || cancelled) return;
        driverFound = true;
        handler.removeCallbacks(circleRunnable);
        stopVehicleIconAnimation();

        // Firebase update
        if (!rideId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("rides")
                .child(rideId).child("status").setValue("driver_assigned");
            FirebaseDatabase.getInstance().getReference("rides")
                .child(rideId).child("driverName").setValue(simDriverName);
            FirebaseDatabase.getInstance().getReference("rides")
                .child(rideId).child("vehicleModel").setValue(simVehicleModel);
        }

        // Update header
        textStatusTitle.setText("Driver Found!");
        textStatusSub.setText(simDriverName + " is heading to you");
        progressSearching.setVisibility(View.GONE);
        imgFoundTick.setVisibility(View.VISIBLE);

        // Update cancel button text
        buttonCancelSearch.setText("Cancel (Free)");

        // Populate driver card
        textDriverInitials.setText(String.valueOf(simDriverName.charAt(0)));
        textDriverName.setText(simDriverName);
        textDriverRating.setText(String.format("%.1f ★", simRating));
        textVehicleDetails.setText(simVehicleModel + "  ·  " + simVehicleNumber);
        textDriverEta.setText(simEtaMinutes + " min");

        // Slide in driver card
        cardDriverFound.setVisibility(View.VISIBLE);
        dividerDriver.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(400);
        cardDriverFound.startAnimation(fadeIn);

        // Move driver marker close to pickup
        if (driverMarker != null) {
            driverMarker.setPosition(new LatLng(
                pickupLatLng.latitude  + 0.007,
                pickupLatLng.longitude + 0.007));
        }

        // Auto-advance to active ride
        handler.postDelayed(this::advanceToActiveRide, FOUND_PAUSE_MS);
    }

    private void advanceToActiveRide() {
        if (!isAdded() || cancelled || dispatched) return;
        dispatched = true;
        if (listener != null) {
            listener.onDriverFound(rideId, pickup, dropoff, vehicleType, fare,
                simDriverName, simVehicleNumber, simVehicleModel, simRating,
                simEtaMinutes, pickupLatLng.latitude, pickupLatLng.longitude,
                dropoffLat, dropoffLng);
        }
    }

    // ── Cancel ────────────────────────────────────────────────────────────────
    private void promptCancelSearch() {
        if (cancelled || dispatched) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(driverFound ? "Cancel Ride?" : "Cancel Search?")
                .setMessage(driverFound
                        ? "Your driver is already assigned. Cancellation is free at this stage."
                        : "Stop looking for a driver? Your fare will be refunded.")
                .setPositiveButton("Yes, Cancel", (d, w) -> performCancel())
                .setNegativeButton(driverFound ? "Keep Ride" : "Keep Searching", null)
                .show();
    }

    private void performCancel() {
        if (cancelled || dispatched) return;
        cancelled = true;
        dispatched = true;
        handler.removeCallbacksAndMessages(null);

        if (!rideId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("rides")
                .child(rideId).child("status").setValue("cancelled");
        }

        if (listener != null) listener.onSearchCancelled(rideId, fare);
    }

    // ── Map ───────────────────────────────────────────────────────────────────
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        try {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
        } catch (Exception e) {
            e.printStackTrace();
        }
        map.getUiSettings().setZoomControlsEnabled(false);
        // Allow the rider to pan/zoom while we're searching for a driver.
        map.getUiSettings().setZoomGesturesEnabled(true);
        map.getUiSettings().setScrollGesturesEnabled(true);
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.getUiSettings().setTiltGesturesEnabled(false);

        LatLng dropoffLatLng = new LatLng(dropoffLat, dropoffLng);

        // Pickup (green)
        map.addMarker(new MarkerOptions()
            .position(pickupLatLng).title("Pickup")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Drop-off (red)
        map.addMarker(new MarkerOptions()
            .position(dropoffLatLng).title("Drop-off")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Placeholder curve drawn immediately; replaced with real OSRM road
        // route once the async fetch returns.
        List<LatLng> placeholder =
                com.goguardian.util.RouteFetcher.curvedFallback(pickupLatLng, dropoffLatLng);
        Polyline routeLine = map.addPolyline(new PolylineOptions()
            .addAll(placeholder)
            .width(10f).color(0xFF4A8CFF).geodesic(false)
            .jointType(com.google.android.gms.maps.model.JointType.ROUND));

        com.goguardian.util.RouteFetcher.fetch(pickupLatLng, dropoffLatLng, path -> {
            if (!isAdded() || googleMap == null) return;
            routeLine.setPoints(path);
            LatLngBounds.Builder b = new LatLngBounds.Builder()
                    .include(pickupLatLng).include(dropoffLatLng);
            for (LatLng p : path) b.include(p);
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 160));
        });

        // Scatter several nearby drivers around pickup using the icon for the
        // booked vehicle type. Gives the "searching" screen the feel of an
        // actual map of available rides.
        com.google.android.gms.maps.model.BitmapDescriptor vehicleIcon =
                com.goguardian.util.MarkerIconUtil.forVehicle(requireContext(), vehicleType, 44);
        java.util.Random rnd = new java.util.Random();
        int driverCount = 4 + rnd.nextInt(3); // 4..6
        Marker firstNearby = null;
        for (int i = 0; i < driverCount; i++) {
            // ~0.003–0.012 deg offset (~330m–1.3km) in random direction
            double angle = rnd.nextDouble() * 2 * Math.PI;
            double radius = 0.003 + rnd.nextDouble() * 0.009;
            LatLng pos = new LatLng(
                    pickupLatLng.latitude + Math.sin(angle) * radius,
                    pickupLatLng.longitude + Math.cos(angle) * radius);
            Marker m = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .icon(vehicleIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation((float) (rnd.nextDouble() * 360.0))
                    .title("Available driver"));
            if (firstNearby == null) firstNearby = m;
        }
        // Keep one of the random drivers as the "incoming" driver marker so
        // existing code that updates driverMarker still works.
        driverMarker = firstNearby != null
                ? firstNearby
                : map.addMarker(new MarkerOptions()
                        .position(new LatLng(pickupLatLng.latitude + 0.013, pickupLatLng.longitude))
                        .icon(vehicleIcon).anchor(0.5f, 0.5f).flat(true)
                        .title("Driver"));

        // Camera padding
        map.setPadding(0, 0, 0, dpToPx(320));
        LatLngBounds bounds = new LatLngBounds.Builder()
            .include(pickupLatLng).include(dropoffLatLng).build();
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 160));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void generateDriverData() {
        simDriverName    = DRIVER_NAMES[(int)(Math.random() * DRIVER_NAMES.length)];
        simVehicleNumber = VEHICLE_NUMBERS[(int)(Math.random() * VEHICLE_NUMBERS.length)];
        simRating        = RATINGS[(int)(Math.random() * RATINGS.length)];
        simEtaMinutes    = 5 + (int)(Math.random() * 9);

        switch (vehicleType) {
            case "Bike": simVehicleModel = BIKE_MODELS[(int)(Math.random() * BIKE_MODELS.length)]; break;
            case "Auto": simVehicleModel = AUTO_MODELS[(int)(Math.random() * AUTO_MODELS.length)]; break;
            default:     simVehicleModel = CAB_MODELS[(int)(Math.random() * CAB_MODELS.length)];   break;
        }
    }

    private void setVehicleIcon(String type) {
        switch (type) {
            case "Bike": imgVehicleIcon.setImageResource(R.drawable.ic_motorcycle);    break;
            case "Auto": imgVehicleIcon.setImageResource(R.drawable.ic_auto_rickshaw); break;
            default:     imgVehicleIcon.setImageResource(R.drawable.ic_car_side);      break;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        stopVehicleIconAnimation();
    }
}
