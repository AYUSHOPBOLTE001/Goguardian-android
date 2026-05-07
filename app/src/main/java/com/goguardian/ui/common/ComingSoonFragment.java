package com.goguardian.ui.common;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;

public class ComingSoonFragment extends Fragment {

    public interface OnServicesActionListener {
        void onServicesClosed();
    }

    private OnServicesActionListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnServicesActionListener) {
            listener = (OnServicesActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_coming_soon, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.button_services_back).setOnClickListener(v -> {
            if (listener != null) listener.onServicesClosed();
        });

        MaterialButton notifyBtn = view.findViewById(R.id.button_notify_me);
        notifyBtn.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "You'll be notified when these services go live!",
                        Toast.LENGTH_LONG).show());
    }
}
