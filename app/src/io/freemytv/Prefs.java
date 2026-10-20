package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

/** Small, shared UI settings: sound choice, motion and clock. Written by Settings, read by Home. */
final class Prefs {
    private static final String FILE = "free-tv-ui";
    static final int OFF = -1;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int focusSound(Context c) {
        return clamp(get(c).getInt("sound_focus", 0));
    }

    static int openSound(Context c) {
        return clamp(get(c).getInt("sound_open", 3));
    }

    static void setFocusSound(Context c, int value) {
        get(c).edit().putInt("sound_focus", clamp(value)).apply();
    }

    static void setOpenSound(Context c, int value) {
        get(c).edit().putInt("sound_open", clamp(value)).apply();
    }

    static boolean reducedMotion(Context c) {
        return get(c).getBoolean("motion_reduced", false);
    }

    static void setReducedMotion(Context c, boolean value) {
        get(c).edit().putBoolean("motion_reduced", value).apply();
    }

    static boolean clock24(Context c) {
        return get(c).getBoolean("clock_24", true);
    }

    static void setClock24(Context c, boolean value) {
        get(c).edit().putBoolean("clock_24", value).apply();
    }

    private static int clamp(int v) {
        return v < -1 ? -1 : Math.min(v, 9);
    }
}
