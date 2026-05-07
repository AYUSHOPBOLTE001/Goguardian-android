package com.goguardian.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.goguardian.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Admin-only screen showing one user's full profile: rides, wallet activity
 * (derived from rides), and SOS records. Updates live via Firebase listeners.
 */
public class AdminUserDetailFragment extends Fragment {

    private static final String ARG_UID = "uid";

    private String uid;

    private TextView textName, textEmail, textInitials, textUid;
    private TextView textBalance, textCompleted, textSpent;
    private RecyclerView recyclerRides, recyclerWallet, recyclerSos;
    private TextView emptyRides, emptyWallet, emptySos;

    private RidesAdapter ridesAdapter;
    private WalletAdapter walletAdapter;
    private SosAdapter sosAdapter;

    private DatabaseReference userRef, ridesRef, sosRef;
    private ValueEventListener userListener, ridesListener, sosListener;

    public static AdminUserDetailFragment newInstance(String uid) {
        Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        AdminUserDetailFragment f = new AdminUserDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_user_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        uid = getArguments() != null ? getArguments().getString(ARG_UID, "") : "";

        textName      = view.findViewById(R.id.text_detail_name);
        textEmail     = view.findViewById(R.id.text_detail_email);
        textInitials  = view.findViewById(R.id.text_detail_initials);
        textUid       = view.findViewById(R.id.text_detail_uid);
        textBalance   = view.findViewById(R.id.text_detail_balance);
        textCompleted = view.findViewById(R.id.text_detail_completed_rides);
        textSpent     = view.findViewById(R.id.text_detail_total_spent);

        recyclerRides  = view.findViewById(R.id.recycler_user_rides);
        recyclerWallet = view.findViewById(R.id.recycler_user_wallet);
        recyclerSos    = view.findViewById(R.id.recycler_user_sos);
        emptyRides     = view.findViewById(R.id.text_user_rides_empty);
        emptyWallet    = view.findViewById(R.id.text_user_wallet_empty);
        emptySos       = view.findViewById(R.id.text_user_sos_empty);

        ridesAdapter  = new RidesAdapter();
        walletAdapter = new WalletAdapter();
        sosAdapter    = new SosAdapter();
        recyclerRides.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerWallet.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerSos.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerRides.setAdapter(ridesAdapter);
        recyclerWallet.setAdapter(walletAdapter);
        recyclerSos.setAdapter(sosAdapter);

        view.findViewById(R.id.button_user_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        textUid.setText("UID: " + (uid.length() > 12 ? uid.substring(0, 12) + "…" : uid));

        attachUserListener();
        attachRidesListener();
        attachSosListener();
    }

    private void attachUserListener() {
        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                String name = snapshot.child("name").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                Long balance = snapshot.child("balance").getValue(Long.class);

                String displayName = name != null && !name.isEmpty() ? name : "Anonymous";
                textName.setText(displayName);
                textEmail.setText(email != null ? email : "—");
                textInitials.setText(String.valueOf(Character.toUpperCase(displayName.charAt(0))));
                textBalance.setText(formatRupees(balance != null ? balance : 0L));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);
    }

