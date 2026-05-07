package com.goguardian.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminRidesFragment extends Fragment {

    private RecyclerView recyclerView;
    private RidesAdapter adapter;
    private DatabaseReference ridesRef;
    private DatabaseReference usersRef;
    private ValueEventListener ridesListener;
    private ValueEventListener usersListener;

    private final Map<String, String> uidToName = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_rides, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_admin_rides);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RidesAdapter();
        recyclerView.setAdapter(adapter);

        // Cache user names so we can label rides with the rider's name instead of a uid.
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                uidToName.clear();
                for (DataSnapshot u : snapshot.getChildren()) {
                    String name = u.child("name").getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        uidToName.put(u.getKey(), name);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        usersRef.addValueEventListener(usersListener);

        ridesRef = FirebaseDatabase.getInstance().getReference("rides");
        ridesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<AdminRideItem> rides = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    AdminRideItem ride = new AdminRideItem();
                    ride.id          = ds.getKey();
                    ride.riderUid    = str(ds, "riderUid");
                    ride.pickup      = str(ds, "pickup");
                    ride.dropoff     = str(ds, "dropoff");
                    ride.status      = str(ds, "status");
                    ride.vehicleType = str(ds, "vehicleType");
                    ride.fare        = num(ds, "fare");
                    ride.distanceKm  = (int) num(ds, "distanceKm");
                    ride.createdAt   = num(ds, "createdAt");
                    ride.pickupTime  = num(ds, "pickupTime");
                    ride.dropoffTime = num(ds, "dropoffTime");
                    rides.add(ride);
                }
                // Most-recent first
                rides.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                adapter.setRides(rides);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load rides", Toast.LENGTH_SHORT).show();
            }
        };
        ridesRef.addValueEventListener(ridesListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ridesRef != null && ridesListener != null) {
            ridesRef.removeEventListener(ridesListener);
        }
        if (usersRef != null && usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }
    }

    private static String str(DataSnapshot s, String key) {
        Object v = s.child(key).getValue();
        return v != null ? String.valueOf(v) : "";
    }

    private static long num(DataSnapshot s, String key) {
        Object v = s.child(key).getValue();
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double)  return ((Double) v).longValue();
        try { return v != null ? Long.parseLong(String.valueOf(v)) : 0L; }
        catch (NumberFormatException e) { return 0L; }
    }

    public static class AdminRideItem {
        public String id, riderUid, pickup, dropoff, status, vehicleType;
        public long fare;
        public int  distanceKm;
        public long createdAt, pickupTime, dropoffTime;
    }

    class RidesAdapter extends RecyclerView.Adapter<RidesAdapter.RideViewHolder> {
        private List<AdminRideItem> rides = new ArrayList<>();

        private final SimpleDateFormat timeFormat     =
                new SimpleDateFormat("h:mm a", Locale.getDefault());
        private final SimpleDateFormat dateTimeFormat =
                new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());

        public void setRides(List<AdminRideItem> rides) {
            this.rides = rides;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_ride, parent, false);
            return new RideViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RideViewHolder h, int position) {
            AdminRideItem ride = rides.get(position);

            // Vehicle icon
            String vt = ride.vehicleType != null ? ride.vehicleType.toLowerCase(Locale.ROOT) : "";
            if ("bike".equals(vt))      h.icon.setImageResource(R.drawable.ic_motorcycle);
            else if ("auto".equals(vt)) h.icon.setImageResource(R.drawable.ic_auto_rickshaw);
            else                        h.icon.setImageResource(R.drawable.ic_car_side);

            String pickup  = ride.pickup  != null && !ride.pickup.isEmpty()  ? ride.pickup  : "Unknown pickup";
            String dropoff = ride.dropoff != null && !ride.dropoff.isEmpty() ? ride.dropoff : "Unknown drop-off";
            h.route.setText(pickup + "  →  " + dropoff);

            String shortId = ride.id != null && ride.id.length() > 6 ? ride.id.substring(0, 6) : ride.id;
            h.id.setText("#" + shortId);

            h.fare.setText("₹" + ride.fare);

            // Status pill — same look as rider history
            String statusLower = ride.status != null ? ride.status.toLowerCase(Locale.ROOT) : "";
            String statusDisplay;
            int bgDrawable;
            int textColor;
            switch (statusLower) {
                case "completed":
                    statusDisplay = "Completed";
                    bgDrawable = R.drawable.bg_pill_green;
                    textColor  = h.itemView.getResources().getColor(R.color.success, null);
                    break;
                case "cancelled":
                    statusDisplay = "Cancelled";
                    bgDrawable = R.drawable.bg_pill_red;
                    textColor  = h.itemView.getResources().getColor(R.color.status_cancelled, null);
                    break;
                case "in_progress":
                case "active":
                case "ongoing":
                case "driver_assigned":
                    statusDisplay = "In Progress";
                    bgDrawable = R.drawable.bg_pill_blue;
                    textColor  = h.itemView.getResources().getColor(R.color.primary, null);
                    break;
                default:
                    statusDisplay = "Searching";
                    bgDrawable = R.drawable.bg_pill_amber;
                    textColor  = h.itemView.getResources().getColor(R.color.warning, null);
                    break;
            }
            h.status.setText(statusDisplay);
            h.status.setBackgroundResource(bgDrawable);
            h.status.setTextColor(textColor);

            // Rider name (falls back to a short uid if name not loaded yet)
            String rider = uidToName.get(ride.riderUid);
            if (rider == null || rider.isEmpty()) {
                rider = ride.riderUid != null && ride.riderUid.length() > 6
                        ? ride.riderUid.substring(0, 6) + "…" : ride.riderUid;
            }
            h.rider.setText("Rider: " + (rider != null ? rider : "—"));

            // Pickup → Drop · distance
            StringBuilder trip = new StringBuilder();
            if (ride.pickupTime > 0 && ride.dropoffTime > 0) {
                trip.append("Pickup ").append(timeFormat.format(new Date(ride.pickupTime)))
                    .append(" → Drop ").append(timeFormat.format(new Date(ride.dropoffTime)));
            }
            if (ride.distanceKm > 0) {
                if (trip.length() > 0) trip.append("  ·  ");
                trip.append(ride.distanceKm).append(" km");
            }
            if (trip.length() > 0) {
                h.tripTimes.setText(trip.toString());
                h.tripTimes.setVisibility(View.VISIBLE);
            } else {
                h.tripTimes.setVisibility(View.GONE);
            }

            // Booked at
            if (ride.createdAt > 0) {
                h.bookedAt.setText("Booked " + formatDate(ride.createdAt));
                h.bookedAt.setVisibility(View.VISIBLE);
            } else {
                h.bookedAt.setVisibility(View.GONE);
            }
        }

        private String formatDate(long millis) {
            Date date = new Date(millis);
            Calendar now = Calendar.getInstance();
            Calendar then = Calendar.getInstance();
            then.setTime(date);
            if (now.get(Calendar.DATE)  == then.get(Calendar.DATE)
             && now.get(Calendar.MONTH) == then.get(Calendar.MONTH)
             && now.get(Calendar.YEAR)  == then.get(Calendar.YEAR)) {
                return "today, " + timeFormat.format(date);
            }
            now.add(Calendar.DATE, -1);
            if (now.get(Calendar.DATE)  == then.get(Calendar.DATE)
             && now.get(Calendar.MONTH) == then.get(Calendar.MONTH)
             && now.get(Calendar.YEAR)  == then.get(Calendar.YEAR)) {
                return "yesterday, " + timeFormat.format(date);
            }
            return dateTimeFormat.format(date);
        }

        @Override
        public int getItemCount() {
            return rides.size();
        }

        class RideViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView route, id, status, fare, rider, tripTimes, bookedAt;
            RideViewHolder(@NonNull View v) {
                super(v);
                icon      = v.findViewById(R.id.icon_vehicle_type);
                route     = v.findViewById(R.id.text_ride_route);
                id        = v.findViewById(R.id.text_ride_id);
                status    = v.findViewById(R.id.text_ride_status);
                fare      = v.findViewById(R.id.text_ride_fare);
                rider     = v.findViewById(R.id.text_ride_rider);
                tripTimes = v.findViewById(R.id.text_ride_trip_times);
                bookedAt  = v.findViewById(R.id.text_ride_booked_at);
            }
        }
    }
}
