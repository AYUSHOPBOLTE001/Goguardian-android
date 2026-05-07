package com.goguardian.ui.rider;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import android.view.inputmethod.EditorInfo;

import com.google.android.gms.maps.model.Polyline;

import com.google.android.gms.maps.model.MapStyleOptions;

public class RideBookingFragment extends Fragment implements OnMapReadyCallback {

    // ── Navigation callback ──────────────────────────────────────────────────
    public interface OnBookingActionListener {
        void onRideBooked(String rideId, String pickup, String dropoff,
                String vehicleType, int fare,
                double pickupLat, double pickupLng,
                double dropoffLat, double dropoffLng);
    }

    // ── Constants ────────────────────────────────────────────────────────────
    private static final LatLng DEFAULT_DELHI = new LatLng(28.6139, 77.2090);

    private static final Map<String, LatLng> LOCATION_COORDS = new java.util.LinkedHashMap<String, LatLng>() {{
        put("Connaught Place, New Delhi",                  new LatLng(28.6289, 77.2091));
        put("Indira Gandhi International Airport, Delhi",  new LatLng(28.5562, 77.1000));
        put("New Delhi Railway Station",                   new LatLng(28.6431, 77.2197));
        put("Old Delhi Railway Station",                   new LatLng(28.6611, 77.2277));
        put("Hazrat Nizamuddin Railway Station",           new LatLng(28.5896, 77.2531));
        put("Anand Vihar ISBT, Delhi",                     new LatLng(28.6471, 77.3160));
        put("Kashmere Gate ISBT, Delhi",                   new LatLng(28.6675, 77.2306));
        put("Hauz Khas Village, New Delhi",                new LatLng(28.5494, 77.2001));
        put("India Gate, New Delhi",                       new LatLng(28.6129, 77.2295));
        put("Red Fort, Delhi",                             new LatLng(28.6562, 77.2410));
        put("Qutub Minar, New Delhi",                      new LatLng(28.5245, 77.1855));
        put("Gurugram Cyber City, Haryana",                new LatLng(28.4949, 77.0886));
        put("DLF Mall of India, Noida",                    new LatLng(28.5672, 77.3211));
        put("Akshardham Temple, Delhi",                    new LatLng(28.6127, 77.2773));
        put("Lotus Temple, New Delhi",                     new LatLng(28.5535, 77.2588));
        put("Karol Bagh, New Delhi",                       new LatLng(28.6519, 77.1909));
        put("Chandni Chowk, Delhi",                        new LatLng(28.6506, 77.2303));
        put("Khan Market, New Delhi",                      new LatLng(28.5994, 77.2273));
        put("Saket, New Delhi",                            new LatLng(28.5244, 77.2066));
        put("Vasant Kunj, New Delhi",                      new LatLng(28.5244, 77.1576));
        put("Dwarka Sector 21, New Delhi",                 new LatLng(28.5521, 77.0586));
        put("Janakpuri, New Delhi",                        new LatLng(28.6219, 77.0856));
        put("Rajouri Garden, New Delhi",                   new LatLng(28.6492, 77.1207));
        put("Lajpat Nagar, New Delhi",                     new LatLng(28.5708, 77.2434));
        put("Greater Kailash, New Delhi",                  new LatLng(28.5419, 77.2434));
        put("Nehru Place, New Delhi",                      new LatLng(28.5495, 77.2540));
        put("Rohini Sector 18, Delhi",                     new LatLng(28.7376, 77.0688));
        put("Pitampura, Delhi",                            new LatLng(28.6996, 77.1310));
        put("Mayur Vihar Phase 1, Delhi",                  new LatLng(28.6094, 77.2940));
        put("Laxmi Nagar, Delhi",                          new LatLng(28.6353, 77.2780));
        put("AIIMS Hospital, New Delhi",                   new LatLng(28.5672, 77.2100));
        put("Safdarjung Hospital, New Delhi",              new LatLng(28.5685, 77.2086));
        put("Max Hospital, Saket",                         new LatLng(28.5286, 77.2155));
        put("Select Citywalk Mall, Saket",                 new LatLng(28.5286, 77.2197));
        put("Ambience Mall, Gurugram",                     new LatLng(28.5045, 77.0966));
        put("DLF Cyber Hub, Gurugram",                     new LatLng(28.4955, 77.0890));
        put("Sector 18 Market, Noida",                     new LatLng(28.5705, 77.3260));
        put("DND Flyway, Noida",                           new LatLng(28.5757, 77.3022));
        put("Indirapuram, Ghaziabad",                      new LatLng(28.6362, 77.3700));
        put("Faridabad Sector 15, Haryana",                new LatLng(28.4040, 77.3107));
        put("IIT Delhi, Hauz Khas",                        new LatLng(28.5450, 77.1926));
        put("JNU, New Delhi",                              new LatLng(28.5402, 77.1662));
        put("Delhi University North Campus",               new LatLng(28.6889, 77.2079));
        put("Pragati Maidan, New Delhi",                   new LatLng(28.6160, 77.2418));
        put("Jama Masjid, Delhi",                          new LatLng(28.6507, 77.2334));
        put("Humayun's Tomb, Delhi",                       new LatLng(28.5933, 77.2507));
        put("Rashtrapati Bhavan, New Delhi",               new LatLng(28.6143, 77.1996));
        put("Parliament House, New Delhi",                 new LatLng(28.6172, 77.2082));
    }};

