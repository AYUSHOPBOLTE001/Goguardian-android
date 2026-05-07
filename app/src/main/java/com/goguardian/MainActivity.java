package com.goguardian;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.goguardian.data.DemoSessionManager;
import com.goguardian.ui.admin.AdminDashboardFragment;
import com.goguardian.ui.auth.OnboardingFragment;
import com.goguardian.ui.auth.SplashLoginFragment;
import com.goguardian.ui.auth.WelcomeFragment;
import com.goguardian.ui.rider.RideRatingFragment;
import com.goguardian.ui.common.ComingSoonFragment;
import com.goguardian.ui.common.RideConfirmationFragment;
import com.goguardian.ui.common.SosFragment;
import com.goguardian.ui.rider.RideActiveFragment;
import com.goguardian.ui.rider.RideArrivingFragment;
import com.goguardian.ui.rider.RideBookingFragment;
import com.goguardian.ui.rider.RideSearchingFragment;
import com.goguardian.ui.rider.RiderHomeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity
        implements SplashLoginFragment.OnLoginActionListener,
                   RiderHomeFragment.OnRiderActionListener,
                   AdminDashboardFragment.OnAdminActionListener,
                   WelcomeFragment.OnWelcomeFinishedListener,
                   OnboardingFragment.OnOnboardingCompleteListener,
                   RideBookingFragment.OnBookingActionListener,
                   RideSearchingFragment.OnSearchingActionListener,
                   RideActiveFragment.OnActiveRideActionListener,
                   RideArrivingFragment.OnArrivingActionListener,
                   RideConfirmationFragment.OnConfirmationActionListener,
                   com.goguardian.ui.rider.TripReceiptFragment.OnReceiptActionListener,
                   com.goguardian.ui.rider.SafetyCheckFragment.OnSafetyCheckActionListener,
                   RideRatingFragment.OnRatingActionListener,
                   SosFragment.OnSosActionListener,
                   ComingSoonFragment.OnServicesActionListener,
                   com.goguardian.ui.rider.ProfileFragment.OnProfileActionListener {

    private static final String ADMIN_EMAIL   = "ayushsingh2262@gmail.com";
    private static final int    CANCEL_FEE    = 30;

    private DemoSessionManager   demoSessionManager;
    private BottomNavigationView bottomNavigationView;
    private boolean              isWelcomeShown = false;
    private View                 offlineBanner;
    private ConnectivityManager  connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            isWelcomeShown = savedInstanceState.getBoolean("isWelcomeShown", false);
        }

        demoSessionManager   = new DemoSessionManager(this);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        offlineBanner        = findViewById(R.id.offline_banner);
        bottomNavigationView.setOnItemSelectedListener(this::onNavigationItemSelected);
        registerConnectivityCallback();

        if (savedInstanceState == null) {
            setNavVisibility(false);
            if (!demoSessionManager.isOnboardingShown()) {
                showFragment(new OnboardingFragment());
            } else if (!isWelcomeShown) {
                showFragment(new WelcomeFragment());
            } else {
                renderCurrentState();
            }
        } else {
            renderCurrentState();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isWelcomeShown", isWelcomeShown);
    }


    @Override
    public void onOnboardingComplete() {
        demoSessionManager.markOnboardingShown();
        showFragment(new WelcomeFragment());
    }

    @Override
    public void onWelcomeFinished() {
        isWelcomeShown = true;
        renderCurrentState();
    }

    private void renderCurrentState() {
        if (!isWelcomeShown) return;

        if (!demoSessionManager.isLoggedIn()) {
            setNavVisibility(false);
            showFragment(new SplashLoginFragment());
            return;
        }

        setNavVisibility(true);
        if (demoSessionManager.isAdmin()) {
            bottomNavigationView.getMenu().clear();
            bottomNavigationView.inflateMenu(R.menu.admin_bottom_nav);
            bottomNavigationView.setSelectedItemId(R.id.admin_dashboard);
            showFragment(new AdminDashboardFragment());
        } else {
            bottomNavigationView.getMenu().clear();
            bottomNavigationView.inflateMenu(R.menu.rider_bottom_nav);
            bottomNavigationView.setSelectedItemId(R.id.rider_home);
            showFragment(new RiderHomeFragment());
        }
    }

   
    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (demoSessionManager.isAdmin()) return handleAdminNavigation(item.getItemId());
        return handleRiderNavigation(item.getItemId());
    }

    private boolean handleRiderNavigation(int itemId) {
        if (itemId == R.id.rider_home) {
            showFragment(new RiderHomeFragment());
            return true;
        }
        if (itemId == R.id.rider_book) {
            showFragment(new RideBookingFragment());
            return true;
        }
        if (itemId == R.id.rider_profile) {
            showFragment(new com.goguardian.ui.rider.ProfileFragment());
            return true;
        }
        return false;
    }

    private boolean handleAdminNavigation(int itemId) {
        if (itemId == R.id.admin_dashboard) {
            showFragment(new AdminDashboardFragment());
            return true;
        }
        if (itemId == R.id.admin_rides) {
            showFragment(new com.goguardian.ui.admin.AdminRidesFragment());
            return true;
        }
        if (itemId == R.id.admin_users) {
            showFragment(new com.goguardian.ui.admin.AdminUsersFragment());
            return true;
        }
        if (itemId == R.id.admin_claims) {
            showFragment(new com.goguardian.ui.admin.AdminClaimsFragment());
            return true;
        }
        if (itemId == R.id.admin_broadcast) {
            showFragment(new com.goguardian.ui.admin.AdminBroadcastFragment());
            return true;
        }
        return false;
    }

    // ── RideBookingFragment.OnBookingActionListener ───────────────────────────
    @Override
    public void onRideBooked(String rideId, String pickup, String dropoff,
                             String vehicleType, int fare,
                             double pickupLat, double pickupLng,
                             double dropoffLat, double dropoffLng) {
        // Hide bottom nav — entering full-screen ride mode
        setNavVisibility(false);
        showFragment(RideSearchingFragment.newInstance(
            rideId, pickup, dropoff, vehicleType, fare,
            pickupLat, pickupLng, dropoffLat, dropoffLng));
    }

    // ── RideSearchingFragment.OnSearchingActionListener ───────────────────────
    @Override
    public void onDriverFound(String rideId, String pickup, String dropoff,
                              String vehicleType, int fare,
                              String driverName, String vehicleNumber,
                              String vehicleModel, float driverRating,
                              int etaMinutes,
                              double pickupLat, double pickupLng,
                              double dropoffLat, double dropoffLng) {
        // Show driver matched confirmation screen first
        showFragment(RideConfirmationFragment.newInstance(
            rideId, pickup, dropoff, vehicleType, fare,
            driverName, vehicleNumber, vehicleModel, driverRating,
            etaMinutes, pickupLat, pickupLng, dropoffLat, dropoffLng));
    }

    // ── RideConfirmationFragment.OnConfirmationActionListener ─────────────────
    @Override
    public void onRideConfirmed(String rideId, String pickup, String dropoff,
                                String vehicleType, int fare,
                                String driverName, String vehicleNumber,
                                String vehicleModel, float driverRating,
                                int etaMinutes,
                                double pickupLat, double pickupLng,
                                double dropoffLat, double dropoffLng) {
        // Driver "arriving" phase — mock 500m approach to pickup
        showFragment(RideArrivingFragment.newInstance(
            rideId, pickup, dropoff, vehicleType, fare,
            driverName, vehicleNumber, vehicleModel, driverRating,
            pickupLat, pickupLng, dropoffLat, dropoffLng));
    }

    @Override
    public void onConfirmationCancelled(String rideId, int fare) {
        // Full refund — same as cancelling during search
        refundToWallet(fare);
        exitRideMode();
        Toast.makeText(this, "Ride cancelled. Full refund of ₹" + fare + " processed.",
            Toast.LENGTH_LONG).show();
    }

    // ── RideArrivingFragment.OnArrivingActionListener ─────────────────────────
    @Override
    public void onDriverArrived(String rideId, String pickup, String dropoff,
                                String vehicleType, int fare,
                                String driverName, String vehicleNumber,
                                String vehicleModel, float driverRating,
                                double pickupLat, double pickupLng,
                                double dropoffLat, double dropoffLng) {
        // No OTP step — driver "arrives" and ride starts immediately
        showFragment(RideActiveFragment.newInstance(
            rideId, pickup, dropoff, vehicleType, fare,
            driverName, vehicleNumber, vehicleModel, driverRating,
            /* etaMinutes */ Math.max(1, (int) Math.round(estimatedTripMinutes(
                pickupLat, pickupLng, dropoffLat, dropoffLng))),
            pickupLat, pickupLng, dropoffLat, dropoffLng));
    }

    private double estimatedTripMinutes(double pLat, double pLng, double dLat, double dLng) {
        float[] dist = new float[1];
        android.location.Location.distanceBetween(pLat, pLng, dLat, dLng, dist);
        // ~1.5 min per km, just for display purposes during 20s anim
        return Math.max(2, dist[0] / 1000f * 1.5);
    }

    @Override
    public void onSearchCancelled(String rideId, int fare) {
        // Full refund — add fare back to wallet
        refundToWallet(fare);
        exitRideMode();
        Toast.makeText(this, "Booking cancelled. Full refund of ₹" + fare + " processed.",
            Toast.LENGTH_LONG).show();
    }

    // ── RideActiveFragment.OnActiveRideActionListener ─────────────────────────
    @Override
    public void onRideCompleted(String rideId, int fare, String driverName, String vehicleModel) {
        // Receipt → Rating → Safety check → Home
        showFragment(com.goguardian.ui.rider.TripReceiptFragment.newInstance(
            rideId, driverName, vehicleModel, fare));
    }

    // ── TripReceiptFragment.OnReceiptActionListener ───────────────────────────
    @Override
    public void onReceiptDone(String rideId, int fare, String driverName, String vehicleModel) {
        showFragment(RideRatingFragment.newInstance(rideId, driverName, vehicleModel, fare));
    }

    // ── RideRatingFragment.OnRatingActionListener ─────────────────────────────
    @Override
    public void onRatingDone() {
        showFragment(new com.goguardian.ui.rider.SafetyCheckFragment());
    }

    // ── SafetyCheckFragment.OnSafetyCheckActionListener ───────────────────────
    @Override
    public void onSafetyCheckDone() {
        exitRideMode();
    }

    @Override
    public void onRideCancelled(String rideId, int fare) {
        // Partial refund (deduct cancellation fee)
        int refund = Math.max(fare - CANCEL_FEE, 0);
        if (refund > 0) refundToWallet(refund);
        exitRideMode();
        Toast.makeText(this,
            "Ride cancelled. ₹" + CANCEL_FEE + " fee charged. "
            + (refund > 0 ? "₹" + refund + " refunded to wallet." : ""),
            Toast.LENGTH_LONG).show();
    }

    // Called from RideActiveFragment SOS button (reuses same handler)
    // onSosRequested() is already implemented above via OnRiderActionListener

    // ── ProfileFragment.OnProfileActionListener ───────────────────────────────
    @Override
    public void onLogoutFromProfile() {
        onLogoutRequested();
    }

    @Override
    public void onOpenBookingFromProfile() {
        bottomNavigationView.setSelectedItemId(R.id.rider_book);
    }

    // ── Auth callbacks ─────────────────────────────────────────────────────────
    @Override
    public void onLoginAsRider() {
        demoSessionManager.signIn(false);
        renderCurrentState();
    }

    @Override
    public void onLoginAsAdmin() {
        demoSessionManager.signIn(true);
        renderCurrentState();
    }

    @Override
    public void onGoogleLoginSuccess(String email) {
        boolean isAdmin = email != null && ADMIN_EMAIL.equalsIgnoreCase(email.trim());
        demoSessionManager.signIn(isAdmin);
        renderCurrentState();
    }

    @Override
    public void onLogoutRequested() {
        demoSessionManager.signOut();
        renderCurrentState();
    }

    // ── Rider home callbacks ──────────────────────────────────────────────────
    @Override
    public void onOpenBooking() {
        bottomNavigationView.setSelectedItemId(R.id.rider_book);
    }

    @Override
    public void onOpenProfile() {
        bottomNavigationView.setSelectedItemId(R.id.rider_profile);
    }

    @Override
    public void onSosRequested() {
        setNavVisibility(false);
        showFragment(new SosFragment());
    }

    @Override
    public void onMoreServicesRequested() {
        setNavVisibility(false);
        showFragment(new ComingSoonFragment());
    }

    // ── SosFragment.OnSosActionListener ──────────────────────────────────────
    @Override
    public void onSosClosed() {
        // Return to wherever the user was; restore nav if rider is logged in
        if (demoSessionManager.isLoggedIn() && !demoSessionManager.isAdmin()) {
            setNavVisibility(true);
            bottomNavigationView.setSelectedItemId(R.id.rider_home);
            showFragment(new RiderHomeFragment());
        } else {
            renderCurrentState();
        }
    }

    // ── ComingSoonFragment.OnServicesActionListener ───────────────────────────
    @Override
    public void onServicesClosed() {
        setNavVisibility(true);
        bottomNavigationView.setSelectedItemId(R.id.rider_home);
        showFragment(new RiderHomeFragment());
    }

    // ── Admin callbacks ────────────────────────────────────────────────────────
    @Override
    public void onOpenAdminSection(int menuId) {
        bottomNavigationView.setSelectedItemId(menuId);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    /** Restore bottom nav and go to home screen. */
    private void exitRideMode() {
        setNavVisibility(true);
        bottomNavigationView.setSelectedItemId(R.id.rider_home);
        showFragment(new RiderHomeFragment());
    }

    /** Add {@code amount} back to the current user's wallet. */
    private void refundToWallet(int amount) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || amount <= 0) return;
        com.google.firebase.database.DatabaseReference balRef = FirebaseDatabase.getInstance()
            .getReference("users").child(user.getUid()).child("balance");
        balRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long current = snapshot.getValue(Long.class);
                balRef.setValue((current != null ? current : 0) + amount);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setNavVisibility(boolean visible) {
        int vis = visible ? android.view.View.VISIBLE : android.view.View.GONE;
        android.view.View navCard = findViewById(R.id.card_bottom_nav);
        if (navCard != null) navCard.setVisibility(vis);
        if (bottomNavigationView != null) bottomNavigationView.setVisibility(vis);
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out)
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, fragment)
            .commit();
    }

    // ── Connectivity ──────────────────────────────────────────────────────────
    private void registerConnectivityCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        // Initial state: hide banner if currently online
        Network active = connectivityManager.getActiveNetwork();
        NetworkCapabilities caps = active != null ? connectivityManager.getNetworkCapabilities(active) : null;
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        setOfflineBanner(!online);

        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> setOfflineBanner(false));
            }
            @Override public void onLost(@NonNull Network network) {
                runOnUiThread(() -> setOfflineBanner(true));
            }
        };
        try {
            connectivityManager.registerNetworkCallback(req, networkCallback);
        } catch (SecurityException ignored) {}
    }

    private void setOfflineBanner(boolean offline) {
        if (offlineBanner != null) {
            offlineBanner.setVisibility(offline ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (IllegalArgumentException ignored) {}
        }
    }
}