    private void attachRidesListener() {
        ridesRef = FirebaseDatabase.getInstance().getReference("rides");
        ridesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<RideRow> rides = new ArrayList<>();
                List<WalletRow> wallet = new ArrayList<>();
                long totalSpent = 0;
                int completedCount = 0;

                for (DataSnapshot ride : snapshot.getChildren()) {
                    String rUid = ride.child("riderUid").getValue(String.class);
                    if (rUid == null || !rUid.equals(uid)) continue;

                    String pickup  = strOr(ride, "pickup", "Unknown pickup");
                    String dropoff = strOr(ride, "dropoff", "Unknown drop-off");
                    String status  = strOr(ride, "status", "unknown");
                    String vt      = strOr(ride, "vehicleType", "Cab");
                    long fare      = longOr(ride, "fare", 0L);
                    long when      = longOr(ride, "createdAt", longOr(ride, "pickupTime", 0L));

                    rides.add(new RideRow(ride.getKey(), pickup, dropoff, status, vt, fare, when));

                    if ("completed".equalsIgnoreCase(status)) {
                        completedCount++;
                        totalSpent += fare;
                        wallet.add(new WalletRow("Ride to " + dropoff, when, -fare, false));
                    } else if ("cancelled".equalsIgnoreCase(status)) {
                        // Booking deducted full fare; cancellation refunds what we
                        // currently refund from MainActivity (full at search,
                        // partial at active). Show both legs so the math reads.
                        wallet.add(new WalletRow("Ride hold (" + dropoff + ")", when, -fare, false));
                        wallet.add(new WalletRow("Cancellation refund", when + 1, fare, true));
                    } else {
                        // searching / driver_assigned / active — fare is held but
                        // ride hasn't completed. Show as pending hold.
                        wallet.add(new WalletRow("Ride hold (" + dropoff + ")", when, -fare, false));
                    }
                }

                rides.sort(Comparator.comparingLong((RideRow r) -> r.createdAt).reversed());
                wallet.sort(Comparator.comparingLong((WalletRow w) -> w.when).reversed());

                textCompleted.setText(String.valueOf(completedCount));
                textSpent.setText(formatRupees(totalSpent));
                ridesAdapter.submit(rides);
                walletAdapter.submit(wallet);
                emptyRides.setVisibility(rides.isEmpty() ? View.VISIBLE : View.GONE);
                emptyWallet.setVisibility(wallet.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        ridesRef.addValueEventListener(ridesListener);
    }

    private void attachSosListener() {
        sosRef = FirebaseDatabase.getInstance().getReference("sos_logs");
        sosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<SosRow> rows = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    String sUid = s.child("uid").getValue(String.class);
                    if (sUid == null || !sUid.equals(uid)) continue;
                    long when = longOr(s, "triggeredAt", 0L);
                    String status = strOr(s, "status", "open");
                    rows.add(new SosRow(s.getKey(), when, status));
                }
                rows.sort(Comparator.comparingLong((SosRow r) -> r.when).reversed());
                sosAdapter.submit(rows);
                emptySos.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        sosRef.addValueEventListener(sosListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userRef != null && userListener != null)   userRef.removeEventListener(userListener);
        if (ridesRef != null && ridesListener != null) ridesRef.removeEventListener(ridesListener);
        if (sosRef != null && sosListener != null)     sosRef.removeEventListener(sosListener);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private static String strOr(DataSnapshot s, String key, String fallback) {
        Object v = s.child(key).getValue();
        return v != null ? String.valueOf(v) : fallback;
    }

    private static long longOr(DataSnapshot s, String key, long fallback) {
        Object v = s.child(key).getValue();
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double)  return ((Double) v).longValue();
        try { return v != null ? Long.parseLong(v.toString()) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String formatRupees(long amount) {
        return "₹" + String.format(Locale.US, "%,d", amount);
    }

    private static String formatDate(long when) {
        if (when <= 0) return "—";
        return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(new Date(when));
    }

    private static String formatDateTime(long when) {
        if (when <= 0) return "—";
        return new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(new Date(when));
    }

    // ─── Row models ───────────────────────────────────────────────────────────
    private static class RideRow {
        final String id, pickup, dropoff, status, vehicleType;
        final long fare, createdAt;
        RideRow(String id, String pickup, String dropoff, String status,
                String vehicleType, long fare, long createdAt) {
            this.id = id; this.pickup = pickup; this.dropoff = dropoff;
            this.status = status; this.vehicleType = vehicleType;
            this.fare = fare; this.createdAt = createdAt;
        }
    }

    private static class WalletRow {
        final String label;
        final long when;
        final long amount;     // negative = debit, positive = credit
        final boolean isCredit;
        WalletRow(String label, long when, long amount, boolean isCredit) {
            this.label = label; this.when = when; this.amount = amount; this.isCredit = isCredit;
        }
    }

    private static class SosRow {
        final String id, status;
        final long when;
        SosRow(String id, long when, String status) {
            this.id = id; this.when = when; this.status = status;
        }
    }

    // ─── Adapters ─────────────────────────────────────────────────────────────
    private class RidesAdapter extends RecyclerView.Adapter<RidesAdapter.VH> {
        private final List<RideRow> data = new ArrayList<>();

        void submit(List<RideRow> rows) {
            data.clear(); data.addAll(rows); notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_ride, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            RideRow r = data.get(position);
            h.route.setText(r.pickup + "  →  " + r.dropoff);
            h.when.setText(formatDate(r.createdAt));
            h.fare.setText(formatRupees(r.fare));

            String key = r.status != null ? r.status.toLowerCase(Locale.ROOT) : "";
            String label;
            int bg, color;
            switch (key) {
                case "completed":
                    label = "Completed"; bg = R.drawable.bg_pill_green; color = R.color.success; break;
                case "cancelled":
                    label = "Cancelled"; bg = R.drawable.bg_pill_red; color = R.color.status_cancelled; break;
                case "active": case "in_progress": case "ongoing": case "driver_assigned":
                    label = "In Progress"; bg = R.drawable.bg_pill_blue; color = R.color.primary; break;
                default:
                    label = "Searching"; bg = R.drawable.bg_pill_amber; color = R.color.warning; break;
            }
            h.status.setText(label);
            h.status.setBackgroundResource(bg);
            h.status.setTextColor(h.itemView.getResources().getColor(color, null));

            if ("Bike".equalsIgnoreCase(r.vehicleType))      h.icon.setImageResource(R.drawable.ic_motorcycle);
            else if ("Auto".equalsIgnoreCase(r.vehicleType)) h.icon.setImageResource(R.drawable.ic_auto_rickshaw);
            else                                              h.icon.setImageResource(R.drawable.ic_car_side);
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView route, when, fare, status;
            VH(@NonNull View v) {
                super(v);
                icon   = v.findViewById(R.id.img_ride_icon);
                route  = v.findViewById(R.id.text_ride_route);
                when   = v.findViewById(R.id.text_ride_when);
                fare   = v.findViewById(R.id.text_ride_fare);
                status = v.findViewById(R.id.text_ride_status);
            }
        }
    }

    private class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.VH> {
        private final List<WalletRow> data = new ArrayList<>();

        void submit(List<WalletRow> rows) {
            data.clear(); data.addAll(rows); notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_wallet, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletRow w = data.get(position);
            h.label.setText(w.label);
            h.when.setText(formatDate(w.when));
            String prefix = w.isCredit ? "+₹" : "-₹";
            h.amount.setText(prefix + String.format(Locale.US, "%,d", Math.abs(w.amount)));
            h.amount.setTextColor(h.itemView.getResources().getColor(
                    w.isCredit ? R.color.success : R.color.secondary, null));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView label, when, amount;
            VH(@NonNull View v) {
                super(v);
                label  = v.findViewById(R.id.text_wallet_label);
                when   = v.findViewById(R.id.text_wallet_when);
                amount = v.findViewById(R.id.text_wallet_amount);
            }
        }
    }

    private class SosAdapter extends RecyclerView.Adapter<SosAdapter.VH> {
        private final List<SosRow> data = new ArrayList<>();

        void submit(List<SosRow> rows) {
            data.clear(); data.addAll(rows); notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_sos, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SosRow r = data.get(position);
            h.when.setText(formatDateTime(r.when));
            String shortId = r.id != null && r.id.length() > 8 ? r.id.substring(0, 8) : r.id;
            h.id.setText("ID: " + shortId);

            String status = r.status != null ? r.status.toUpperCase(Locale.ROOT) : "OPEN";
            h.status.setText(status);
            int bg, color;
            if ("RESOLVED".equals(status)) {
                bg = R.drawable.bg_pill_green; color = R.color.success;
            } else {
                bg = R.drawable.bg_pill_amber; color = R.color.warning;
            }
            h.status.setBackgroundResource(bg);
            h.status.setTextColor(h.itemView.getResources().getColor(color, null));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView when, id, status;
            VH(@NonNull View v) {
                super(v);
                when   = v.findViewById(R.id.text_sos_when);
                id     = v.findViewById(R.id.text_sos_id);
                status = v.findViewById(R.id.text_sos_status);
            }
        }
    }
}
