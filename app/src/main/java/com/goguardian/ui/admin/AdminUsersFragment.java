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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminUsersFragment extends Fragment {

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private DatabaseReference usersRef;
    private ValueEventListener usersListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_users);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UserAdapter();
        recyclerView.setAdapter(adapter);

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                List<UserData> users = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    UserData user = ds.getValue(UserData.class);
                    if (user != null) {
                        user.uid = ds.getKey();
                        // Don't list admin accounts in the user directory
                        if (!"admin".equals(user.role)) {
                            users.add(user);
                        }
                    }
                }
                adapter.setUsers(users);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load users", Toast.LENGTH_SHORT).show();
                }
            }
        };
        usersRef.addValueEventListener(usersListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (usersRef != null && usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }
    }

    static class UserData {
        public String uid;
        public String name;
        public String email;
        public String role;
        public long balance;
    }

    class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private List<UserData> users = new ArrayList<>();

        public void setUsers(List<UserData> users) {
            this.users = users;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_user, parent, false);
            return new UserViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            UserData user = users.get(position);
            String displayName = (user.name != null && !user.name.isEmpty()) ? user.name : "Anonymous";
            holder.textName.setText(displayName);
            holder.textEmail.setText(user.email != null ? user.email : "No Email");
            holder.textBalance.setText("₹" + String.format(Locale.US, "%,d", user.balance));
            holder.textInitials.setText(String.valueOf(Character.toUpperCase(displayName.charAt(0))));
            holder.itemView.setOnClickListener(v -> openUserDetail(user.uid));
        }

        private void openUserDetail(String uid) {
            if (uid == null || uid.isEmpty() || !isAdded()) return;
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out,
                            R.anim.fragment_fade_in, R.anim.fragment_fade_out)
                    .replace(R.id.fragment_container, AdminUserDetailFragment.newInstance(uid))
                    .addToBackStack("user_detail")
                    .commit();
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView textName, textEmail, textBalance, textInitials;

            UserViewHolder(@NonNull View v) {
                super(v);
                textName     = v.findViewById(R.id.text_user_name);
                textEmail    = v.findViewById(R.id.text_user_email);
                textBalance  = v.findViewById(R.id.text_user_balance);
                textInitials = v.findViewById(R.id.text_user_initials);
            }
        }
    }
}
