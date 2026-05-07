package com.goguardian.ui.common;

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
import com.goguardian.util.FareUtils;
import com.google.android.material.button.MaterialButton;

import java.util.Calendar;

public class RideConfirmationFragment extends Fragment {

    // Bundle keys
    public static final String KEY_RIDE_ID      = "ride_id";
    public static final String KEY_PICKUP       = "pickup";
    public static final String KEY_DROPOFF      = "dropoff";
    public static final String KEY_VEHICLE      = "vehicle_type";
    public static final String KEY_FARE         = "fare";
    public static final String KEY_DRIVER_NAME  = "driver_name";
    public static final String KEY_VEH_NUMBER   = "veh_number";
    public static final String KEY_VEH_MODEL    = "veh_model";
    public static final String KEY_RATING       = "driver_rating";
    public static final String KEY_ETA          = "eta_minutes";
    public static final String KEY_PLAT         = "pickup_lat";
    public static final String KEY_PLNG         = "pickup_lng";
    public static final String KEY_DLAT         = "dropoff_lat";
    public static final String KEY_DLNG         = "dropoff_lng";

    private static final int COUNTDOWN_SECONDS = 10;

    public interface OnConfirmationActionListener {
        void onRideConfirmed(String rideId, String pickup, String dropoff,
                             String vehicleType, int fare,
                             String driverName, String vehicleNumber,
                             String vehicleModel, float driverRating,
                             int etaMinutes,
                             double pickupLat, double pickupLng,
                             double dropoffLat, double dropoffLng);

        void onConfirmationCancelled(String rideId, int fare);
    }

    private OnConfirmationActionListener listener;
    private Handler handler;
    private boolean advanced = false;
    private boolean cancelled = false;

    // args
    private String rideId, pickup, dropoff, vehicleType, driverName, vehicleNumber, vehicleModel;
    private int fare, etaMinutes;
    private float driverRating;
    private double pickupLat, pickupLng, dropoffLat, dropoffLng;

