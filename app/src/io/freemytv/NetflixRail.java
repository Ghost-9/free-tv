package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Direction A: "Popular on Netflix", using JustWatch's public popularity data (no account, no API key).
 * The row is a popularity list for Netflix India, not a personal recommendation; every card opens the
 * title in Netflix through the provider's own netflix.com/title/&lt;id&gt; link.
 *
 * The rail is self-contained: it fetches, caches the JSON and six posters, draws itself, and hands the
 * home screen one focus index. HomeView only has to route the D-pad and call open().
 */
final class NetflixRail {
    private static final String API = "https://apis.justwatch.com/graphql";
    private static final String PREFS = "netflix-rail";
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    private static final int COUNT = 6;                 // six posters, plus the "See all" card
    private static final float LEFT = 80, TOP = 340, CARD_W = 210, CARD_H = 294, GAP = 20;

    private static final String QUERY =
            "query { popularTitles(country: IN, first: " + COUNT + ", filter: {packages: [\"nfx\"]}) { " +
            "edges { node { objectType content(country: IN, language: en) { title originalReleaseYear runtime posterUrl } " +
            "offers(country: IN, platform: WEB) { standardWebURL package { clearName } } } } } }";

    static final class Item {
        String title = "", type = "", url = "", poster = "";
        int year, runtime, rank;
        Bitmap bitmap;
    }

    private final Context context;
    private final Runnable onChanged;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Item> items = new ArrayList<>();
    private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint kicker = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint cardTitle = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint cardMeta = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint seeArrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF(), cell = new RectF(), tmp = new RectF();
    private volatile boolean fetching;
    private String state = "loading";

