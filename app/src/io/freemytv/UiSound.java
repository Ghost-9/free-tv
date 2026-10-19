package io.freemytv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * The two UI sounds chosen for Free TV: 01 Tick Soft on focus, 04 Glass Ping on open. Both are 48 kHz mono
 * WAVs in res/raw, kept uncompressed by build.sh so SoundPool can stream them. Quiet on purpose; the TV's
 * own volume still applies.
 */
final class UiSound {
    private SoundPool pool;
    private int move, open;

    UiSound(Context c) {
        try {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            move = pool.load(c, R.raw.focus_tick, 1);
            open = pool.load(c, R.raw.open_ping, 1);
        } catch (RuntimeException e) {
            AppLog.w("ui sound unavailable: " + e.getMessage());
            release();
        }
    }

    void move() {
        if (pool != null) pool.play(move, 0.55f, 0.55f, 1, 0, 1f);
    }

    void open() {
        if (pool != null) pool.play(open, 0.68f, 0.68f, 1, 0, 1f);
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }
}
