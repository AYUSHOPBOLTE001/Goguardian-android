package com.goguardian.data;

import android.content.Context;
import android.content.SharedPreferences;

public class DemoSessionManager {

    private static final String PREFS            = "goguardian_session";
    private static final String KEY_LOGGED_IN    = "logged_in";
    private static final String KEY_IS_ADMIN     = "is_admin";
    private static final String KEY_ONBOARDING   = "onboarding_shown";

    private final SharedPreferences sharedPreferences;

    public DemoSessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        // Both SharedPreferences and Firebase Auth must agree the user is logged in.
        // This prevents a stale SharedPreferences flag from bypassing the login screen
        // after a Firebase session expires or after an explicit Firebase sign-out.
        return sharedPreferences.getBoolean(KEY_LOGGED_IN, false)
                && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public boolean isAdmin() {
        return sharedPreferences.getBoolean(KEY_IS_ADMIN, false);
    }

    public void signIn(boolean admin) {
        sharedPreferences.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putBoolean(KEY_IS_ADMIN, admin)
                .apply();
    }

    public boolean isOnboardingShown() {
        return sharedPreferences.getBoolean(KEY_ONBOARDING, false);
    }

    public void markOnboardingShown() {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING, true).apply();
    }

    public void signOut() {
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        sharedPreferences.edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .putBoolean(KEY_IS_ADMIN, false)
                .apply();
    }
}
