package com.goguardian.ui.rider;

import com.goguardian.util.HapticUtils;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

import com.google.android.gms.maps.model.MapStyleOptions;

public class RiderHomeFragment extends Fragment implements OnMapReadyCallback {

    public interface OnRiderActionListener {
        void onOpenBooking();
        void onOpenProfile();
        void onLogoutRequested();
        void onSosRequested();
        void onMoreServicesRequested();
    }

    private OnRiderActionListener listener;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;

    private TextView textGreeting;
    private TextView textUserName;
    private TextView textWalletBalance;
    private TextView textRidesCount;

    private com.google.firebase.database.DatabaseReference homeUserRef;
    private com.google.firebase.database.Query homeRidesQuery;
    private com.google.firebase.database.Query broadcastQuery;
    private ValueEventListener homeUserListener;
    private ValueEventListener homeRidesListener;
    private ValueEventListener broadcastListener;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof OnRiderActionListener) {
            listener = (OnRiderActionListener) context;
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rider_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        textGreeting = view.findViewById(R.id.text_greeting);
        textUserName = view.findViewById(R.id.text_user_name);
        textWalletBalance = view.findViewById(R.id.text_wallet_balance);
        textRidesCount = view.findViewById(R.id.text_rides_count);

        loadUserData();
        setupBroadcastListener(view);

        View bookButton = view.findViewById(R.id.button_book_ride_now);
        View profileButton = view.findViewById(R.id.button_open_profile);
        View logoutButton = view.findViewById(R.id.button_logout);
        View fabSos = view.findViewById(R.id.fab_sos_home);
        View cardSosQuick = view.findViewById(R.id.card_sos_quick);
        View cardServices = view.findViewById(R.id.card_more_services);

        bookButton.setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onOpenBooking();
        });
        profileButton.setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onOpenProfile();
        });
        logoutButton.setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onLogoutRequested();
        });
        fabSos.setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onSosRequested();
        });
        cardSosQuick.setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onSosRequested();
        });
        cardServices.setOnClickListener(v -> {
            HapticUtils.feedback(v);
            if (listener != null) listener.onMoreServicesRequested();
        });
    }

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
        map.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 15f));
                    } else {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(28.6139, 77.2090), 12f));
                    }
                }
            });
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(28.6139, 77.2090), 12f));
            requestPermissions(new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION }, 1001);
        }
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        homeUserRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
        homeUserListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                String name = snapshot.child("name").getValue(String.class);
                Long balance = snapshot.child("balance").getValue(Long.class);
                if (name != null && !name.isEmpty()) {
                    textUserName.setText(name.split(" ")[0]);
                    textGreeting.setText(getTimeBasedGreeting());
                }
                textWalletBalance.setText(balance != null ? "₹" + String.format(java.util.Locale.US, "%,d", balance) : "₹0");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        homeUserRef.addValueEventListener(homeUserListener);

        final String uid = user.getUid();
        homeRidesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                long completed = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String rideUid = child.child("riderUid").getValue(String.class);
                    if (rideUid == null || !rideUid.equals(uid)) continue;
                    if ("completed".equals(child.child("status").getValue(String.class))) {
                        completed++;
                    }
                }
                if (completed == 0) {
                    textRidesCount.setText("No rides yet");
                } else {
                    textRidesCount.setText(completed + (completed == 1 ? " ride completed" : " rides completed"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        // Read the whole /rides node and filter on the client. orderByChild +
        // equalTo silently returns no data when ".indexOn" isn't set on the
        // server, which made the home count stick at "No rides yet".
        homeRidesQuery = FirebaseDatabase.getInstance().getReference("rides");
        homeRidesQuery.addValueEventListener(homeRidesListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (homeUserRef != null && homeUserListener != null) {
            homeUserRef.removeEventListener(homeUserListener);
        }
        if (homeRidesQuery != null && homeRidesListener != null) {
            homeRidesQuery.removeEventListener(homeRidesListener);
        }
        if (broadcastQuery != null && broadcastListener != null) {
            broadcastQuery.removeEventListener(broadcastListener);
        }
    }

    private String getTimeBasedGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning) + ",";
        if (hour < 17) return getString(R.string.greeting_afternoon) + ",";
        return getString(R.string.greeting_evening) + ",";
    }

    private void setupBroadcastListener(View view) {
        broadcastQuery = FirebaseDatabase.getInstance().getReference("broadcasts").limitToLast(1);
        broadcastListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || !snapshot.exists())
                    return;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String message = child.child("message").getValue(String.class);
                    if (message != null) {
                        com.google.android.material.snackbar.Snackbar.make(view,
                                "📢 " + message,
                                com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                                .setAction("Dismiss", v -> {
                                })
                                .show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        broadcastQuery.addValueEventListener(broadcastListener);
    }
}
