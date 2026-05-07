package com.goguardian.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.goguardian.R;
import com.goguardian.BuildConfig;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SplashLoginFragment extends Fragment {

    private static final String ADMIN_EMAIL = BuildConfig.ADMIN_EMAIL;

    public interface OnLoginActionListener {
        void onLoginAsRider();

        void onLoginAsAdmin();

        void onGoogleLoginSuccess(String email);
    }

    private OnLoginActionListener listener;
    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private TextInputEditText inputName;
    private TextInputEditText inputEmail;
    private TextInputEditText inputPassword;
    private TextInputEditText inputPhone;
    private View layoutName;
    private View layoutPhone;
    private TextView textAuthTitle;
    private TextView textSwitchAuth;
    private MaterialButton buttonAuth;
    private boolean isLoginMode = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    showToast("Google sign-in cancelled.");
                    return;
                }

                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account == null || account.getIdToken() == null) {
                        showToast("Google sign-in failed. Missing ID token.");
                        return;
                    }
                    firebaseAuthWithGoogle(account.getIdToken());
                } catch (ApiException e) {
                    showToast("Google sign-in failed: " + e.getStatusCode());
                }
            });

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof OnLoginActionListener) {
            listener = (OnLoginActionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firebaseAuth = FirebaseAuth.getInstance();

        String webClientId = resolveWebClientId();
        if (webClientId == null) {
            showToast("Add google-services.json and enable Google Sign-In in Firebase.");
        } else {
            GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(
                    GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(requireContext(), googleSignInOptions);
        }

        inputName = view.findViewById(R.id.input_name);
        inputEmail = view.findViewById(R.id.input_email);
        inputPassword = view.findViewById(R.id.input_password);
        inputPhone = view.findViewById(R.id.input_phone);
        layoutName = view.findViewById(R.id.layout_name);
        layoutPhone = view.findViewById(R.id.layout_phone);
        textAuthTitle = view.findViewById(R.id.text_auth_title);
        textSwitchAuth = view.findViewById(R.id.text_switch_auth);
        buttonAuth = view.findViewById(R.id.button_auth);

        buttonAuth.setOnClickListener(v -> {
            if (isLoginMode) {
                loginWithEmailPassword();
            } else {
                registerWithEmailPasswordAndPhone();
            }
        });

        textSwitchAuth.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateAuthUI();
        });

        view.findViewById(R.id.button_google_login).setOnClickListener(v -> {
            if (googleSignInClient == null) {
                showToast("Google login is not configured yet.");
                return;
            }
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        });
    }

    private void updateAuthUI() {
        if (isLoginMode) {
            textAuthTitle.setText("LOGIN");
            buttonAuth.setText("Login");
            textSwitchAuth.setText("Don't have an account? Sign Up");
            layoutName.setVisibility(View.GONE);
            layoutPhone.setVisibility(View.GONE);
        } else {
            textAuthTitle.setText("SIGN UP");
            buttonAuth.setText("Register");
            textSwitchAuth.setText("Already have an account? Login");
            layoutName.setVisibility(View.VISIBLE);
            layoutPhone.setVisibility(View.VISIBLE);
        }
    }

    private void loginWithEmailPassword() {
        String email = readText(inputEmail);
        String password = readText(inputPassword);

        if (email.isEmpty() || password.isEmpty()) {
            showToast("Please enter email and password");
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful() && task.getResult().getUser() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        checkUserRoleAndNavigate(user);
                    } else {
                        showToast("Login failed: "
                                + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                    }
                });
    }

    private void checkUserRoleAndNavigate(FirebaseUser user) {
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("role")
                .get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        String role = task.getResult().getValue(String.class);
                        if ("admin".equals(role)) {
                            if (listener != null)
                                listener.onLoginAsAdmin();
                        } else {
                            if (listener != null)
                                listener.onLoginAsRider();
                        }
                    } else {
                        // No DB record yet (first login with email/password).
                        // saveUserRoleAfterLogin will detect admin email and set role correctly.
                        saveUserRoleAfterLogin(user, null, null);
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (!task.isSuccessful()) {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        showToast("Authentication failed: " + error);
                        return;
                    }

                    FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                    if (user == null || user.getUid() == null) {
                        showToast("Signed-in user details are incomplete.");
                        return;
                    }

                    saveUserRoleAfterLogin(user, null, null);
                });
    }

    private void registerWithEmailPasswordAndPhone() {
        String name = readText(inputName);
        String email = readText(inputEmail).toLowerCase(Locale.US);
        String password = readText(inputPassword);
        String phone = readText(inputPhone);

        if (TextUtils.isEmpty(name)) {
            showToast("Enter your full name.");
            return;
        }
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            showToast("Enter a valid email.");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            showToast("Password must be at least 6 characters.");
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            showToast("Enter phone number.");
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (!task.isSuccessful()) {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        showToast("Registration failed: " + error);
                        return;
                    }

                    FirebaseUser user = task.getResult() != null ? task.getResult().getUser() : null;
                    if (user == null) {
                        showToast("Registration completed but user info is unavailable.");
                        return;
                    }

                    saveUserRoleAfterLogin(user, name, phone);
                });
    }

    private void saveUserRoleAfterLogin(FirebaseUser user, @Nullable String nameOverride,
            @Nullable String phoneOverride) {
        String rawEmail = user.getEmail() != null ? user.getEmail().trim() : "";
        String normalizedEmail = rawEmail.toLowerCase(Locale.US);

        // Admin credentials override
        String finalName = nameOverride;
        String finalPhone = phoneOverride;
        String role = "rider";

        if (ADMIN_EMAIL.equalsIgnoreCase(normalizedEmail)) {
            role = "admin";
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", user.getUid());
        userData.put("email", normalizedEmail);

        String phoneValue = !TextUtils.isEmpty(finalPhone) ? finalPhone
                : (user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
        userData.put("phone", phoneValue);

        String nameValue = !TextUtils.isEmpty(finalName) ? finalName
                : (user.getDisplayName() != null ? user.getDisplayName() : "");
        userData.put("name", nameValue);
        userData.put("photoUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        userData.put("role", role);
        userData.put("updatedAt", ServerValue.TIMESTAMP);

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.getUid());

        // Add 1000 balance if it doesn't exist (initial signup/first login)
        userRef.child("balance").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    userData.put("balance", 1000);
                }
                userRef.updateChildren(userData).addOnCompleteListener(writeTask -> {
                    if (!writeTask.isSuccessful()) {
                        if (isAdded())
                            showToast("Failed to save profile. Try again.");
                        return;
                    }
                    if (listener != null) {
                        listener.onGoogleLoginSuccess(normalizedEmail);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback to update without balance check if cancelled
                userRef.updateChildren(userData);
            }
        });
    }

    private String readText(@Nullable TextInputEditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    private String resolveWebClientId() {
        int resourceId = getResources().getIdentifier("default_web_client_id", "string",
                requireContext().getPackageName());
        if (resourceId == 0) {
            return null;
        }
        String value = getString(resourceId);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
