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
import com.google.android.material.button.MaterialButton;

/**
 * Single-question post-ride safety check. All exits ({@code Yes}, {@code No},
 * {@code Skip}) call {@link OnSafetyCheckActionListener#onSafetyCheckDone()},
 * which the host should treat as "done — go home."
 */
public class SafetyCheckFragment extends Fragment {

    public interface OnSafetyCheckActionListener {
        void onSafetyCheckDone();
    }

    private OnSafetyCheckActionListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSafetyCheckActionListener) {
            listener = (OnSafetyCheckActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_safety_check, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton yes = view.findViewById(R.id.button_safety_yes);
        MaterialButton no  = view.findViewById(R.id.button_safety_no);
        TextView skip      = view.findViewById(R.id.button_safety_skip);

        yes.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Thanks — glad you arrived safely.",
                Toast.LENGTH_SHORT).show();
            done();
        });
        no.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                "Report sent to GoGuardian Safety. We'll follow up.",
                Toast.LENGTH_LONG).show();
            done();
        });
        skip.setOnClickListener(v -> done());
    }

    private void done() {
        if (listener != null) listener.onSafetyCheckDone();
    }
}