    public static RideConfirmationFragment newInstance(
            String rideId, String pickup, String dropoff,
            String vehicleType, int fare,
            String driverName, String vehicleNumber, String vehicleModel,
            float driverRating, int etaMinutes,
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

        RideConfirmationFragment f = new RideConfirmationFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnConfirmationActionListener) {
            listener = (OnConfirmationActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ride_confirmation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        rideId       = args.getString(KEY_RIDE_ID, "");
        pickup       = args.getString(KEY_PICKUP, "");
        dropoff      = args.getString(KEY_DROPOFF, "");
        vehicleType  = args.getString(KEY_VEHICLE, "Cab");
        fare         = args.getInt(KEY_FARE, 0);
        driverName   = args.getString(KEY_DRIVER_NAME, "Driver");
        vehicleNumber = args.getString(KEY_VEH_NUMBER, "");
        vehicleModel = args.getString(KEY_VEH_MODEL, "");
        driverRating = args.getFloat(KEY_RATING, 4.5f);
        etaMinutes   = args.getInt(KEY_ETA, 8);
        pickupLat    = args.getDouble(KEY_PLAT, 28.6139);
        pickupLng    = args.getDouble(KEY_PLNG, 77.2090);
        dropoffLat   = args.getDouble(KEY_DLAT, 28.62);
        dropoffLng   = args.getDouble(KEY_DLNG, 77.24);

        // Bind views
        TextView subText    = view.findViewById(R.id.text_confirmation_sub);
        TextView countdown  = view.findViewById(R.id.text_auto_advance);
        MaterialButton cancelBtn = view.findViewById(R.id.button_cancel_confirmation);
        TextView initials   = view.findViewById(R.id.text_conf_driver_initials);
        TextView nameText   = view.findViewById(R.id.text_conf_driver_name);
        TextView ratingText = view.findViewById(R.id.text_conf_driver_rating);
        TextView ridesText  = view.findViewById(R.id.text_conf_driver_rides);
        TextView joinedText = view.findViewById(R.id.text_conf_driver_joined);
        View safetyBadge    = view.findViewById(R.id.badge_safety_verified);
        TextView etaText    = view.findViewById(R.id.text_conf_eta);
        ImageView vehicleIcon = view.findViewById(R.id.img_conf_vehicle_icon);
        TextView modelText  = view.findViewById(R.id.text_conf_vehicle_model);
        TextView numText    = view.findViewById(R.id.text_conf_vehicle_number);
        TextView typeText   = view.findViewById(R.id.text_conf_vehicle_type);
        TextView pickupText = view.findViewById(R.id.text_conf_pickup);
        TextView dropoffText = view.findViewById(R.id.text_conf_dropoff);
        TextView fareText   = view.findViewById(R.id.text_conf_fare);

        // Populate
        subText.setText(driverName + " is heading to you");
        initials.setText(String.valueOf(driverName.charAt(0)));
        nameText.setText(driverName);
        ratingText.setText(String.format("%.1f ★", driverRating));
        etaText.setText(String.valueOf(etaMinutes));
        modelText.setText(vehicleModel);
        numText.setText(vehicleNumber);
        typeText.setText(vehicleTypeLabel(vehicleType));
        pickupText.setText(pickup);
        dropoffText.setText(dropoff);
        fareText.setText("₹" + fare);
        setVehicleIcon(vehicleIcon, vehicleType);

        // Random driver profile stats (deterministic per driver name so the same
        // driver shows consistent numbers if the screen is recreated mid-trip).
        java.util.Random rnd = new java.util.Random(driverName.hashCode());
        int rideCount = 350 + rnd.nextInt(2400);
        ridesText.setText(String.format(java.util.Locale.US, "%,d rides", rideCount));
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int joinedYear  = currentYear - 1 - rnd.nextInt(5); // 1–5 years ago
        joinedText.setText("Since " + joinedYear);

        // ~2 in 3 drivers are "Safety Verified".
        boolean isVerified = rnd.nextInt(3) != 0;
        safetyBadge.setVisibility(isVerified ? View.VISIBLE : View.GONE);

        // Cancel button → confirm dialog → full refund and exit
        cancelBtn.setOnClickListener(v -> promptCancel());

        // Fare breakdown
        FareUtils.Breakdown bd = FareUtils.of(fare);
        ((TextView) view.findViewById(R.id.text_breakdown_base))
            .setText("₹" + bd.base);
        ((TextView) view.findViewById(R.id.text_breakdown_distance))
            .setText("₹" + bd.distance);
        ((TextView) view.findViewById(R.id.text_breakdown_fee))
            .setText("₹" + bd.fee);
        ((TextView) view.findViewById(R.id.text_breakdown_gst))
            .setText("₹" + bd.gst);
        ((TextView) view.findViewById(R.id.text_breakdown_total))
            .setText("₹" + bd.total);

        // 10-second countdown then auto-advance
        countdown.setText("Starting ride in " + COUNTDOWN_SECONDS + "...");
        handler = new Handler(Looper.getMainLooper());
        final int[] seconds = {COUNTDOWN_SECONDS};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || advanced || cancelled) return;
                seconds[0]--;
                if (seconds[0] <= 0) {
                    advance();
                } else {
                    countdown.setText("Starting ride in " + seconds[0] + "...");
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(tick, 1000);
    }

    private void promptCancel() {
        if (advanced || cancelled || !isAdded()) return;
        // Pause countdown while the dialog is open so the ride doesn't auto-start
        // out from under the prompt.
        if (handler != null) handler.removeCallbacksAndMessages(null);
        new AlertDialog.Builder(requireContext())
                .setTitle("Cancel Ride?")
                .setMessage("Cancellation is free at this stage. Your ₹" + fare
                        + " fare will be refunded to your wallet.")
                .setPositiveButton("Yes, Cancel", (d, w) -> performCancel())
                .setNegativeButton("Keep Ride", (d, w) -> resumeCountdown())
                .setOnCancelListener(d -> resumeCountdown())
                .show();
    }

    private void resumeCountdown() {
        if (advanced || cancelled || !isAdded()) return;
        // Restart from the displayed value rather than from 10 so the user
        // doesn't lose progress.
        TextView countdown = getView() != null ? getView().findViewById(R.id.text_auto_advance) : null;
        int remaining = COUNTDOWN_SECONDS;
        if (countdown != null) {
            String txt = countdown.getText().toString();
            try {
                String digits = txt.replaceAll("\\D+", "");
                if (!digits.isEmpty()) remaining = Math.max(1, Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {}
        }
        final int[] seconds = {remaining};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || advanced || cancelled) return;
                seconds[0]--;
                if (seconds[0] <= 0) {
                    advance();
                } else if (countdown != null) {
                    countdown.setText("Starting ride in " + seconds[0] + "...");
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(tick, 1000);
    }

    private void performCancel() {
        if (advanced || cancelled || !isAdded()) return;
        cancelled = true;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (!rideId.isEmpty()) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("rides").child(rideId).child("status").setValue("cancelled");
        }
        if (listener != null) listener.onConfirmationCancelled(rideId, fare);
    }

    private void advance() {
        if (advanced || !isAdded()) return;
        advanced = true;
        if (listener != null) {
            listener.onRideConfirmed(rideId, pickup, dropoff, vehicleType, fare,
                    driverName, vehicleNumber, vehicleModel, driverRating, etaMinutes,
                    pickupLat, pickupLng, dropoffLat, dropoffLng);
        }
    }

    private void setVehicleIcon(ImageView img, String type) {
        switch (type) {
            case "Bike": img.setImageResource(R.drawable.ic_motorcycle);    break;
            case "Auto": img.setImageResource(R.drawable.ic_auto_rickshaw); break;
            default:     img.setImageResource(R.drawable.ic_car_side);      break;
        }
    }

    private String vehicleTypeLabel(String type) {
        switch (type) {
            case "Bike": return "GoBike";
            case "Auto": return "GoAuto";
            default:     return "GoKab";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }
}
