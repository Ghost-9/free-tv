package io.freemytv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * The ten UI sounds. Focus and open are separate choices, stored in {@link Prefs}; the picker previews
 * each one. The WAVs live in res/raw and are kept uncompressed by build.sh so SoundPool can stream them.
 */
final class UiSound {
    static final String[] NAMES = {
            "Tick Soft", "Tock Low", "Wood Tap", "Glass Ping", "Marimba",
            "Water Drop", "Soft Pop", "Paper Tick", "Double Tick", "Chime"
    };
    static final String[] DESCRIPTIONS = {
            "almost subliminal", "warm and muted", "dry acoustic", "bright and short", "musical",
            "soft sweep", "round and quiet", "textural", "two steps", "long"
    };
    private static final int[] RAW = {
            R.raw.s01, R.raw.s02, R.raw.s03, R.raw.s04, R.raw.s05,
            R.raw.s06, R.raw.s07, R.raw.s08, R.raw.s09, R.raw.s10
    };

    private final Context context;
    private SoundPool pool;
    private final int[] ids = new int[10];

    UiSound(Context c) {
        context = c.getApplicationContext();
        try {
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (int i = 0; i < RAW.length; i++) ids[i] = pool.load(context, RAW[i], 1);
        } catch (RuntimeException e) {
            AppLog.w("ui sound unavailable: " + e.getMessage());
            release();
        }
    }

    void move() {
        play(Prefs.focusSound(context), 0.55f);
    }

    void open() {
        play(Prefs.openSound(context), 0.68f);
    }

    void preview(int index) {
        play(index, 0.6f);
    }

    private void play(int index, float volume) {
        if (pool == null || index < 0 || index >= ids.length) return;
        pool.play(ids[index], volume, volume, 1, 0, 1f);
    }

    static String name(int index) {
        return index < 0 || index >= NAMES.length ? "Off" : NAMES[index];
    }

    static String description(int index) {
        return index < 0 || index >= DESCRIPTIONS.length ? "muted" : DESCRIPTIONS[index];
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }
}
