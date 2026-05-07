package com.goguardian.ui.rider;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.goguardian.util.FareUtils;
import com.google.android.material.button.MaterialButton;

/**
 * Post-ride trip receipt. Shows driver recap, fare breakdown, and a payment line.
 * Continues to the rating screen.
 */
public class TripReceiptFragment extends Fragment {

    private static final String KEY_RIDE_ID     = "ride_id";
    private static final String KEY_DRIVER_NAME = "driver_name";
    private static final String KEY_VEH_MODEL   = "veh_model";
    private static final String KEY_FARE        = "fare";

    public interface OnReceiptActionListener {
        void onReceiptDone(String rideId, int fare, String driverName, String vehicleModel);
    }

    private OnReceiptActionListener listener;

    public static TripReceiptFragment newInstance(String rideId, String driverName,
                                                  String vehicleModel, int fare) {
        Bundle args = new Bundle();
        args.putString(KEY_RIDE_ID, rideId);
        args.putString(KEY_DRIVER_NAME, driverName);
        args.putString(KEY_VEH_MODEL, vehicleModel);
        args.putInt(KEY_FARE, fare);
        TripReceiptFragment f = new TripReceiptFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnReceiptActionListener) {
            listener = (OnReceiptActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trip_receipt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String rideId       = args.getString(KEY_RIDE_ID, "");
        String driverName   = args.getString(KEY_DRIVER_NAME, "Driver");
        String vehicleModel = args.getString(KEY_VEH_MODEL, "");
        int fare            = args.getInt(KEY_FARE, 0);

        ((TextView) view.findViewById(R.id.text_receipt_subtitle))
            .setText("You arrived safely with " + driverName);
        ((TextView) view.findViewById(R.id.text_receipt_driver_initial))
            .setText(driverName.isEmpty() ? "?" : String.valueOf(driverName.charAt(0)));
        ((TextView) view.findViewById(R.id.text_receipt_driver_name))
            .setText(driverName);
        ((TextView) view.findViewById(R.id.text_receipt_vehicle))
            .setText(vehicleModel.isEmpty() ? "GoGuardian Driver" : vehicleModel);
        ((TextView) view.findViewById(R.id.text_receipt_total_pill))
            .setText("₹" + fare);

        FareUtils.Breakdown bd = FareUtils.of(fare);
        ((TextView) view.findViewById(R.id.text_receipt_base)).setText("₹" + bd.base);
        ((TextView) view.findViewById(R.id.text_receipt_distance)).setText("₹" + bd.distance);
        ((TextView) view.findViewById(R.id.text_receipt_fee)).setText("₹" + bd.fee);
        ((TextView) view.findViewById(R.id.text_receipt_gst)).setText("₹" + bd.gst);
        ((TextView) view.findViewById(R.id.text_receipt_total)).setText("₹" + bd.total);

        MaterialButton download = view.findViewById(R.id.button_receipt_download);
        download.setOnClickListener(v ->
            Toast.makeText(requireContext(), "Receipt saved to your phone", Toast.LENGTH_SHORT).show());

        MaterialButton cont = view.findViewById(R.id.button_receipt_continue);
        cont.setOnClickListener(v -> {
            if (listener != null) listener.onReceiptDone(rideId, fare, driverName, vehicleModel);
        });
    }
}
