package com.goguardian.util;

import android.view.HapticFeedbackConstants;
import android.view.View;

public class HapticUtils {
    public static void feedback(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public static void feedbackSuccess(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public static void feedbackError(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}
