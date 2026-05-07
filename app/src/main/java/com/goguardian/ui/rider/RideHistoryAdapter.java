package com.goguardian.ui.rider;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.goguardian.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RideHistoryAdapter extends RecyclerView.Adapter<RideHistoryAdapter.RideViewHolder> {

    private final List<RideItem> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault());

    public void submitItems(List<RideItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ride_history, parent, false);
        return new RideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RideViewHolder holder, int position) {
        RideItem item = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.routeText.setText(item.pickup + "  →  " + item.dropoff);
        holder.fareText.setText("₹" + item.fare);
        holder.timeText.setText(formatDate(item.createdAt));

        // Optional second line: "Pickup 5:00 PM → Drop 5:23 PM · 12 km".
        // Older rides may not have these fields recorded — hide the line when missing.
        if (holder.tripTimesText != null) {
            StringBuilder sb = new StringBuilder();
            if (item.pickupTime > 0 && item.dropoffTime > 0) {
                sb.append("Pickup ").append(timeFormat.format(new Date(item.pickupTime)))
                  .append(" → Drop ").append(timeFormat.format(new Date(item.dropoffTime)));
            }
            if (item.distanceKm > 0) {
                if (sb.length() > 0) sb.append("  ·  ");
                sb.append(item.distanceKm).append(" km");
            }
            if (sb.length() > 0) {
                holder.tripTimesText.setText(sb.toString());
                holder.tripTimesText.setVisibility(View.VISIBLE);
            } else {
                holder.tripTimesText.setVisibility(View.GONE);
            }
        }

        // Status badge: text + color
        String statusDisplay;
        int bgDrawable;
        int textColor;
        String statusLower = item.status != null ? item.status.toLowerCase() : "";
        switch (statusLower) {
            case "completed":
                statusDisplay = "Completed";
                bgDrawable = R.drawable.bg_pill_green;
                textColor = ctx.getResources().getColor(R.color.success, null);
                break;
            case "cancelled":
                statusDisplay = "Cancelled";
                bgDrawable = R.drawable.bg_pill_red;
                textColor = ctx.getResources().getColor(R.color.status_cancelled, null);
                break;
            case "active":
            case "ongoing":
                statusDisplay = "In Progress";
                bgDrawable = R.drawable.bg_pill_blue;
                textColor = ctx.getResources().getColor(R.color.primary, null);
                break;
            default:
                statusDisplay = "Searching";
                bgDrawable = R.drawable.bg_pill_amber;
                textColor = ctx.getResources().getColor(R.color.warning, null);
                break;
        }
        holder.statusText.setText(statusDisplay);
        holder.statusText.setBackgroundResource(bgDrawable);
        holder.statusText.setTextColor(textColor);

        // Vehicle icon
        if (holder.vehicleIcon != null) {
            String type = item.vehicleType != null ? item.vehicleType.toLowerCase() : "";
            if (type.equals("bike")) {
                holder.vehicleIcon.setImageResource(R.drawable.ic_motorcycle);
            } else if (type.equals("auto")) {
                holder.vehicleIcon.setImageResource(R.drawable.ic_auto_rickshaw);
            } else {
                holder.vehicleIcon.setImageResource(R.drawable.ic_car_side);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatDate(long millis) {
        if (millis <= 0) return "";
        Date date = new Date(millis);
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTime(date);
        if (now.get(Calendar.DATE) == then.get(Calendar.DATE)
                && now.get(Calendar.MONTH) == then.get(Calendar.MONTH)
                && now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            return "Today, " + timeFormat.format(date);
        }
        now.add(Calendar.DATE, -1);
        if (now.get(Calendar.DATE) == then.get(Calendar.DATE)
                && now.get(Calendar.MONTH) == then.get(Calendar.MONTH)
                && now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            return "Yesterday, " + timeFormat.format(date);
        }
        return dateTimeFormat.format(date);
    }

    static class RideViewHolder extends RecyclerView.ViewHolder {
        TextView routeText;
        TextView statusText;
        TextView fareText;
        TextView timeText;
        TextView tripTimesText;
        ImageView vehicleIcon;

        RideViewHolder(@NonNull View itemView) {
            super(itemView);
            routeText = itemView.findViewById(R.id.text_ride_route);
            statusText = itemView.findViewById(R.id.text_ride_status);
            fareText = itemView.findViewById(R.id.text_ride_fare);
            timeText = itemView.findViewById(R.id.text_ride_time);
            tripTimesText = itemView.findViewById(R.id.text_ride_trip_times);
            vehicleIcon = itemView.findViewById(R.id.icon_vehicle_type);
        }
    }

    public static class RideItem {
        public final String pickup;
        public final String dropoff;
        public final String status;
        public final String fare;
        public final long createdAt;
        public final String vehicleType;
        public final long pickupTime;
        public final long dropoffTime;
        public final int distanceKm;

        public RideItem(String pickup, String dropoff, String status, String fare,
                        long createdAt, String vehicleType) {
            this(pickup, dropoff, status, fare, createdAt, vehicleType, 0L, 0L, 0);
        }

        public RideItem(String pickup, String dropoff, String status, String fare,
                        long createdAt, String vehicleType,
                        long pickupTime, long dropoffTime, int distanceKm) {
            this.pickup = pickup;
            this.dropoff = dropoff;
            this.status = status;
            this.fare = fare;
            this.createdAt = createdAt;
            this.vehicleType = vehicleType;
            this.pickupTime = pickupTime;
            this.dropoffTime = dropoffTime;
            this.distanceKm = distanceKm;
        }
    }
}