    // Per-km rates (base fare + per km)
    private static final int CAB_BASE = 50;
    private static final int CAB_RATE = 15;
    private static final int BIKE_BASE = 25;
    private static final int BIKE_RATE = 8;
    private static final int AUTO_BASE = 35;
    private static final int AUTO_RATE = 11;

    // ── Fields ───────────────────────────────────────────────────────────────
    private OnBookingActionListener actionListener;
    private GoogleMap googleMap;
    private DatabaseReference userRef;
    private DatabaseReference balanceRef;
    private ValueEventListener balanceListener;
    private long currentBalance = 0;
    private FusedLocationProviderClient fusedLocationClient;

    // Views
    private MaterialAutoCompleteTextView inputPickup;
    private MaterialAutoCompleteTextView inputDropoff;
    private MaterialButton buttonEstimate;
    private View sectionVehicles;
    private MaterialCardView cardCab;
    private MaterialCardView cardBike;
    private MaterialCardView cardAuto;
    private TextView textCabFare;
    private TextView textBikeFare;
    private TextView textAutoFare;
    private TextView textCabEta;
    private TextView textBikeEta;
    private TextView textAutoEta;
    private MaterialButton buttonBook;
    private com.google.android.material.textfield.TextInputEditText inputBidPrice;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // State
    private String selectedVehicle = null;
    private int selectedFare = 0;
    private int selectedEtaMin = 0;
    private int simulatedDistanceKm = 0;
    private double dropoffLat = 0;
    private double dropoffLng = 0;
    private int cabEtaMin, bikeEtaMin, autoEtaMin;
    private LatLng currentLatLng = DEFAULT_DELHI;
    private Polyline currentRoute = null;
    private LatLng pickupSelectedLatLng = null;
    private LatLng dropoffSelectedLatLng = null;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (googleMap != null) {
                        enableMyLocation();
                    }
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnBookingActionListener) {
            actionListener = (OnBookingActionListener) context;
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ride_booking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Listen for user balance
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            balanceRef = userRef.child("balance");
            balanceListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!isAdded())
                        return;
                    Long val = snapshot.getValue(Long.class);
                    currentBalance = val != null ? val : 0;
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };
            balanceRef.addValueEventListener(balanceListener);
        }

        // Map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.booking_map);
        if (mapFragment != null)
            mapFragment.getMapAsync(this);

        // Bind views
        inputPickup = view.findViewById(R.id.input_pickup);
        inputDropoff = view.findViewById(R.id.input_dropoff);
        buttonEstimate = view.findViewById(R.id.button_estimate);
        sectionVehicles = view.findViewById(R.id.section_vehicles);
        cardCab = view.findViewById(R.id.card_cab);
        cardBike = view.findViewById(R.id.card_bike);
        cardAuto = view.findViewById(R.id.card_auto);
        textCabFare = view.findViewById(R.id.text_cab_fare);
        textBikeFare = view.findViewById(R.id.text_bike_fare);
        textAutoFare = view.findViewById(R.id.text_auto_fare);
        textCabEta = view.findViewById(R.id.text_cab_eta);
        textBikeEta = view.findViewById(R.id.text_bike_eta);
        textAutoEta = view.findViewById(R.id.text_auto_eta);
        buttonBook = view.findViewById(R.id.button_book);
        inputBidPrice = view.findViewById(R.id.input_bid_price);

        // Setup BottomSheet
        View bottomSheet = view.findViewById(R.id.bottom_sheet_booking);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        setupLocationSuggestions();

        // Handle Enter/Next keys
        inputPickup.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                inputDropoff.requestFocus();
                return true;
            }
            return false;
        });

        inputDropoff.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                estimateFare();
                return true;
            }
            return false;
        });

        // Current location suggestion
        inputPickup.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && (inputPickup.getText() == null || inputPickup.getText().toString().isEmpty())) {
                inputPickup.setText(getString(R.string.current_location));
            }
        });

        // Invalidate cached coords whenever the user edits the field — otherwise a
        // previously tapped suggestion shadows the new typed address. Item-click
        // listener runs after the watcher, so dropdown selections still set the cache.
        inputPickup.addTextChangedListener(new SimpleTextWatcher(() -> pickupSelectedLatLng = null));
        inputDropoff.addTextChangedListener(new SimpleTextWatcher(() -> dropoffSelectedLatLng = null));

        buttonEstimate.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            estimateFare();
        });

        // Vehicle card clicks
        cardCab.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            selectVehicle("Cab", parseFare(textCabFare), cabEtaMin);
        });
        cardBike.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            selectVehicle("Bike", parseFare(textBikeFare), bikeEtaMin);
        });
        cardAuto.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            selectVehicle("Auto", parseFare(textAutoFare), autoEtaMin);
        });

        // Book button
        buttonBook.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedbackSuccess(v);
            bookRide(user);
        });
    }

    // ── Fare estimation ──────────────────────────────────────────────────────
    private void estimateFare() {
        String pickup = text(inputPickup);
        String dropoff = text(inputDropoff);

        if (pickup.isEmpty()) {
            inputPickup.setError(getString(R.string.error_enter_pickup));
            return;
        }
        if (dropoff.isEmpty()) {
            inputDropoff.setError(getString(R.string.error_enter_dropoff));
            return;
        }

        // Hide keyboard
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        // Try sync resolution first (current location, cached suggestion tap, or
        // exact match in the hardcoded list). Anything else falls through to
        // Geocoder on a background thread.
        LatLng syncPickup = resolveSync(pickup, pickupSelectedLatLng, true);
        LatLng syncDropoff = resolveSync(dropoff, dropoffSelectedLatLng, false);

        if (syncPickup != null && syncDropoff != null) {
            completeEstimate(syncPickup, syncDropoff);
            return;
        }

        // Async geocode path. Keep the Estimate button visible (in a loading state)
        // so the bottom sheet still has measurable content — otherwise it expands
        // to drag-handle height and never grows back when the vehicles section
        // appears.
        final CharSequence originalLabel = buttonEstimate.getText();
        buttonEstimate.setEnabled(false);
        buttonEstimate.setText("Finding location…");
        new Thread(() -> {
            LatLng p = syncPickup != null ? syncPickup : geocode(pickup);
            LatLng d = syncDropoff != null ? syncDropoff : geocode(dropoff);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                buttonEstimate.setEnabled(true);
                buttonEstimate.setText(originalLabel);
                if (p == null) {
                    inputPickup.setError("Couldn't find this pickup location");
                    return;
                }
                if (d == null) {
                    inputDropoff.setError("Couldn't find this drop-off location");
                    return;
                }
                completeEstimate(p, d);
            });
        }).start();
    }

    private LatLng resolveSync(String typed, LatLng cached, boolean isPickup) {
        if (isPickup && typed.equalsIgnoreCase(getString(R.string.current_location))) {
            return currentLatLng;
        }
        if (cached != null) return cached;
        LatLng exact = LOCATION_COORDS.get(typed);
        if (exact != null) return exact;
        for (Map.Entry<String, LatLng> e : LOCATION_COORDS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(typed)) return e.getValue();
        }
        return null;
    }

    private LatLng geocode(String query) {
        if (!Geocoder.isPresent()) return null;
        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            // Bias the search around the rider's current area so "MG Road" resolves
            // to the local one, not one across the country.
            List<Address> results = null;
            if (currentLatLng != null) {
                double lat = currentLatLng.latitude;
                double lng = currentLatLng.longitude;
                results = geocoder.getFromLocationName(query, 1,
                        lat - 0.5, lng - 0.5, lat + 0.5, lng + 0.5);
            }
            if (results == null || results.isEmpty()) {
                results = geocoder.getFromLocationName(query, 1);
            }
            if (results != null && !results.isEmpty()) {
                Address a = results.get(0);
                return new LatLng(a.getLatitude(), a.getLongitude());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void completeEstimate(LatLng resolvedPickup, LatLng resolvedDropoff) {
        currentLatLng = resolvedPickup;
        pickupSelectedLatLng = resolvedPickup;
        dropoffSelectedLatLng = resolvedDropoff;
        dropoffLat = resolvedDropoff.latitude;
        dropoffLng = resolvedDropoff.longitude;

        float[] results = new float[1];
        Location.distanceBetween(currentLatLng.latitude, currentLatLng.longitude, dropoffLat, dropoffLng, results);
        simulatedDistanceKm = Math.round(results[0] / 1000f);
        if (simulatedDistanceKm < 1) simulatedDistanceKm = 2;

        int cabFare = CAB_BASE + simulatedDistanceKm * CAB_RATE;
        int bikeFare = BIKE_BASE + simulatedDistanceKm * BIKE_RATE;
        int autoFare = AUTO_BASE + simulatedDistanceKm * AUTO_RATE;

        cabEtaMin = 5 + (int) (Math.random() * 8);
        autoEtaMin = 4 + (int) (Math.random() * 7);
        bikeEtaMin = 3 + (int) (Math.random() * 6);

        textCabFare.setText(getString(R.string.currency_symbol, cabFare));
        textBikeFare.setText(getString(R.string.currency_symbol, bikeFare));
        textAutoFare.setText(getString(R.string.currency_symbol, autoFare));
        textCabEta.setText(getString(R.string.min_label, cabEtaMin));
        textBikeEta.setText(getString(R.string.min_label, bikeEtaMin));
        textAutoEta.setText(getString(R.string.min_label, autoEtaMin));

        sectionVehicles.setVisibility(View.VISIBLE);
        buttonBook.setVisibility(View.GONE);
        selectedVehicle = null;
        resetCardHighlights();

        // Stagger a pop-in for each vehicle card so the menu lands with some life
        // instead of a hard cut.
        animateCardIn(cardCab, 0);
        animateCardIn(cardBike, 70);
        animateCardIn(cardAuto, 140);

        // Hide the Estimate button and expand the sheet AFTER section_vehicles is
        // visible — otherwise the sheet measures with no content and won't grow
        // back when we reveal the vehicle cards.
        buttonEstimate.setVisibility(View.GONE);
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        if (googleMap != null) updateMapWithRoute();
    }

    private void animateCardIn(View card, long delayMs) {
        android.view.animation.Animation anim = android.view.animation.AnimationUtils
                .loadAnimation(requireContext(), R.anim.card_pop_in);
        anim.setStartOffset(delayMs);
        card.startAnimation(anim);
    }

    // Minimal TextWatcher that just runs a callback on every change.
    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onChange;
        SimpleTextWatcher(Runnable onChange) { this.onChange = onChange; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }

    private void updateMapWithRoute() {
        googleMap.clear();
        LatLng pickupLatLng = currentLatLng;
        LatLng dropoffLatLng = new LatLng(dropoffLat, dropoffLng);

        googleMap.addMarker(new MarkerOptions()
                .position(pickupLatLng).title("Pickup")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        googleMap.addMarker(new MarkerOptions()
                .position(dropoffLatLng).title("Drop-off")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Draw a placeholder curve immediately, then swap in the real OSRM
        // route as soon as it arrives so the user never sees an empty map.
        java.util.List<LatLng> placeholder =
                com.goguardian.util.RouteFetcher.curvedFallback(pickupLatLng, dropoffLatLng);
        Polyline line = googleMap.addPolyline(new PolylineOptions()
                .addAll(placeholder).width(10f).color(0xFF4A8CFF).geodesic(false)
                .jointType(com.google.android.gms.maps.model.JointType.ROUND));

        com.goguardian.util.RouteFetcher.fetch(pickupLatLng, dropoffLatLng, path -> {
            if (!isAdded() || googleMap == null) return;
            line.setPoints(path);
            LatLngBounds.Builder b = new LatLngBounds.Builder()
                    .include(pickupLatLng).include(dropoffLatLng);
            for (LatLng p : path) b.include(p);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 160));
        });
    }

    // ── Vehicle selection ─────────────────────────────────────────────────────
    private void selectVehicle(String type, int fare, int etaMin) {
        if (fare <= 0) {
            Toast.makeText(requireContext(), "Tap 'Estimate Fare' first", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedVehicle = type;
        selectedFare = fare;
        selectedEtaMin = etaMin;

        resetCardHighlights();

        // Highlight the selected card
        MaterialCardView target = null;
        switch (type) {
            case "Cab":
                target = cardCab;
                break;
            case "Bike":
                target = cardBike;
                break;
            case "Auto":
                target = cardAuto;
                break;
        }
        if (target != null) {
            target.setStrokeColor(getResources().getColor(R.color.primary, null));
            target.setStrokeWidth(dpToPx(2));
            target.setCardBackgroundColor(getResources().getColor(R.color.primary_container, null));
        }

        // Update book button
        String label;
        switch (type) {
            case "Cab":
                label = getString(R.string.ride_type_cab);
                break;
            case "Bike":
                label = getString(R.string.ride_type_bike);
                break;
            default:
                label = getString(R.string.ride_type_auto);
                break;
        }
        buttonBook.setText(getString(R.string.booking_button_label, label, fare, etaMin));
        buttonBook.setVisibility(View.VISIBLE);
    }

    private void resetCardHighlights() {
        int strokeColor = getResources().getColor(R.color.border, null);
        int bgColor = getResources().getColor(R.color.surface, null);
        for (MaterialCardView card : new MaterialCardView[] { cardCab, cardBike, cardAuto }) {
            card.setStrokeColor(strokeColor);
            card.setStrokeWidth(dpToPx(1));
            card.setCardBackgroundColor(bgColor);
        }
    }

    // ── Booking ───────────────────────────────────────────────────────────────
    private void bookRide(FirebaseUser user) {
        if (selectedVehicle == null || selectedFare <= 0) {
            Toast.makeText(requireContext(), "Please select a vehicle type", Toast.LENGTH_SHORT).show();
            return;
        }
        if (user == null) {
            Toast.makeText(requireContext(), "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Apply optional bid override
        int finalFare = selectedFare;
        String bidStr = inputBidPrice != null && inputBidPrice.getText() != null
                ? inputBidPrice.getText().toString().trim() : "";
        if (!bidStr.isEmpty()) {
            try {
                int bid = Integer.parseInt(bidStr);
                int minBid = Math.max(1, (int) Math.round(selectedFare * 0.6));
                if (bid < minBid) {
                    Toast.makeText(requireContext(),
                            "Bid too low. Minimum ₹" + minBid + " for this trip.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                finalFare = bid;
            } catch (NumberFormatException ignored) {}
        }

        if (currentBalance < finalFare) {
            Toast.makeText(requireContext(),
                    getString(R.string.error_insufficient_wallet, finalFare, currentBalance),
                    Toast.LENGTH_LONG).show();
            return;
        }

        String pickup = text(inputPickup);
        String dropoff = text(inputDropoff);

        // Deduct fare from wallet immediately
        userRef.child("balance").setValue(currentBalance - finalFare);

        // Create ride record in Firebase
        DatabaseReference ridesRef = FirebaseDatabase.getInstance().getReference("rides").push();
        String rideId = ridesRef.getKey();

        long pickupTime = System.currentTimeMillis();
        long dropoffTime = pickupTime + (long) selectedEtaMin * 60_000L;

        Map<String, Object> rideData = new HashMap<>();
        rideData.put("riderUid", user.getUid());
        rideData.put("pickup", pickup);
        rideData.put("dropoff", dropoff);
        rideData.put("fare", finalFare);
        rideData.put("baseFare", selectedFare);
        rideData.put("vehicleType", selectedVehicle);
        rideData.put("distanceKm", simulatedDistanceKm);
        rideData.put("etaMin", selectedEtaMin);
        rideData.put("status", "searching");
        rideData.put("createdAt", pickupTime);
        rideData.put("pickupTime", pickupTime);
        rideData.put("dropoffTime", dropoffTime);
        rideData.put("pickupLat", currentLatLng.latitude);
        rideData.put("pickupLng", currentLatLng.longitude);
        rideData.put("dropoffLat", dropoffLat);
        rideData.put("dropoffLng", dropoffLng);
        ridesRef.setValue(rideData);

        // Navigate to searching screen
        if (actionListener != null) {
            actionListener.onRideBooked(rideId, pickup, dropoff,
                    selectedVehicle, finalFare,
                    currentLatLng.latitude, currentLatLng.longitude,
                    dropoffLat, dropoffLng);
        }
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
        map.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_DELHI, 13f));
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        googleMap.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
            } else {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_DELHI, 13f));
            }
        });
    }

    private void setupLocationSuggestions() {
        List<String> locations = new ArrayList<>(LOCATION_COORDS.keySet());

        // Each input gets its own adapter — sharing one breaks filtering when both
        // dropdowns try to publish results at the same time.
        inputPickup.setAdapter(new SubstringLocationAdapter(requireContext(), locations));
        inputDropoff.setAdapter(new SubstringLocationAdapter(requireContext(), locations));
        inputPickup.setThreshold(1);
        inputDropoff.setThreshold(1);

        inputPickup.setOnItemClickListener((parent, v, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            pickupSelectedLatLng = LOCATION_COORDS.get(selected);
        });

        inputDropoff.setOnItemClickListener((parent, v, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            dropoffSelectedLatLng = LOCATION_COORDS.get(selected);
        });
    }

    /**
     * Autocomplete adapter that filters by substring (case-insensitive) instead of
     * the default prefix-only match, so typing "airport" surfaces "Indira Gandhi
     * International Airport, Delhi". Matches that start with the query rank above
     * mid-string matches.
     */
    private static class SubstringLocationAdapter extends ArrayAdapter<String> {
        private final List<String> all;
        private List<String> filtered;

        SubstringLocationAdapter(Context ctx, List<String> source) {
            super(ctx, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(source));
            this.all = new ArrayList<>(source);
            this.filtered = new ArrayList<>(source);
        }

        @Override public int getCount() { return filtered.size(); }
        @Override @Nullable public String getItem(int position) { return filtered.get(position); }

        @NonNull
        @Override
        public android.widget.Filter getFilter() {
            return new android.widget.Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    List<String> out = new ArrayList<>();
                    if (constraint == null || constraint.length() == 0) {
                        out.addAll(all);
                    } else {
                        String q = constraint.toString().toLowerCase(Locale.ROOT).trim();
                        List<String> prefix = new ArrayList<>();
                        List<String> contains = new ArrayList<>();
                        for (String s : all) {
                            String low = s.toLowerCase(Locale.ROOT);
                            if (low.startsWith(q)) prefix.add(s);
                            else if (low.contains(q)) contains.add(s);
                        }
                        out.addAll(prefix);
                        out.addAll(contains);
                    }
                    FilterResults r = new FilterResults();
                    r.values = out;
                    r.count  = out.size();
                    return r;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filtered = results.values != null ? (List<String>) results.values : new ArrayList<>();
                    if (results.count > 0) notifyDataSetChanged();
                    else notifyDataSetInvalidated();
                }
            };
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String text(MaterialAutoCompleteTextView et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private int parseFare(TextView tv) {
        try {
            return Integer.parseInt(tv.getText().toString().replace("₹", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (balanceRef != null && balanceListener != null) {
            balanceRef.removeEventListener(balanceListener);
        }
    }
}
