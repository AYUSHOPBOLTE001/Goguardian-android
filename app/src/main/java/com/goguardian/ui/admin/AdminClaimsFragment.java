package com.goguardian.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminClaimsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ClaimsAdapter adapter;
    private DatabaseReference ridesRef;
    private DatabaseReference sosRef;
    private ValueEventListener claimsListener;
    private ValueEventListener sosListener;
    private List<ClaimItem> cancellationClaims = new ArrayList<>();
    private List<ClaimItem> sosClaims = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_claims, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_claims);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ClaimsAdapter();
        recyclerView.setAdapter(adapter);

        ridesRef = FirebaseDatabase.getInstance().getReference("rides");
        claimsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                cancellationClaims.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String status = ds.child("status").getValue(String.class);
                    if ("cancelled".equals(status)) {
                        Long fareVal = ds.child("fare").getValue(Long.class);
                        cancellationClaims.add(new ClaimItem(
                                ClaimKind.REFUND,
                                ds.getKey(),
                                ds.child("riderUid").getValue(String.class),
                                ds.child("pickup").getValue(String.class),
                                ds.child("dropoff").getValue(String.class),
                                fareVal,
                                "Cancellation Review"
                        ));
                    }
                }
                publishClaims();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load claims", Toast.LENGTH_SHORT).show();
            }
        };
        ridesRef.orderByChild("status").addValueEventListener(claimsListener);

        sosRef = FirebaseDatabase.getInstance().getReference("sos_logs");
        sosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                sosClaims.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String status = ds.child("status").getValue(String.class);
                    if (status == null || "open".equals(status)) {
                        String email = ds.child("email").getValue(String.class);
                        String uid = ds.child("uid").getValue(String.class);
                        sosClaims.add(new ClaimItem(
                                ClaimKind.SOS,
                                ds.getKey(),
                                uid,
                                email != null ? email : (uid != null ? uid : "anonymous"),
                                "SOS triggered",
                                0L,
                                "Emergency SOS"
                        ));
                    }
                }
                publishClaims();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) Toast.makeText(requireContext(), "Failed to load SOS logs", Toast.LENGTH_SHORT).show();
            }
        };
        sosRef.addValueEventListener(sosListener);
    }

    private void publishClaims() {
        List<ClaimItem> all = new ArrayList<>(sosClaims.size() + cancellationClaims.size());
        all.addAll(sosClaims);            // SOS first — higher priority
        all.addAll(cancellationClaims);
        adapter.setClaims(all);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ridesRef != null && claimsListener != null) {
            ridesRef.removeEventListener(claimsListener);
        }
        if (sosRef != null && sosListener != null) {
            sosRef.removeEventListener(sosListener);
        }
    }

    enum ClaimKind { REFUND, SOS }

    static class ClaimItem {
        public ClaimKind kind;
        public String rideId, riderUid, pickup, dropoff, type;
        public long fare;

        ClaimItem(ClaimKind k, String id, String uid, String p, String d, Long f, String t) {
            kind = k;
            rideId = id;
            riderUid = uid;
            pickup = p != null ? p : "Unknown";
            dropoff = d != null ? d : "Unknown";
            fare = f != null ? f : 0;
            type = t;
        }
    }

    class ClaimsAdapter extends RecyclerView.Adapter<ClaimsAdapter.ClaimViewHolder> {
        private List<ClaimItem> claims = new ArrayList<>();

        public void setClaims(List<ClaimItem> claims) {
            this.claims = claims;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ClaimViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_claim, parent, false);
            return new ClaimViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ClaimViewHolder holder, int position) {
            ClaimItem claim = claims.get(position);
            String shortId = claim.rideId != null && claim.rideId.length() > 8
                    ? claim.rideId.substring(0, 8) : claim.rideId;
            holder.textRideId.setText((claim.kind == ClaimKind.SOS ? "SOS #" : "#") + shortId);
            holder.textRoute.setText(claim.pickup + " → " + claim.dropoff);
            holder.textReason.setText(claim.type);
            holder.buttonRefund.setEnabled(true);

            if (claim.kind == ClaimKind.SOS) {
                holder.textFare.setText("URGENT");
                holder.buttonRefund.setText("Mark Resolved");
                holder.buttonRefund.setOnClickListener(v -> {
                    if (!holder.buttonRefund.isEnabled()) return;
                    holder.buttonRefund.setEnabled(false);
                    holder.buttonRefund.setText("Resolving…");
                    FirebaseDatabase.getInstance().getReference("sos_logs")
                            .child(claim.rideId).child("status").setValue("resolved");
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "SOS marked resolved", Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }

            holder.textFare.setText("₹" + claim.fare);
            holder.buttonRefund.setText("Refund ₹" + claim.fare);

            holder.buttonRefund.setOnClickListener(v -> {
                if (!holder.buttonRefund.isEnabled()) return;
                holder.buttonRefund.setEnabled(false);
                holder.buttonRefund.setText("Processing…");

                if (claim.riderUid == null || claim.riderUid.isEmpty()) {
                    Toast.makeText(requireContext(), "Cannot refund: no rider ID", Toast.LENGTH_SHORT).show();
                    return;
                }

                DatabaseReference balRef = FirebaseDatabase.getInstance()
                        .getReference("users").child(claim.riderUid).child("balance");
                balRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded()) return;
                        Long current = snapshot.getValue(Long.class);
                        balRef.setValue((current != null ? current : 0) + claim.fare);
                        // Mark as refunded so it disappears from the list
                        FirebaseDatabase.getInstance().getReference("rides")
                                .child(claim.rideId).child("status").setValue("refunded");
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "Refund of ₹" + claim.fare + " processed!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (!isAdded()) return;
                        holder.buttonRefund.setEnabled(true);
                        holder.buttonRefund.setText("Refund ₹" + claim.fare);
                        Toast.makeText(requireContext(), "Refund failed, try again", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return claims.size();
        }

        class ClaimViewHolder extends RecyclerView.ViewHolder {
            TextView textRideId, textRoute, textFare, textReason;
            MaterialButton buttonRefund;

            ClaimViewHolder(@NonNull View v) {
                super(v);
                textRideId = v.findViewById(R.id.text_claim_ride_id);
                textRoute = v.findViewById(R.id.text_claim_route);
                textFare = v.findViewById(R.id.text_claim_fare);
                textReason = v.findViewById(R.id.text_claim_reason);
                buttonRefund = v.findViewById(R.id.button_process_refund);
            }
        }
    }
}
