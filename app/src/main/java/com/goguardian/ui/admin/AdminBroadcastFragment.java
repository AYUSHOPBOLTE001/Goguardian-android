package com.goguardian.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class AdminBroadcastFragment extends Fragment {

    private TextInputEditText inputTitle;
    private TextInputEditText inputMessage;
    private MaterialButton buttonSend;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_broadcast, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        inputTitle = view.findViewById(R.id.input_broadcast_title);
        inputMessage = view.findViewById(R.id.input_broadcast_message);
        buttonSend = view.findViewById(R.id.button_send_broadcast);

        buttonSend.setOnClickListener(v -> sendBroadcast());
    }

    private void sendBroadcast() {
        String title = inputTitle.getText() != null ? inputTitle.getText().toString().trim() : "";
        String message = inputMessage.getText() != null ? inputMessage.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonSend.setEnabled(false);
        buttonSend.setText("Sending...");

        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("title", title);
        broadcast.put("message", message);
        broadcast.put("timestamp", ServerValue.TIMESTAMP);

        FirebaseDatabase.getInstance().getReference("broadcasts")
                .push()
                .setValue(broadcast)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Broadcast sent!", Toast.LENGTH_SHORT).show();
                    inputTitle.setText("");
                    inputMessage.setText("");
                    buttonSend.setEnabled(true);
                    buttonSend.setText("Send Broadcast");
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    buttonSend.setEnabled(true);
                    buttonSend.setText("Send Broadcast");
                });
    }
}
