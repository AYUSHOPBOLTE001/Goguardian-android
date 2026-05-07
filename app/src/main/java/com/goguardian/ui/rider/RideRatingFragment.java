package com.goguardian.ui.rider;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class RideRatingFragment extends Fragment {

    private static final String KEY_RIDE_ID     = "ride_id";
    private static final String KEY_DRIVER_NAME = "driver_name";
    private static final String KEY_VEH_MODEL   = "veh_model";
    private static final String KEY_FARE        = "fare";

    public interface OnRatingActionListener {
        void onRatingDone();
    }

    private OnRatingActionListener listener;
    private int selectedStars = 0;
    private ImageView[] stars = new ImageView[5];
    private final boolean[] activeTags = new boolean[4];

    public static RideRatingFragment newInstance(String rideId, String driverName,
                                                 String vehicleModel, int fare) {
        Bundle args = new Bundle();
        args.putString(KEY_RIDE_ID, rideId);
        args.putString(KEY_DRIVER_NAME, driverName);
        args.putString(KEY_VEH_MODEL, vehicleModel);
        args.putInt(KEY_FARE, fare);
        RideRatingFragment f = new RideRatingFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnRatingActionListener) {
            listener = (OnRatingActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ride_rating, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String rideId     = args.getString(KEY_RIDE_ID, "");
        String driverName = args.getString(KEY_DRIVER_NAME, "Driver");
        String vehModel   = args.getString(KEY_VEH_MODEL, "");
        int fare          = args.getInt(KEY_FARE, 0);

        // Bind views
        TextView summaryText  = view.findViewById(R.id.text_rating_summary);
        TextView initialText  = view.findViewById(R.id.text_rating_driver_initial);
        TextView nameText     = view.findViewById(R.id.text_rating_driver_name);
        TextView vehicleText  = view.findViewById(R.id.text_rating_vehicle);
        TextView fareText     = view.findViewById(R.id.text_rating_fare);
        MaterialButton submit = view.findViewById(R.id.button_submit_rating);
        TextView skip         = view.findViewById(R.id.button_skip_rating);

        stars[0] = view.findViewById(R.id.star_1);
        stars[1] = view.findViewById(R.id.star_2);
        stars[2] = view.findViewById(R.id.star_3);
        stars[3] = view.findViewById(R.id.star_4);
        stars[4] = view.findViewById(R.id.star_5);

        TextView[] tags = {
            view.findViewById(R.id.tag_safe),
            view.findViewById(R.id.tag_clean),
            view.findViewById(R.id.tag_friendly),
            view.findViewById(R.id.tag_ontime)
        };

        // Populate
        summaryText.setText("You arrived safely with " + driverName + ".");
        initialText.setText(String.valueOf(driverName.charAt(0)));
        nameText.setText(driverName);
        vehicleText.setText(vehModel.isEmpty() ? "GoGuardian Driver" : vehModel);
        fareText.setText("₹" + fare);

        // Star tap
        for (int i = 0; i < stars.length; i++) {
            final int starIndex = i + 1;
            stars[i].setOnClickListener(v -> {
                selectedStars = starIndex;
                updateStars(starIndex);
                summaryText.setText(starLabel(starIndex));
                submit.setEnabled(true);
                submit.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.primary)));
            });
        }

        // Tag toggles
        for (int i = 0; i < tags.length; i++) {
            final int idx = i;
            tags[i].setOnClickListener(v -> {
                activeTags[idx] = !activeTags[idx];
                tags[idx].setAlpha(activeTags[idx] ? 1f : 0.5f);
                tags[idx].setTextColor(requireContext().getColor(
                    activeTags[idx] ? R.color.primary : R.color.text_secondary));
            });
        }

        submit.setOnClickListener(v -> {
            saveRating(rideId, selectedStars);
            done();
        });

        skip.setOnClickListener(v -> done());
    }

    private void updateStars(int count) {
        for (int i = 0; i < stars.length; i++) {
            boolean filled = i < count;
            stars[i].setColorFilter(requireContext().getColor(
                filled ? R.color.warning : R.color.border_strong));

            if (filled) {
                ScaleAnimation anim = new ScaleAnimation(0.7f, 1.15f, 0.7f, 1.15f,
                        ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                        ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
                anim.setDuration(180);
                anim.setFillAfter(false);
                stars[i].startAnimation(anim);
            }
        }
    }

    private String starLabel(int n) {
        switch (n) {
            case 1: return "Very bad experience.";
            case 2: return "Below expectations.";
            case 3: return "It was okay.";
            case 4: return "Great ride! Thanks.";
            case 5: return "Excellent! 5 stars.";
            default: return "You arrived safely.";
        }
    }

    private void saveRating(String rideId, int stars) {
        if (rideId.isEmpty()) return;
        FirebaseDatabase.getInstance()
            .getReference("rides").child(rideId).child("driverRating")
            .setValue(stars);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid())
                .child("lastRating").setValue(stars);
        }

        Toast.makeText(requireContext(),
            stars == 5 ? "Thanks! 5 stars saved." : "Rating saved. Thanks for the feedback!",
            Toast.LENGTH_SHORT).show();
    }

    private void done() {
        if (listener != null) listener.onRatingDone();
    }
}
