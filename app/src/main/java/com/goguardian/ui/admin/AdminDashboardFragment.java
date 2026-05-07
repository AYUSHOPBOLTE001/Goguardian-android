package com.goguardian.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.goguardian.util.HapticUtils;

import java.util.HashMap;
import java.util.Map;

public class AdminDashboardFragment extends Fragment implements OnMapReadyCallback {

    public interface OnAdminActionListener {
        void onOpenAdminSection(int menuId);
        void onLogoutRequested();
    }

    private OnAdminActionListener listener;

    private TextView totalRidesText;
    private TextView liveRidesText;
    private TextView totalUsersText;
    private TextView activeAlertsText;

    private DatabaseReference rootRef;
    private ValueEventListener dashboardListener;
    private GoogleMap googleMap;
    private final Map<String, com.google.android.gms.maps.model.Marker> liveMarkers = new HashMap<>();

    // Latest snapshot held so onMapReady can apply it if data arrived before map was ready
    private DataSnapshot latestSnapshot = null;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof OnAdminActionListener) {
            listener = (OnAdminActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        totalRidesText   = view.findViewById(R.id.text_total_rides);
        liveRidesText    = view.findViewById(R.id.text_live_rides);
        totalUsersText   = view.findViewById(R.id.text_total_users);
        activeAlertsText = view.findViewById(R.id.text_active_alerts);

        view.findViewById(R.id.button_admin_rides).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_rides);
        });
        view.findViewById(R.id.button_admin_users).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_users);
        });
        view.findViewById(R.id.button_admin_claims).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_claims);
        });
        view.findViewById(R.id.button_admin_broadcast).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_broadcast);
        });
        view.findViewById(R.id.button_admin_logout).setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onLogoutRequested();
        });

        // Stat tiles → matching section
        view.findViewById(R.id.card_total_rides).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_rides);
        });
        view.findViewById(R.id.card_live_rides).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_rides);
        });
        view.findViewById(R.id.card_total_users).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_users);
        });
        view.findViewById(R.id.card_active_alerts).setOnClickListener(v -> {
            HapticUtils.feedback(v); openSection(R.id.admin_claims);
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.admin_live_map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Attach Firebase listener here, not inside onMapReady, so stats update independently
        rootRef = FirebaseDatabase.getInstance().getReference();
        dashboardListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                latestSnapshot = snapshot;
                applySnapshot(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        rootRef.addValueEventListener(dashboardListener);
    }

    private void applySnapshot(@NonNull DataSnapshot snapshot) {
        long totalUsers = snapshot.child("users").getChildrenCount();
        long totalRides = snapshot.child("rides").getChildrenCount();
        long liveRides  = 0;
        long activeAlerts = 0;

        if (googleMap != null) {
            for (com.google.android.gms.maps.model.Marker m : liveMarkers.values()) m.remove();
            liveMarkers.clear();
        }

        for (DataSnapshot ride : snapshot.child("rides").getChildren()) {
            String status = ride.child("status").getValue(String.class);
            if (status == null) continue;

            // Count all in-flight rides as "live" — matches statuses set throughout the ride flow
            switch (status.toLowerCase()) {
                case "active":
                case "ongoing":
                case "in_progress":
                case "driver_assigned":
                case "searching":
                    liveRides++;
                    break;
            }

            // Pin live rides on the map
            boolean isOnMap = status.equalsIgnoreCase("active")
                    || status.equalsIgnoreCase("ongoing")
                    || status.equalsIgnoreCase("in_progress")
                    || status.equalsIgnoreCase("driver_assigned");

            if (isOnMap && googleMap != null) {
                Double pLat = ride.child("pickupLat").getValue(Double.class);
                Double pLng = ride.child("pickupLng").getValue(Double.class);
                String vType = ride.child("vehicleType").getValue(String.class);
                if (pLat != null && pLng != null) {
                    float color = BitmapDescriptorFactory.HUE_GREEN;
                    if ("Bike".equalsIgnoreCase(vType))  color = BitmapDescriptorFactory.HUE_ORANGE;
                    if ("Cab".equalsIgnoreCase(vType))   color = BitmapDescriptorFactory.HUE_AZURE;
                    com.google.android.gms.maps.model.Marker marker = googleMap.addMarker(
                            new MarkerOptions()
                                    .position(new LatLng(pLat, pLng))
                                    .title((vType != null ? vType : "Ride") + " — " + status)
                                    .icon(BitmapDescriptorFactory.defaultMarker(color)));
                    if (marker != null) liveMarkers.put(ride.getKey(), marker);
                }
            }
        }

        // Active alerts = unresolved SOS logs (status absent or "open").
        for (DataSnapshot sos : snapshot.child("sos_logs").getChildren()) {
            String status = sos.child("status").getValue(String.class);
            if (status == null || "open".equalsIgnoreCase(status)) activeAlerts++;
        }

        if (totalUsersText   != null) totalUsersText.setText(String.valueOf(totalUsers));
        if (totalRidesText   != null) totalRidesText.setText(String.valueOf(totalRides));
        if (liveRidesText    != null) liveRidesText.setText(String.valueOf(liveRides));
        if (activeAlertsText != null) activeAlertsText.setText(String.valueOf(activeAlerts));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        try {
            googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
        } catch (Exception ignored) {}
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setScrollGesturesEnabled(true);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(28.6139, 77.2090), 10f));

        // If data already arrived before map was ready, apply it now
        if (latestSnapshot != null) applySnapshot(latestSnapshot);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (rootRef != null && dashboardListener != null) {
            rootRef.removeEventListener(dashboardListener);
        }
    }

    private void openSection(int menuId) {
        if (listener != null) listener.onOpenAdminSection(menuId);
    }
}
