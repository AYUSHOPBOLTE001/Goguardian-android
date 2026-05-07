package com.goguardian.ui.rider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    public interface OnProfileActionListener {
        void onLogoutFromProfile();
        void onOpenBookingFromProfile();
    }

    private OnProfileActionListener actionListener;

    private RideHistoryAdapter rideHistoryAdapter;
    private View emptyRidesView;
    private TextView textProfileName;
    private TextView textProfileEmail;
    private TextView textProfileWallet;
    private TextView textTotalRides;
    private TextView textTotalSpent;
    private TextView textAvatarLetter;

    private ValueEventListener userListener;
    private ValueEventListener ridesListener;
    private DatabaseReference userRef;
    private DatabaseReference ridesRef;
    private Query ridesQuery;

    private String currentName = "";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnProfileActionListener) {
            actionListener = (OnProfileActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton topUpButton = view.findViewById(R.id.button_top_up);
        MaterialButton editButton = view.findViewById(R.id.button_edit_profile);
        MaterialButton logoutButton = view.findViewById(R.id.button_logout);
        RecyclerView ridesRecycler = view.findViewById(R.id.recycler_my_rides);
        emptyRidesView = view.findViewById(R.id.text_empty_rides);
        View bookFromEmptyButton = view.findViewById(R.id.button_empty_book_now);
        if (bookFromEmptyButton != null) {
            bookFromEmptyButton.setOnClickListener(v -> {
                com.goguardian.util.HapticUtils.feedback(v);
                if (actionListener != null) actionListener.onOpenBookingFromProfile();
            });
        }
        textProfileName = view.findViewById(R.id.text_profile_name);
        textProfileEmail = view.findViewById(R.id.text_profile_email);
        textProfileWallet = view.findViewById(R.id.text_profile_wallet);
        textTotalRides = view.findViewById(R.id.text_total_rides);
        textTotalSpent = view.findViewById(R.id.text_total_spent);
        textAvatarLetter = view.findViewById(R.id.text_avatar_letter);

        rideHistoryAdapter = new RideHistoryAdapter();
        ridesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        ridesRecycler.setAdapter(rideHistoryAdapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            emptyRidesView.setVisibility(VISIBLE);
            return;
        }

        topUpButton.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            showTopUpDialog(user);
        });

        editButton.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            showEditNameDialog(user);
        });

        logoutButton.setOnClickListener(v -> {
            com.goguardian.util.HapticUtils.feedback(v);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to sign out?")
                    .setPositiveButton("Sign Out", (dialog, which) -> {
                        if (actionListener != null) actionListener.onLogoutFromProfile();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                String name = snapshot.child("name").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                long balance = 0;
                Long balanceVal = snapshot.child("balance").getValue(Long.class);
                if (balanceVal != null) balance = balanceVal;

                currentName = name != null ? name : "";
                textProfileName.setText(currentName.isEmpty() ? "GoGuardian User" : currentName);
                textProfileEmail.setText(email != null ? email : user.getEmail());
                textProfileWallet.setText(formatAmount(balance));

                // Set avatar letter from first character of name
                if (!currentName.isEmpty()) {
                    textAvatarLetter.setText(String.valueOf(Character.toUpperCase(currentName.charAt(0))));
                } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                    textAvatarLetter.setText(String.valueOf(Character.toUpperCase(user.getEmail().charAt(0))));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);

        ridesRef = FirebaseDatabase.getInstance().getReference("rides");
        // Listen on the whole /rides node and filter client-side by riderUid.
        // The indexed query (orderByChild("riderUid").equalTo(uid)) silently
        // returns nothing when the Realtime DB doesn't have ".indexOn":["riderUid"]
        // or when the rule layout disallows that query — which made the history
        // appear empty. Filtering on the client keeps things working regardless.
        ridesQuery = ridesRef;
        final String uid = user.getUid();
        ridesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<RideHistoryAdapter.RideItem> rides = new ArrayList<>();
                long totalSpent = 0;
                int completedCount = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String rideUid = child.child("riderUid").getValue(String.class);
                    if (rideUid == null || !rideUid.equals(uid)) continue;

                    String pickup = getStr(child, "pickup", "Unknown pickup");
                    String dropoff = getStr(child, "dropoff", "Unknown drop-off");
                    String status = getStr(child, "status", "unknown");
                    String vehicleType = getStr(child, "vehicleType", "Cab");
                    Object fareObj = child.child("fare").getValue();
                    String fareStr = fareObj != null ? String.valueOf(fareObj) : "0";
                    long fareValue = 0;
                    if (fareObj instanceof Long) fareValue = (Long) fareObj;
                    else if (fareObj instanceof Integer) fareValue = ((Integer) fareObj).longValue();
                    else if (fareObj != null) {
                        try { fareValue = Long.parseLong(fareObj.toString()); } catch (NumberFormatException ignored) {}
                    }

                    if ("completed".equalsIgnoreCase(status)) {
                        completedCount++;
                        totalSpent += fareValue;
                    }

                    long createdAt   = getLong(child, "createdAt",  0L);
                    long pickupTime  = getLong(child, "pickupTime", createdAt);
                    long dropoffTime = getLong(child, "dropoffTime", 0L);
                    int  distanceKm  = (int) getLong(child, "distanceKm", 0L);
                    rides.add(new RideHistoryAdapter.RideItem(
                            pickup, dropoff, status, fareStr, createdAt, vehicleType,
                            pickupTime, dropoffTime, distanceKm));
                }

                textTotalRides.setText(String.valueOf(completedCount));
                textTotalSpent.setText(formatAmount(totalSpent));

                rides.sort(Comparator.comparingLong((RideHistoryAdapter.RideItem r) -> r.createdAt).reversed());
                rideHistoryAdapter.submitItems(rides);

                emptyRidesView.setVisibility(rides.isEmpty() ? VISIBLE : GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                emptyRidesView.setVisibility(VISIBLE);
                Toast.makeText(getContext(),
                        "Could not load rides: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };
        ridesQuery.addValueEventListener(ridesListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
        if (ridesQuery != null && ridesListener != null) {
            ridesQuery.removeEventListener(ridesListener);
        }
    }

    private void showTopUpDialog(FirebaseUser user) {
        new TopUpBottomSheet().show(getChildFragmentManager(), "topup");
    }

    private void showEditNameDialog(FirebaseUser user) {
        EditText input = new EditText(requireContext());
        input.setText(currentName);
        input.setSelection(currentName.length());
        input.setPadding(48, 24, 48, 8);
        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Name")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseDatabase.getInstance().getReference("users")
                            .child(user.getUid()).child("name").setValue(newName);
                    Toast.makeText(getContext(), "Name updated!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String formatAmount(long amount) {
        return "₹" + String.format(Locale.US, "%,d", amount);
    }

    private String getStr(DataSnapshot snapshot, String key, String fallback) {
        Object value = snapshot.child(key).getValue();
        return value != null ? String.valueOf(value) : fallback;
    }

    private long getLong(DataSnapshot snapshot, String key, long fallback) {
        Object value = snapshot.child(key).getValue();
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Double) return ((Double) value).longValue();
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
