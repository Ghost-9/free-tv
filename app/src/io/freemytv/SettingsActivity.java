package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;

import org.json.JSONObject;

/**
 * Free TV settings. Home, screensaver, TV settings, interface sound, motion, clock, region, apps off
 * and restore. It runs in its own process and ends that process on BACK, so a finished window cannot
 * retain graphics in the home process.
 */
public class SettingsActivity extends Activity {
    private SettingsView view;
    private DreamView preview;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        if (preview == null && getIntent().getBooleanExtra("screensaver", false)) {
            getIntent().removeExtra("screensaver");
            view.pause();
            showPreview();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();
        view.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        closePreview();
        if (view != null) view.release();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_UP) closePreview();
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        private static final int MAIN = 0, HOME_CHOICE = 1, SOUND_MENU = 2, SOUND_LIST = 3, CONFIRM = 4;
        private static final float LIST_X = 980, LIST_R = 1824, LIST_TOP = 120, ROW_H = 58, ROW_GAP = 6;
        private static final float CHOICE_H = 96, CHOICE_GAP = 8;
        private static final int INK = 0xFF1C1C1E;

        private final SettingsActivity activity;
        private final Scene scene = new Scene();
        private final UiSound sound;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Spring[] pop = new Spring[11];
        private final float[] rowY = new float[11];
        private final RectF rowRect = new RectF(), tmp = new RectF();
        private JSONObject state = new JSONObject();
        private int screen = MAIN, focus, soundRole;
        private boolean running, okDown, backDown;
        private long startedAt, lastNanos, pendingAt, lastSky;
        private int w, h;
        private float k = 1;
        private Paint title, subtitle, version, rowLabel, rowValue, rowSub, header, warn, spinner, white, shadow, barPaint;
        private TextPaint notice;

        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                state = HomeMode.state(getContext());
                if (!state.optString("busy").isEmpty()) pendingAt = 0;
                invalidate();
                handler.postDelayed(this, 500);
            }
        };

        SettingsView(SettingsActivity a) {
            super(a);
            activity = a;
            sound = new UiSound(a);
            for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.28f, 0.85f, 0);
            HomeData.Weather wx = HomeData.get(a).weather;
            Shaders.sky(scene.palette, System.currentTimeMillis(), wx.sunrise, wx.sunset);
            Ui.clock24 = Prefs.clock24(a);
            scene.setMotion(Prefs.reducedMotion(a) ? 0f : 0.5f, 0.2f);
            scene.applyPalette();
            setFocusable(true);
        }

        void resume() {
            if (running) return;
            running = true;
            startedAt = SystemClock.uptimeMillis();
            lastNanos = 0;
            Ui.clock24 = Prefs.clock24(getContext());
            scene.setMotion(Prefs.reducedMotion(getContext()) ? 0f : 0.5f, 0.2f);
            handler.post(poll);
            Choreographer.getInstance().postFrameCallback(this);
        }

        void pause() {
            running = false;
            handler.removeCallbacks(poll);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        void release() {
            pause();
            sound.release();
            scene.release();
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            boolean moving = busy();
            for (Spring s : pop) moving |= !s.settled();
            if (moving || SystemClock.uptimeMillis() - lastSky >= 100) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            k = w / 1920f;
            scene.setSize(w, h);
            title = Ui.text(Ui.displaySemibold, 50 * k, 0xFFFFFFFF);
            title.setTextAlign(Paint.Align.CENTER);
            subtitle = Ui.text(Ui.regular, 26 * k, 0x99FFFFFF);
            subtitle.setTextAlign(Paint.Align.CENTER);
            version = Ui.text(Ui.regular, 22 * k, 0x66FFFFFF);
            version.setTextAlign(Paint.Align.CENTER);
            rowLabel = Ui.text(Ui.medium, 28 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 26 * k, 0x8CFFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowSub = Ui.text(Ui.regular, 21 * k, 0x8CFFFFFF);
            header = Ui.text(Ui.semibold, 20 * k, 0x73FFFFFF);
            header.setLetterSpacing(0.06f);
            warn = Ui.text(Ui.regular, 22 * k, 0xFFFFC266);
            notice = new TextPaint(Ui.text(Ui.regular, 22 * k, 0xB3FFFFFF));
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xFFF5F5F7);
            barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
        }

        private boolean busy() {
            return !state.optString("busy").isEmpty() || (pendingAt != 0 && SystemClock.uptimeMillis() - pendingAt < 10_000);
        }

        private boolean freeActive() {
            return HomeMode.FREE.equals(state.optString("mode")) && !state.optBoolean("homeReverted");
        }

        private String soundSummary(Context c) {
            int f = Prefs.focusSound(c), o = Prefs.openSound(c);
            if (f < 0 && o < 0) return Ui.tr("Desligado", "Off");
            if (f == o) return UiSound.name(f);
            return UiSound.name(f) + " + " + UiSound.name(o);
        }

        private int disabledCount() {
            return HomeMode.stringList(state, "disabled").size();
        }

        private int rowCount() {
            switch (screen) {
                case MAIN: return 10;
                case HOME_CHOICE: return 2;
                case SOUND_MENU: return 3;
                case SOUND_LIST: return 11;
                case CONFIRM: return 2;
                default: return 0;
            }
        }

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            for (int i = 0; i < pop.length; i++) {
                pop[i].target = i == focus && i < rowCount() ? 1 : 0;
                pop[i].step(dt);
            }
            long up = SystemClock.uptimeMillis();
            float seconds = (up - startedAt) / 1000f;
            if (up - lastSky >= 100) {
                lastSky = up;
                scene.advance(seconds);
            }
            scene.drawBackground(c);

            float cx = 500 * k;
            Symbols.draw(getContext(), c, "freetv", cx, 380 * k, 150 * k, 0xEBFFFFFF);
            c.drawText("Free TV", cx, 600 * k, title);
            c.drawText(Ui.tr("Ajustes do launcher", "Launcher settings"), cx, 648 * k, subtitle);
            c.drawText(versionName(getContext()), cx, 692 * k, version);
            c.drawText("Acer R10G  ·  Android 14  ·  1.81 GiB", cx, 862 * k, version);
            c.drawText(Ui.tr("5 títulos  ·  " + disabledCount() + " apps desligados",
                    "5 titles  ·  " + disabledCount() + " apps off"), cx, 900 * k, version);

            switch (screen) {
                case MAIN: drawMain(c, seconds); break;
                case HOME_CHOICE: drawChoice(c); break;
                case SOUND_MENU: drawSoundMenu(c); break;
                case SOUND_LIST: drawSoundList(c); break;
                default: drawConfirm(c); break;
            }
            drawFooter(c);
        }

        private void drawMain(Canvas c, float seconds) {
            String busyText = state.optString("busy");
            boolean working = busy();
            String mode = freeActive() ? "Free TV" : Ui.tr("Original", "Original");
            String[][] rows = {
                    {Ui.tr("Início", "Home screen"), working && busyText.isEmpty() ? Ui.tr("Trocando…", "Switching…") : mode, ""},
                    {Ui.tr("Tela de descanso", "Screensaver"), Ui.tr("Ver agora", "Preview"), ""},
                    {Ui.tr("Ajustes da TV", "TV settings"), "", ">"},
                    {Ui.tr("Som da interface", "Interface sound"), soundSummary(getContext()), ""},
                    {Ui.tr("Movimento", "Motion"), Prefs.reducedMotion(getContext()) ? Ui.tr("Reduzido", "Reduced") : Ui.tr("Completo", "Full effects"), ""},
                    {Ui.tr("Relógio", "Clock"), Prefs.clock24(getContext()) ? "24-hour" : "12-hour", ""},
                    {Ui.tr("Região", "Region"), "India", ""},
                    {Ui.tr("Atualização", "Refresh"), Ui.tr("Semanal · 5 títulos", "Weekly · 5 titles"), ""},
                    {Ui.tr("Apps desligados", "Apps off"), String.valueOf(disabledCount()), ""},
                    {Ui.tr("Restaurar original", "Restore original"), "", ">"}
            };
            String[] groups = {"HOME", "LOOK & SOUND", "NETFLIX", "SYSTEM"};
            int[] starts = {0, 3, 6, 8}, ends = {3, 6, 8, 10};
            float y = LIST_TOP * k;
            int row = 0;
            for (int g = 0; g < groups.length; g++) {
                c.drawText(groups[g], LIST_X * k, y + 16 * k, header);
                y += 28 * k;
                for (int i = starts[g]; i < ends[g]; i++) {
                    rowY[row] = y;
                    drawRow(c, row, rows[i][0], rows[i][1], ">".equals(rows[i][2]), i == 0 && working, false);
                    row++;
                    y += (ROW_H + ROW_GAP) * k;
                }
                y += 4 * k;
            }
            if (working && !busyText.isEmpty()) {
                c.drawText(TextUtils.ellipsize(busyText, notice, 780 * k, TextUtils.TruncateAt.END).toString(),
                        LIST_X * k + 30 * k, 962 * k, notice);
            }
        }

        private void drawRow(Canvas c, int i, String label, String value, boolean chevron, boolean working, boolean selected) {
            rowRect.set(LIST_X * k, rowY[i], LIST_R * k, rowY[i] + ROW_H * k);
            float f = drawRowBackground(c, i);
            int ink = blend(0xF2FFFFFF, INK, f);
            int muted = blend(0x8CFFFFFF, 0xFF6E6E73, f);
            rowLabel.setColor(ink);
            rowValue.setColor(muted);
            float base = rowRect.centerY() + Ui.halfCap(rowLabel);
            c.drawText(label, rowRect.left + 28 * k, base, rowLabel);
            float right = rowRect.right - 28 * k;
            if (chevron) {
                float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                Symbols.draw(getContext(), c, "chevron_right", right - cw / 2, rowRect.centerY(), 22 * k,
                        blend(0x66FFFFFF, 0x991C1C1E, f));
            } else if (selected) {
                float cw = Symbols.width(getContext(), "check", 26 * k);
                Symbols.draw(getContext(), c, "check", right - cw / 2, rowRect.centerY(), 26 * k, ink);
            } else if (!value.isEmpty()) {
                float vr = working ? right - 38 * k : right;
                String shown = TextUtils.ellipsize(value, new TextPaint(rowValue), 520 * k, TextUtils.TruncateAt.END).toString();
                c.drawText(shown, vr, base, rowValue);
                if (working) {
                    spinner.setColor(blend(0xFFFFFFFF, INK, f));
                    Ui.spinner(c, right - 16 * k, rowRect.centerY(), 11 * k, SystemClock.uptimeMillis() / 800f, spinner);
                }
            }
        }

        private void drawChoice(Canvas c) {
            c.drawText(Ui.tr("Início", "Home screen"), LIST_X * k, (LIST_TOP - 22) * k, header);
            String[][] options = {{"Free TV", Ui.tr("Sem anúncios, com a faixa e a tela de descanso novas", "No ads, with the new rail and screensaver")},
                    {Ui.tr("Original", "Original"), Ui.tr("O Início de fábrica da TV", "The TV's factory home screen")}};
            int current = freeActive() ? 0 : 1;
            for (int i = 0; i < 2; i++) {
                float y = (LIST_TOP + 34 + i * (CHOICE_H + CHOICE_GAP)) * k;
                rowY[i] = y;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + CHOICE_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowSub.setColor(blend(0x8CFFFFFF, 0xFF6E6E73, f));
                c.drawText(options[i][0], rowRect.left + 28 * k, rowRect.top + 40 * k, rowLabel);
                c.drawText(options[i][1], rowRect.left + 28 * k, rowRect.top + 72 * k, rowSub);
                if (i == current) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 28 * k - cw / 2, rowRect.centerY(), 26 * k,
                            blend(0xFFFFFFFF, INK, f));
                }
            }
            c.drawText(Ui.tr("A troca leva uns segundos; a tela pisca nas medições.",
                    "Switching takes a few seconds; the screen blinks while it measures."),
                    LIST_X * k + 28 * k, (LIST_TOP + 34 + 2 * (CHOICE_H + CHOICE_GAP) + 36) * k, notice);
        }

        private void drawSoundMenu(Canvas c) {
            c.drawText(Ui.tr("Som da interface", "Interface sound"), LIST_X * k, (LIST_TOP - 22) * k, header);
            String[] labels = {Ui.tr("Som de foco", "Focus sound"), Ui.tr("Som de abrir", "Open sound"), Ui.tr("Silenciar tudo", "Mute all")};
            String[] values = {UiSound.name(Prefs.focusSound(getContext())), UiSound.name(Prefs.openSound(getContext())), ""};
            for (int i = 0; i < 3; i++) {
                rowY[i] = (LIST_TOP + 34 + i * (ROW_H + ROW_GAP)) * k;
                drawRow(c, i, labels[i], values[i], false, false, i == 2 && Prefs.focusSound(getContext()) < 0 && Prefs.openSound(getContext()) < 0);
            }
            c.drawText(Ui.tr("OK abre os dez sons. Foco e abrir podem ser diferentes.",
                    "OK opens the ten sounds. Focus and open can differ."),
                    LIST_X * k + 28 * k, (LIST_TOP + 34 + 3 * (ROW_H + ROW_GAP) + 36) * k, notice);
        }

        private void drawSoundList(Canvas c) {
            boolean focusRole = soundRole == 0;
            c.drawText(focusRole ? Ui.tr("Som de foco", "Focus sound") : Ui.tr("Som de abrir", "Open sound"),
                    LIST_X * k, (LIST_TOP - 22) * k, header);
            int selected = (focusRole ? Prefs.focusSound(getContext()) : Prefs.openSound(getContext())) + 1;
            for (int i = 0; i < 11; i++) {
                float y = (LIST_TOP + 30 + i * 58) * k;
                rowY[i] = y;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + 52 * k);
                float f = drawRowBackground(c, i);
                int ink = blend(0xF2FFFFFF, INK, f);
                int muted = blend(0x8CFFFFFF, 0xFF6E6E73, f);
                rowLabel.setColor(ink);
                rowSub.setColor(muted);
                String name = i == 0 ? Ui.tr("Desligado", "Off") : UiSound.name(i - 1);
                String desc = i == 0 ? Ui.tr("sem som", "muted") : UiSound.description(i - 1);
                c.drawText(name, rowRect.left + 28 * k, rowRect.top + 24 * k, rowLabel);
                c.drawText(desc, rowRect.left + 28 * k, rowRect.top + 44 * k, rowSub);
                if (i > 0) {
                    for (int b = 0; b < 14; b++) {
                        float bh = 4 + ((b * 7 + i * 3) % 16) * k;
                        barPaint.setColor(i == selected ? INK : blend(0x33FFFFFF, 0x221C1C1E, f));
                        c.drawRect(rowRect.right - 180 * k + b * 9 * k, rowRect.centerY() + 8 * k - bh,
                                rowRect.right - 176 * k + b * 9 * k, rowRect.centerY() + 8 * k, barPaint);
                    }
                }
                if (i == selected) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", rowRect.right - 28 * k - cw / 2, rowRect.centerY(), 26 * k, ink);
                }
            }
            c.drawText(Ui.tr("Cima/baixo move e ouve · OK escolhe · VOLTAR volta",
                    "UP/DOWN moves and previews · OK selects · BACK returns"),
                    LIST_X * k + 28 * k, 1010 * k, notice);
        }

        private void drawConfirm(Canvas c) {
            c.drawText(Ui.tr("Restaurar o Início original?", "Restore the original home screen?"),
                    LIST_X * k, (LIST_TOP - 22) * k, header);
            c.drawText(Ui.tr("O Free TV sai de cena. O Início, a tela de descanso e os apps de fábrica voltam.",
                    "Free TV steps aside. The factory home, screensaver and apps come back."),
                    LIST_X * k + 28 * k, (LIST_TOP + 34) * k, notice);
            String[] labels = {Ui.tr("Cancelar", "Cancel"), Ui.tr("Restaurar", "Restore")};
            for (int i = 0; i < 2; i++) {
                rowY[i] = (LIST_TOP + 100 + i * (CHOICE_H + CHOICE_GAP)) * k;
                rowRect.set(LIST_X * k, rowY[i], LIST_R * k, rowY[i] + CHOICE_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                c.drawText(labels[i], rowRect.left + 28 * k, rowRect.centerY() + Ui.halfCap(rowLabel), rowLabel);
                if (i == 1) {
                    c.drawText(Ui.tr("Volta ao Início de fábrica", "Returns to the factory home"), rowRect.right - 28 * k,
                            rowRect.centerY() + Ui.halfCap(rowSub), rowSub);
                }
            }
            c.drawText(Ui.tr("Cancelar fica selecionado por padrão.", "Cancel is focused by default."),
                    LIST_X * k + 28 * k, (LIST_TOP + 100 + 2 * (CHOICE_H + CHOICE_GAP) + 36) * k, notice);
        }

        private void drawFooter(Canvas c) {
            if (screen == SOUND_LIST) return;   // that screen has its own footer
            String error = state.optString("error");
            if (!error.isEmpty()) {
                c.drawText(TextUtils.ellipsize(error, notice, 780 * k, TextUtils.TruncateAt.END).toString(),
                        LIST_X * k + 28 * k, 1030 * k, warn);
            } else {
                c.drawText(Ui.tr("Cima/baixo move · OK abre · VOLTAR sai",
                        "UP/DOWN moves · OK opens · BACK leaves"), LIST_X * k + 28 * k, 1030 * k, notice);
            }
        }

        private float drawRowBackground(Canvas c, int i) {
            float f = Ui.clamp(pop[i].value);
            if (f > 0.01f) {
                float s = 1 + 0.02f * f;
                float cx = rowRect.centerX(), cy = rowRect.centerY();
                tmp.set(cx - rowRect.width() * s / 2, cy - rowRect.height() * s / 2, cx + rowRect.width() * s / 2, cy + rowRect.height() * s / 2);
                float r = 18 * k * s;
                tmp.offset(0, 10 * k * f);
                shadow.setColor(Ui.alpha(0xFF000000, 0.4f * f));
                c.drawRoundRect(tmp, r, r, shadow);
                tmp.offset(0, -10 * k * f);
                white.setAlpha(Math.round(255 * f));
                c.drawRoundRect(tmp, r, r, white);
                rowRect.set(tmp);
            }
            return f;
        }

        private void activate() {
            sound.open();
            Context c = getContext();
            if (screen == MAIN) {
                switch (focus) {
                    case 0:
                        screen = HOME_CHOICE;
                        focus = freeActive() ? 0 : 1;
                        break;
                    case 1:
                        pause();
                        activity.showPreview();
                        break;
                    case 2:
                        activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        break;
                    case 3:
                        screen = SOUND_MENU;
                        focus = 0;
                        break;
                    case 4:
                        Prefs.setReducedMotion(c, !Prefs.reducedMotion(c));
                        scene.setMotion(Prefs.reducedMotion(c) ? 0f : 0.5f, 0.2f);
                        break;
                    case 5:
                        Prefs.setClock24(c, !Prefs.clock24(c));
                        Ui.clock24 = Prefs.clock24(c);
                        break;
                    case 6:
                    case 7:
                    case 8:
                        break;
                    case 9:
                        screen = CONFIRM;
                        focus = 0;
                        break;
                    default:
                        break;
                }
            } else if (screen == HOME_CHOICE) {
                if (focus == 0) {
                    if (!freeActive() && !busy()) {
                        HomeMode.request(c, HomeMode.FREE);
                        pendingAt = SystemClock.uptimeMillis();
                    }
                    screen = MAIN;
                    focus = 0;
                } else {
                    screen = CONFIRM;
                    focus = 0;
                }
            } else if (screen == SOUND_MENU) {
                if (focus < 2) {
                    soundRole = focus;
                    screen = SOUND_LIST;
                    int current = (focus == 0 ? Prefs.focusSound(c) : Prefs.openSound(c)) + 1;
                    focus = Math.max(0, Math.min(10, current));
                    sound.preview(focus == 0 ? -1 : focus - 1);
                } else {
                    Prefs.setFocusSound(c, Prefs.OFF);
                    Prefs.setOpenSound(c, Prefs.OFF);
                }
            } else if (screen == SOUND_LIST) {
                int value = focus - 1;
                if (soundRole == 0) Prefs.setFocusSound(c, value); else Prefs.setOpenSound(c, value);
                screen = SOUND_MENU;
                focus = soundRole;
            } else if (screen == CONFIRM) {
                if (focus == 1 && !busy()) {
                    HomeMode.request(c, HomeMode.ORIGINAL);
                    pendingAt = SystemClock.uptimeMillis();
                    screen = MAIN;
                    focus = 0;
                } else {
                    screen = MAIN;
                    focus = 9;
                }
            }
            for (Spring s : pop) s.snap(0);
            invalidate();
        }

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down && focus > 0) {
                        focus--;
                        if (screen == SOUND_LIST) sound.preview(focus == 0 ? -1 : focus - 1); else sound.move();
                    }
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && focus < rowCount() - 1) {
                        focus++;
                        if (screen == SOUND_LIST) sound.preview(focus == 0 ? -1 : focus - 1); else sound.move();
                    }
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled()) activate();
                        okDown = false;
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (screen == MAIN) {
                        if (!down) {
                            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(home);
                            release();
                            activity.finish();
                            android.os.Process.killProcess(android.os.Process.myPid());
                        }
                        return true;
                    }
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                    } else {
                        if (backDown && !e.isCanceled()) {
                            if (screen == SOUND_LIST) {
                                screen = SOUND_MENU;
                                focus = soundRole;
                            } else if (screen == HOME_CHOICE) {
                                screen = MAIN;
                                focus = 0;
                            } else {
                                int backFocus = screen == CONFIRM ? 9 : 3;
                                screen = MAIN;
                                focus = backFocus;
                            }
                        }
                        backDown = false;
                    }
                    invalidate();
                    return true;
                default:
                    return false;
            }
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        private static String versionName(Context c) {
            try {
                return "Free TV " + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return "Free TV";
            }
        }
    }
}
