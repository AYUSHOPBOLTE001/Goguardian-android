package com.goguardian.ui.rider;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.goguardian.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class TopUpBottomSheet extends BottomSheetDialogFragment {

    private long selectedAmount = 0;
    private MaterialCardView activePreset = null;
    private TextView currentBalanceText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_topup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentBalanceText = view.findViewById(R.id.text_current_balance);
        TextInputEditText customInput = view.findViewById(R.id.input_custom_amount);
        MaterialButton addButton = view.findViewById(R.id.button_add_money);

        MaterialCardView p100  = view.findViewById(R.id.preset_100);
        MaterialCardView p250  = view.findViewById(R.id.preset_250);
        MaterialCardView p500  = view.findViewById(R.id.preset_500);
        MaterialCardView p1000 = view.findViewById(R.id.preset_1000);

        loadCurrentBalance();

        // Preset taps
        View.OnClickListener presetClick = v -> {
            customInput.setText("");
            long amount = 0;
            if (v.getId() == R.id.preset_100)  amount = 100;
            if (v.getId() == R.id.preset_250)  amount = 250;
            if (v.getId() == R.id.preset_500)  amount = 500;
            if (v.getId() == R.id.preset_1000) amount = 1000;
            selectedAmount = amount;
            highlightPreset((MaterialCardView) v, p100, p250, p500, p1000);
        };
        p100.setOnClickListener(presetClick);
        p250.setOnClickListener(presetClick);
        p500.setOnClickListener(presetClick);
        p1000.setOnClickListener(presetClick);

        // Custom input overrides preset selection
        customInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String txt = s.toString().trim();
                if (!txt.isEmpty()) {
                    try {
                        selectedAmount = Long.parseLong(txt);
                        clearPresetHighlight(p100, p250, p500, p1000);
                    } catch (NumberFormatException e) {
                        selectedAmount = 0;
                    }
                } else {
                    selectedAmount = 0;
                }
            }
        });

        addButton.setOnClickListener(v -> {
            if (selectedAmount <= 0) {
                Toast.makeText(requireContext(), "Select or enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedAmount > 50000) {
                Toast.makeText(requireContext(), "Maximum top-up is ₹50,000", Toast.LENGTH_SHORT).show();
                return;
            }
            addMoney(selectedAmount);
        });
    }

    private void loadCurrentBalance() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseDatabase.getInstance().getReference("users")
            .child(user.getUid()).child("balance")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!isAdded()) return;
                    Long bal = snapshot.getValue(Long.class);
                    currentBalanceText.setText("₹" + String.format(Locale.US, "%,d", bal != null ? bal : 0));
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
    }

    private void addMoney(long amount) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        var balRef = FirebaseDatabase.getInstance()
            .getReference("users").child(user.getUid()).child("balance");
        balRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                Long current = snapshot.getValue(Long.class);
                long newBalance = (current != null ? current : 0) + amount;
                balRef.setValue(newBalance);
                Toast.makeText(requireContext(),
                    "₹" + String.format(Locale.US, "%,d", amount) + " added! New balance: ₹"
                    + String.format(Locale.US, "%,d", newBalance),
                    Toast.LENGTH_LONG).show();
                dismiss();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void highlightPreset(MaterialCardView selected,
                                  MaterialCardView... all) {
        for (MaterialCardView c : all) {
            c.setStrokeColor(requireContext().getColor(R.color.border));
            c.setCardBackgroundColor(requireContext().getColor(R.color.surface_variant));
        }
        selected.setStrokeColor(requireContext().getColor(R.color.primary));
        selected.setCardBackgroundColor(requireContext().getColor(R.color.primary_container));
        activePreset = selected;
    }

    private void clearPresetHighlight(MaterialCardView... all) {
        for (MaterialCardView c : all) {
            c.setStrokeColor(requireContext().getColor(R.color.border));
            c.setCardBackgroundColor(requireContext().getColor(R.color.surface_variant));
        }
        activePreset = null;
    }
}
