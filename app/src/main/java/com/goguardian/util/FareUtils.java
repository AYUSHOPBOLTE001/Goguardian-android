package com.goguardian.util;

/** Splits a single fare total into a plausible breakdown for display. Demo-only. */
public final class FareUtils {

    public static final int FEE_FLAT = 5; // GoGuardian platform fee

    public static class Breakdown {
        public final int base;
        public final int distance;
        public final int fee;
        public final int gst;
        public final int total;
        Breakdown(int base, int distance, int fee, int gst, int total) {
            this.base = base;
            this.distance = distance;
            this.fee = fee;
            this.gst = gst;
            this.total = total;
        }
    }

    /** Splits {@code total} into base (≈40%), distance (≈45%), flat fee, and 5% GST.
     *  Components are rounded to ints; base absorbs rounding so the parts always
     *  sum exactly to {@code total}. */
    public static Breakdown of(int total) {
        if (total <= 0) return new Breakdown(0, 0, 0, 0, 0);
        int gst      = Math.round(total * 0.05f);
        int fee      = Math.min(FEE_FLAT, Math.max(0, total - gst));
        int remaining = Math.max(0, total - gst - fee);
        int distance = Math.round(remaining * 0.55f);
        int base     = remaining - distance; // absorbs rounding
        return new Breakdown(base, distance, fee, gst, total);
    }

    private FareUtils() {}
}
