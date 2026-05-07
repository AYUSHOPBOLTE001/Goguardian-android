package com.goguardian.ui.common;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SosFragment extends Fragment {

    public interface OnSosActionListener {
        void onSosClosed();
    }

    private OnSosActionListener listener;
    private Handler holdHandler;
    private Runnable holdRunnable;
    private Runnable pulseRunnable;
    private boolean sosTriggered = false;
    private static final long PULSE_INTERVAL_MS = 400L;
    private static final long HOLD_DURATION_MS  = 2000L;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSosActionListener) {
            listener = (OnSosActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        holdHandler = new Handler(Looper.getMainLooper());

        View backButton = view.findViewById(R.id.button_sos_back);
        MaterialCardView sosButton = view.findViewById(R.id.button_sos_trigger);
        TextView sosFeedback = view.findViewById(R.id.text_sos_feedback);
        SwitchMaterial locationSwitch = view.findViewById(R.id.switch_live_location);
        TextView locationStatus = view.findViewById(R.id.text_location_status);
        MaterialButton shareLocationBtn = view.findViewById(R.id.button_share_location);
        MaterialButton sendSmsBtn = view.findViewById(R.id.button_send_sms);
        MaterialButton callPolice = view.findViewById(R.id.button_call_police);
        MaterialButton callAmbulance = view.findViewById(R.id.button_call_ambulance);

        backButton.setOnClickListener(v -> close());

        // Hold-to-trigger SOS with pulsing haptic feedback
        sosButton.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (sosTriggered) return true;
                    sosFeedback.setVisibility(View.VISIBLE);
                    sosFeedback.setText("Hold to confirm SOS...");
                    startPulse();
                    holdRunnable = () -> {
                        if (!isAdded()) return;
                        stopPulse();
                        triggerSos(sosFeedback);
                    };
                    holdHandler.postDelayed(holdRunnable, HOLD_DURATION_MS);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!sosTriggered) {
                        cancelHold(sosFeedback);
                    }
                    return true;
            }
            return false;
        });

        locationSwitch.setOnCheckedChangeListener((btn, checked) -> {
            locationStatus.setText(checked
                    ? "Sharing with emergency contacts"
                    : "Share with emergency contacts");
            if (checked) {
                Toast.makeText(requireContext(),
                        "[Demo] Live location sharing enabled", Toast.LENGTH_SHORT).show();
            }
        });

        shareLocationBtn.setOnClickListener(v -> {
            locationSwitch.setChecked(true);
            Toast.makeText(requireContext(),
                    "[Demo] Location link sent to emergency contacts", Toast.LENGTH_LONG).show();
        });

        sendSmsBtn.setOnClickListener(v -> {
            vibrate(200);
            Toast.makeText(requireContext(),
                    "[Demo] Offline SMS sent to 3 emergency contacts with your last known location",
                    Toast.LENGTH_LONG).show();
        });

        callPolice.setOnClickListener(v ->
                Toast.makeText(requireContext(), "[Demo] Calling Police 100...", Toast.LENGTH_SHORT).show());

        callAmbulance.setOnClickListener(v ->
                Toast.makeText(requireContext(), "[Demo] Calling Ambulance 108...", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.button_add_contact).setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "[Demo] Add emergency contact — coming in v1.1", Toast.LENGTH_SHORT).show());
    }

    private void triggerSos(TextView feedback) {
        if (!isAdded()) return;
        sosTriggered = true;
        vibrate(600);
        feedback.setText("SOS ALERT SENT! Help is on the way.");
        feedback.setTextColor(requireContext().getColor(R.color.white));
        Toast.makeText(requireContext(),
                "[Demo] Emergency alert sent to contacts and GoGuardian safety team!",
                Toast.LENGTH_LONG).show();
        logSosToFirebase();
    }

    private void logSosToFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        DatabaseReference logRef = FirebaseDatabase.getInstance()
                .getReference("sos_logs").push();
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user != null ? user.getUid() : "anonymous");
        data.put("email", user != null ? user.getEmail() : null);
        data.put("triggeredAt", System.currentTimeMillis());
        data.put("status", "open");
        logRef.setValue(data);
    }

    private void startPulse() {
        stopPulse();
        pulseRunnable = new Runnable() {
            @Override public void run() {
                if (!isAdded() || sosTriggered) return;
                vibrate(80);
                holdHandler.postDelayed(this, PULSE_INTERVAL_MS);
            }
        };
        holdHandler.post(pulseRunnable);
    }

    private void stopPulse() {
        if (pulseRunnable != null) {
            holdHandler.removeCallbacks(pulseRunnable);
            pulseRunnable = null;
        }
    }

    private void cancelHold(TextView feedback) {
        stopPulse();
        if (holdRunnable != null) {
            holdHandler.removeCallbacks(holdRunnable);
            holdRunnable = null;
        }
        if (feedback != null) {
            feedback.setText("Released too soon — hold for 2 seconds");
            holdHandler.postDelayed(() -> {
                if (isAdded() && !sosTriggered) feedback.setVisibility(View.INVISIBLE);
            }, 1500);
        }
    }

    private void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}
    }

    private void close() {
        if (listener != null) listener.onSosClosed();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (holdHandler != null) holdHandler.removeCallbacksAndMessages(null);
    }
}