    NetflixRail(Context c, Runnable onChanged) {
        this.context = c.getApplicationContext();
        this.onChanged = onChanged;
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        clock.setTypeface(Ui.displaySemibold);
        date.setTypeface(Ui.regular);
        kicker.setTypeface(Ui.semibold);
        kicker.setLetterSpacing(0.18f);
        title.setTypeface(Ui.displaySemibold);
        sub.setTypeface(Ui.regular);
        cardTitle.setTypeface(Ui.semibold);
        cardMeta.setTypeface(Ui.regular);
        badgeText.setTypeface(Ui.semibold);
        badge.setColor(0xFFE50914);
        seeArrow.setTypeface(Ui.medium);
        seeArrow.setTextAlign(Paint.Align.CENTER);
        shadow.setColor(0x66000000);
        shadow.setMaskFilter(new BlurMaskFilter(18, BlurMaskFilter.Blur.NORMAL));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1.5f);
    }

    void start() {
        handler.postDelayed(clockTick, 30_000);
        io.execute(this::load);
    }

    void stop() {
        io.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        for (Item it : items) if (it.bitmap != null) it.bitmap.recycle();
        items.clear();
    }

    boolean hasItems() { return !items.isEmpty(); }

    int count() { return items.size() + (items.isEmpty() ? 0 : 1); }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (onChanged != null) onChanged.run();
            handler.postDelayed(this, 30_000);
        }
    };

    /** The item's card centre in screen coordinates; the home screen uses it to keep the shelf aligned. */
    float centerX(int index, float k) {
        return (LEFT + Math.min(index, count() - 1) * (CARD_W + GAP) + CARD_W / 2) * k;
    }

    int nearest(float x, float k) {
        int best = 0;
        float d = Float.MAX_VALUE;
        for (int i = 0; i < count(); i++) {
            float c = (LEFT + i * (CARD_W + GAP) + CARD_W / 2) * k;
            if (Math.abs(c - x) < d) { d = Math.abs(c - x); best = i; }
        }
        return best;
    }

    void open(android.app.Activity a, int index) {
        if (items.isEmpty()) return;
        Intent intent;
        if (index < items.size()) {
            Item it = items.get(index);
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(it.url.isEmpty() ? "https://www.netflix.com/" : it.url));
        } else {
            intent = a.getPackageManager().getLeanbackLaunchIntentForPackage("com.netflix.ninja");
            if (intent == null) intent = a.getPackageManager().getLaunchIntentForPackage("com.netflix.ninja");
        }
        if (intent == null) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(intent);
        } catch (RuntimeException e) {
            AppLog.w("could not open Netflix: " + e.getMessage());
        }
    }

    // MARK: - Drawing (1920×790 coordinates, scaled by k)

    private float scale = 1;

    void draw(Canvas c, float k, int focused, Spring[] focus, float night) {
        scale = k;
        int ink = blend(0xFF1D1D1F, 0xFFF5F5F7, night);
        int muted = blend(0x996E6E73, 0x99FFFFFF, night);
        int faint = blend(0x666E6E73, 0x66FFFFFF, night);

        clock.setTextSize(92 * k);
        clock.setColor(ink);
        date.setTextSize(20 * k);
        date.setColor(muted);
        kicker.setTextSize(11 * k);
        kicker.setColor(0xFFE50914);
        title.setTextSize(34 * k);
        title.setColor(ink);
        sub.setTextSize(14 * k);
        sub.setColor(muted);
        cardTitle.setTextSize(13 * k);
        cardTitle.setColor(ink);
        cardMeta.setTextSize(11.5f * k);
        cardMeta.setColor(muted);

        long now = System.currentTimeMillis();
        c.drawText(Ui.clock(now), 80 * k, 150 * k, clock);
        c.drawText(Ui.date(now), 84 * k, 184 * k, date);
        c.drawText(Ui.tr("NETFLIX · ÍNDIA", "NETFLIX · INDIA"), 80 * k, 250 * k, kicker);
        c.drawText(Ui.tr("Popular na Netflix", "Popular on Netflix"), 80 * k, 292 * k, title);
        c.drawText(Ui.tr("Atualiza toda semana · abre o título na Netflix",
                "Refreshed weekly · opens the title in Netflix"), 80 * k, 318 * k, sub);

        if (items.isEmpty()) {
            Paint p = sub;
            p.setTextSize(16 * k);
            p.setColor(muted);
            String msg = "error".equals(state)
                    ? Ui.tr("Netflix indisponível agora. Tente de novo mais tarde.",
                            "Netflix is unreachable right now. Try again later.")
                    : Ui.tr("Buscando os títulos…", "Fetching titles…");
            c.drawText(msg, 80 * k, 400 * k, p);
            return;
        }

        int top = -1;
        for (int i = 0; i < items.size(); i++) {
            float f = focus != null && i < focus.length ? Ui.clamp(focus[i].value) : 0;
            if (i == focused) top = i;
            drawCard(c, i, k, f, ink, muted);
        }
        if (top >= 0) drawCard(c, top, k, Ui.clamp(focus[top].value), ink, muted);

        // "See all" tile: opens the Netflix app.
        int si = items.size();
        float f = focus != null && si < focus.length ? Ui.clamp(focus[si].value) : 0;
        rect(si, k, cell);
        float s = 1 + 0.07f * f;
        float cx = cell.centerX(), cy = cell.centerY() - 3 * k * f;
        cell.set(cx - CARD_W * k * s / 2, cy - CARD_H * k * s / 2, cx + CARD_W * k * s / 2, cy + CARD_H * k * s / 2);
        if (f > 0.01f) {
            tmp.set(cell); tmp.offset(0, 10 * k * f);
            c.drawRoundRect(tmp, 14 * k, 14 * k, shadow);
        }
        fill.setColor(blend(0x0F000000, 0x14FFFFFF, night));
        c.drawRoundRect(cell, 14 * k, 14 * k, fill);
        border.setColor(f > 0.01f ? blend(0x331D1D1F, 0x99FFFFFF, Math.max(f, night * 0.35f)) : blend(0x221D1D1F, 0x33FFFFFF, 0.5f));
        c.drawRoundRect(cell, 14 * k, 14 * k, border);
        float r = 22 * k;
        float ax = cell.centerX(), ay = cell.centerY() - 34 * k;
        fill.setColor(0xFFE50914);
        c.drawCircle(ax, ay, r, fill);
        seeArrow.setTextSize(22 * k);
        seeArrow.setColor(0xFFFFFFFF);
        c.drawText("→", ax, ay + Ui.halfCap(seeArrow), seeArrow);
        Paint t = cardTitle;
        t.setTextAlign(Paint.Align.CENTER);
        c.drawText(Ui.tr("Ver tudo na Netflix", "See all on Netflix"), cell.centerX(), cell.centerY() + 24 * k, t);
        t.setTextAlign(Paint.Align.LEFT);
        Paint m = cardMeta;
        m.setTextAlign(Paint.Align.CENTER);
        c.drawText(Ui.tr("Abre o app", "Opens the app"), cell.centerX(), cell.centerY() + 46 * k, m);
        m.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCard(Canvas c, int i, float k, float f, int ink, int muted) {
        Item it = items.get(i);
        rect(i, k, cell);
        float s = 1 + 0.07f * f;
        float cx = cell.centerX(), cy = cell.centerY() - 3 * k * f;
        cell.set(cx - CARD_W * k * s / 2, cy - CARD_H * k * s / 2, cx + CARD_W * k * s / 2, cy + CARD_H * k * s / 2);
        if (f > 0.01f) {
            tmp.set(cell); tmp.offset(0, 10 * k * f);
            c.drawRoundRect(tmp, 14 * k, 14 * k, shadow);
        }
        clip.reset();
        clip.addRoundRect(cell, 14 * k, 14 * k, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (it.bitmap != null) {
            c.drawBitmap(it.bitmap, null, cell, image);
        } else {
            fill.setColor(blend(0x1A1D1D1F, 0xFF1A1C24, 0.5f));
            c.drawRect(cell, fill);
        }
        c.restore();
        border.setStrokeWidth((1.5f + 1.5f * f) * k);
        border.setColor(f > 0.01f ? blend(0x66FFFFFF, 0xFFFFFFFF, f) : blend(0x221D1D1F, 0x33FFFFFF, 0.5f));
        c.drawRoundRect(cell, 14 * k, 14 * k, border);

        badgeText.setTextSize(17 * k);
        badgeText.setColor(0xFFFFFFFF);
        float bs = 32 * k;
        tmp.set(cell.left + 10 * k, cell.bottom - bs - 10 * k, cell.left + 10 * k + bs, cell.bottom - 10 * k);
        c.drawRoundRect(tmp, 8 * k, 8 * k, badge);
        c.drawText(String.valueOf(it.rank), tmp.centerX() - badgeText.measureText(String.valueOf(it.rank)) / 2,
                tmp.centerY() + Ui.halfCap(badgeText), badgeText);

        float base = cell.bottom + 22 * k;
        c.save();
        c.clipRect(cell.left, cell.bottom, cell.right, cell.bottom + 60 * k);
        c.drawText(it.title, cell.left, base, cardTitle);
        c.restore();
        String kind = "SHOW".equals(it.type) ? Ui.tr("Série", "Series") : Ui.tr("Filme", "Film");
        String meta = kind + (it.runtime > 0 ? " · " + it.runtime + "m" : "");
        c.drawText(meta, cell.left, base + 20 * k, cardMeta);
    }

    private void rect(int i, float k, RectF out) {
        float x = LEFT + i * (CARD_W + GAP);
        out.set(x * k, TOP * k, (x + CARD_W) * k, (TOP + CARD_H) * k);
    }

    // MARK: - Data

    private void load() {
        String body = prefs.getString("body", null);
        long at = prefs.getLong("at", 0);
        boolean haveCache = false;
        if (body != null) {
            List<Item> cached = parse(body, false);
            if (!cached.isEmpty()) {
                haveCache = true;
                loadPosters(cached);
                post(cached, "ready");
            }
        }
        if (!haveCache || System.currentTimeMillis() - at > WEEK) fetch();
    }

    private void fetch() {
        if (fetching) return;
        fetching = true;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "ArtCraftLauncher/1.0");
            byte[] body = ("{\"query\":\"" + QUERY.replace("\"", "\\\"") + "\"}").getBytes(StandardCharsets.UTF_8);
            try (java.io.OutputStream out = c.getOutputStream()) { out.write(body); }
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            String text;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                text = out.toString(StandardCharsets.UTF_8.name());
            } finally {
                c.disconnect();
            }
            List<Item> fresh = parse(text, true);
            if (fresh.isEmpty()) throw new IOException("empty list");
            prefs.edit().putString("body", text).putLong("at", System.currentTimeMillis()).apply();
            post(fresh, "ready");
        } catch (Exception e) {
            AppLog.w("netflix rail: " + e.getMessage());
            if (items.isEmpty()) { state = "error"; handler.post(() -> { if (onChanged != null) onChanged.run(); }); }
        } finally {
            fetching = false;
        }
    }

    private List<Item> parse(String body, boolean download) {
        List<Item> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            JSONArray edges = root.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges");
            for (int i = 0; i < edges.length() && i < COUNT; i++) {
                JSONObject node = edges.getJSONObject(i).getJSONObject("node");
                JSONObject content = node.getJSONObject("content");
                Item it = new Item();
                it.rank = i + 1;
                it.title = content.optString("title");
                it.year = content.optInt("originalReleaseYear");
                it.runtime = content.optInt("runtime");
                it.type = node.optString("objectType");
                String p = content.optString("posterUrl");
                if (p.startsWith("/")) p = "https://images.justwatch.com" + p;
                it.poster = p.replace("{profile}", "s332").replace("{format}", "webp");
                JSONArray offers = node.optJSONArray("offers");
                if (offers != null) {
                    for (int o = 0; o < offers.length(); o++) {
                        JSONObject offer = offers.getJSONObject(o);
                        String name = offer.optJSONObject("package") != null
                                ? offer.getJSONObject("package").optString("clearName") : "";
                        if ("Netflix".equalsIgnoreCase(name)) {
                            it.url = offer.optString("standardWebURL");
                            break;
                        }
                    }
                }
                if (it.url.isEmpty()) it.url = "https://www.netflix.com/";
                if (download) downloadPoster(it);
                out.add(it);
            }
        } catch (Exception e) {
            AppLog.w("netflix parse: " + e.getMessage());
        }
        return out;
    }

    private void loadPosters(List<Item> list) {
        for (Item it : list) {
            File f = posterFile(it.rank);
            if (f.exists()) it.bitmap = BitmapFactory.decodeFile(f.getPath());
            if (it.bitmap == null) downloadPoster(it);
        }
    }

    private void downloadPoster(Item it) {
        if (it.poster.isEmpty()) return;
        File dir = new File(context.getCacheDir(), "netflix");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = posterFile(it.rank), part = new File(f.getPath() + ".part");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(it.poster).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "ArtCraftLauncher/1.0");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } finally {
                c.disconnect();
            }
            if (!part.renameTo(f)) throw new IOException("rename");
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) {
                int max = 420;
                if (b.getWidth() > max) {
                    Bitmap small = Bitmap.createScaledBitmap(b, max, Math.round(b.getHeight() * max / (float) b.getWidth()), true);
                    if (small != b) b.recycle();
                    b = small;
                }
                it.bitmap = b;
            }
        } catch (Exception e) {
            AppLog.w("netflix poster: " + e.getMessage());
        }
    }

    private File posterFile(int rank) {
        return new File(new File(context.getCacheDir(), "netflix"), rank + ".webp");
    }

    private void post(List<Item> fresh, String s) {
        handler.post(() -> {
            for (Item old : items) if (old.bitmap != null) old.bitmap.recycle();
            items.clear();
            items.addAll(fresh);
            state = s;
            if (onChanged != null) onChanged.run();
        });
    }

    private static int blend(int a, int b, float f) {
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }
}
