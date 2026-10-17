package id.wandalo.gopaynotif;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Neo-Brutalism UI: border hitam tebal, hard shadow, warna flat bold.
 * Tanpa emoji. Navigasi pakai tab: STATUS / APLIKASI / RIWAYAT.
 */
public class MainActivity extends Activity {

    // Palet Neo-Brutalism
    private static final int BLACK = 0xFF111111;
    private static final int PAPER = 0xFFFFF6E5;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLUE = 0xFF3D7BFF;
    private static final int GREEN = 0xFF2ECC71;
    private static final int YELLOW = 0xFFFFC93C;
    private static final int PINK = 0xFFFF6B81;
    private static final int GRAY = 0xFF9AA0A6;
    private static final int LIGHT_GRAY = 0xFFF1F1F1;

    private static final int BORDER = 4;      // px border hitam
    private static final int SHADOW = 10;     // px offset hard shadow
    private static final int CORNER = 6;      // px radius kecil

    private TextView tvStatus, tvCount, tvHistoryEmpty;
    private TextView tvLastSent, tvServerStatus, tvStats, tvPending;
    private EditText etWebhook, etSearch, etToken;
    private EditText etDeviceName, etMinAmount, etKeywords;
    private View serverDot;
    private LinearLayout tutorialBox;
    private LinearLayout appList, historyList, selectedChips;
    private LinearLayout tabStatus, tabApps, tabHistory;
    private Button tabBtnStatus, tabBtnApps, tabBtnHistory;
    private final List<AppRow> rows = new ArrayList<>();

    private static class AppRow {
        String pkg, name;
        Drawable icon;
        CheckBox box;
        LinearLayout layout;
    }

    // ---------- Drawable helpers ----------

    private GradientDrawable brutalBox(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(BORDER, BLACK);
        d.setCornerRadius(CORNER);
        return d;
    }

    private GradientDrawable brutalShadow() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(BLACK);
        d.setCornerRadius(CORNER);
        return d;
    }

    /** Bungkus view dengan hard shadow hitam offset (ciri khas neo-brutalism). */
    private View brutalWrap(View content) {
        FrameLayout wrap = new FrameLayout(this);

        View shadow = new View(this);
        shadow.setBackground(brutalShadow());
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(SHADOW, SHADOW, 0, 0);
        shadow.setLayoutParams(slp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, SHADOW, SHADOW);
        content.setLayoutParams(clp);

        wrap.addView(shadow);
        wrap.addView(content);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.setMargins(0, 0, 0, 28);
        wrap.setLayoutParams(wlp);
        return wrap;
    }

    // ---------- onCreate ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(PAPER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 48);
        sv.addView(root);
        setContentView(sv);

        // Header
        TextView header = new TextView(this);
        header.setText("NOTIF FORWARDER");
        header.setTextSize(28);
        header.setTextColor(BLACK);
        header.setTypeface(header.getTypeface(), Typeface.BOLD);
        header.setPadding(4, 0, 0, 2);
        root.addView(header);

        String verName = "13";
        try {
            verName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}

        TextView subtitle = new TextView(this);
        subtitle.setText("Teruskan notifikasi ke server. Realtime. v" + verName);
        subtitle.setTextSize(14);
        subtitle.setTextColor(GRAY);
        subtitle.setPadding(4, 0, 0, 20);
        root.addView(subtitle);

        // Jadwalkan heartbeat + minta izin notifikasi (Android 13+)
        HeartbeatReceiver.schedule(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // Tab bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(0, 0, 0, 8);

        tabBtnStatus = makeTabButton("STATUS");
        tabBtnApps = makeTabButton("APLIKASI");
        tabBtnHistory = makeTabButton("RIWAYAT");

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(0, 0, 10, 0);
        tabBtnStatus.setLayoutParams(tlp);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp2.setMargins(0, 0, 10, 0);
        tabBtnApps.setLayoutParams(tlp2);
        LinearLayout.LayoutParams tlp3 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tabBtnHistory.setLayoutParams(tlp3);

        tabBar.addView(tabBtnStatus);
        tabBar.addView(tabBtnApps);
        tabBar.addView(tabBtnHistory);
        root.addView(brutalWrap(tabBar));

        // Kontainer tab
        tabStatus = new LinearLayout(this);
        tabStatus.setOrientation(LinearLayout.VERTICAL);
        tabApps = new LinearLayout(this);
        tabApps.setOrientation(LinearLayout.VERTICAL);
        tabHistory = new LinearLayout(this);
        tabHistory.setOrientation(LinearLayout.VERTICAL);

        root.addView(tabStatus);
        root.addView(tabApps);
        root.addView(tabHistory);

        buildTabStatus();
        buildTabApps();
        buildTabHistory();

        tabBtnStatus.setOnClickListener(v -> showTab(0));
        tabBtnApps.setOnClickListener(v -> showTab(1));
        tabBtnHistory.setOnClickListener(v -> showTab(2));

        showTab(0);
        loadApps();
    }

    private Button makeTabButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTypeface(btn.getTypeface(), Typeface.BOLD);
        btn.setAllCaps(true);
        btn.setPadding(0, 22, 0, 22);
        return btn;
    }

    private void showTab(int idx) {
        tabStatus.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        tabApps.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        tabHistory.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);

        styleTab(tabBtnStatus, idx == 0);
        styleTab(tabBtnApps, idx == 1);
        styleTab(tabBtnHistory, idx == 2);

        if (idx == 2) loadHistory();
        if (idx == 1) updateSelected();
    }

    private void styleTab(Button btn, boolean active) {
        btn.setBackground(brutalBox(active ? YELLOW : WHITE));
        btn.setTextColor(BLACK);
    }

    /** Tombol aksi brutal (dengan shadow), return wrapper; tombol asli di getTag(). */
    private View actionButton(String text, int fill, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fill == YELLOW ? BLACK : WHITE);
        b.setTextSize(15);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setBackground(brutalBox(fill));
        b.setPadding(0, 26, 0, 26);
        b.setAllCaps(true);
        b.setOnClickListener(l);
        return brutalWrap(b);
    }

    private TextView sectionLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(16);
        tv.setTextColor(BLACK);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setPadding(4, 16, 0, 8);
        return tv;
    }

    // ---------- TAB: STATUS ----------

    private void buildTabStatus() {
        SharedPreferences prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        // --- Tutorial (hanya sampai ditekan MENGERTI) ---
        if (!prefs.getBoolean("tutorial_done", false)) {
            LinearLayout tutInner = new LinearLayout(this);
            tutInner.setOrientation(LinearLayout.VERTICAL);
            tutInner.setBackground(brutalBox(YELLOW));
            tutInner.setPadding(28, 28, 28, 28);
            TextView tutTitle = new TextView(this);
            tutTitle.setText("PANDUAN SINGKAT");
            tutTitle.setTextSize(16);
            tutTitle.setTextColor(BLACK);
            tutTitle.setTypeface(tutTitle.getTypeface(), Typeface.BOLD);
            tutInner.addView(tutTitle);
            TextView tutBody = new TextView(this);
            tutBody.setText("1. Tekan BERI IZIN lalu aktifkan Notif Forwarder\n" +
                    "2. Isi webhook, token, dan nama perangkat\n" +
                    "3. Buka tab APLIKASI, pilih aplikasi, tekan SIMPAN\n" +
                    "4. Tekan TEST, cek dashboard dalam 2 detik");
            tutBody.setTextSize(14);
            tutBody.setTextColor(BLACK);
            tutBody.setPadding(0, 12, 0, 12);
            tutInner.addView(tutBody);
            Button tutOk = miniButton("MENGERTI", BLACK);
            tutOk.setOnClickListener(v -> {
                prefs.edit().putBoolean("tutorial_done", true).apply();
                tutorialBox.setVisibility(View.GONE);
            });
            tutInner.addView(tutOk);
            tutorialBox = new LinearLayout(this);
            tutorialBox.setOrientation(LinearLayout.VERTICAL);
            tutorialBox.addView(brutalWrap(tutInner));
            tabStatus.addView(tutorialBox);
        }

        // Kartu status izin
        LinearLayout cardInner = new LinearLayout(this);
        cardInner.setOrientation(LinearLayout.VERTICAL);
        cardInner.setBackground(brutalBox(WHITE));
        cardInner.setPadding(28, 28, 28, 28);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setId(View.generateViewId());
        LinearLayout.LayoutParams dotLp =
                new LinearLayout.LayoutParams(36, 36);
        dotLp.setMargins(0, 0, 20, 0);
        dot.setLayoutParams(dotLp);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(17);
        tvStatus.setTextColor(BLACK);
        tvStatus.setTypeface(tvStatus.getTypeface(), Typeface.BOLD);

        statusRow.addView(dot);
        statusRow.addView(tvStatus);
        cardInner.addView(statusRow);

        // Baris status server
        LinearLayout srvRow = new LinearLayout(this);
        srvRow.setOrientation(LinearLayout.HORIZONTAL);
        srvRow.setGravity(Gravity.CENTER_VERTICAL);
        srvRow.setPadding(0, 14, 0, 0);
        serverDot = new View(this);
        LinearLayout.LayoutParams sdotLp =
                new LinearLayout.LayoutParams(36, 36);
        sdotLp.setMargins(0, 0, 20, 0);
        serverDot.setLayoutParams(sdotLp);
        paintDot(serverDot, GRAY);
        tvServerStatus = new TextView(this);
        tvServerStatus.setText("SERVER: MENGECEK...");
        tvServerStatus.setTextSize(15);
        tvServerStatus.setTextColor(BLACK);
        tvServerStatus.setTypeface(tvServerStatus.getTypeface(), Typeface.BOLD);
        srvRow.addView(serverDot);
        srvRow.addView(tvServerStatus);
        cardInner.addView(srvRow);

        TextView hint = new TextView(this);
        hint.setText("Aplikasi membaca notifikasi dari aplikasi yang dipilih, " +
                "lalu mengirimkannya ke webhook secara langsung.");
        hint.setTextSize(13);
        hint.setTextColor(GRAY);
        hint.setPadding(0, 16, 0, 0);
        cardInner.addView(hint);

        tabStatus.addView(brutalWrap(cardInner));
        cardInner.setTag(dot);

        // Kartu terakhir terkirim
        LinearLayout lastInner = new LinearLayout(this);
        lastInner.setOrientation(LinearLayout.VERTICAL);
        lastInner.setBackground(brutalBox(WHITE));
        lastInner.setPadding(28, 24, 28, 24);
        TextView lastLbl = new TextView(this);
        lastLbl.setText("TERAKHIR TERKIRIM");
        lastLbl.setTextSize(12);
        lastLbl.setTextColor(BLACK);
        lastLbl.setTypeface(lastLbl.getTypeface(), Typeface.BOLD);
        lastInner.addView(lastLbl);
        tvLastSent = new TextView(this);
        tvLastSent.setTextSize(14);
        tvLastSent.setTextColor(BLACK);
        tvLastSent.setPadding(0, 8, 0, 0);
        lastInner.addView(tvLastSent);
        tvPending = new TextView(this);
        tvPending.setTextSize(13);
        tvPending.setTypeface(tvPending.getTypeface(), Typeface.BOLD);
        tvPending.setPadding(0, 8, 0, 0);
        lastInner.addView(tvPending);
        tabStatus.addView(brutalWrap(lastInner));

        tabStatus.addView(actionButton("Beri Izin Baca Notifikasi", BLUE, v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "Aktifkan Notif Forwarder di daftar",
                    Toast.LENGTH_LONG).show();
        }));

        tabStatus.addView(actionButton("Matikan Optimasi Baterai", BLACK, v -> {
            try {
                Intent i = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));

        tabStatus.addView(actionButton("Panduan Autostart Xiaomi", PINK, v ->
                openAutostartSettings()));

        // Webhook
        tabStatus.addView(sectionLabel("WEBHOOK SERVER"));
        LinearLayout wbInner = new LinearLayout(this);
        wbInner.setOrientation(LinearLayout.VERTICAL);
        wbInner.setBackground(brutalBox(WHITE));
        wbInner.setPadding(28, 28, 28, 28);

        wbInner.addView(fieldLabel("URL WEBHOOK"));
        etWebhook = new EditText(this);
        etWebhook.setHint("https://...");
        etWebhook.setTextSize(14);
        etWebhook.setTextColor(BLACK);
        etWebhook.setBackground(brutalBox(LIGHT_GRAY));
        etWebhook.setPadding(20, 18, 20, 18);
        wbInner.addView(etWebhook);

        wbInner.addView(fieldLabel("NAMA PERANGKAT"));
        etDeviceName = new EditText(this);
        etDeviceName.setHint("cth: Kasir 1");
        etDeviceName.setTextSize(14);
        etDeviceName.setTextColor(BLACK);
        etDeviceName.setBackground(brutalBox(LIGHT_GRAY));
        etDeviceName.setPadding(20, 18, 20, 18);
        wbInner.addView(etDeviceName);

        wbInner.addView(fieldLabel("TOKEN (SAMAKAN DENGAN SERVER)"));
        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        etToken = new EditText(this);
        etToken.setHint("Token rahasia...");
        etToken.setTextSize(14);
        etToken.setTextColor(BLACK);
        etToken.setBackground(brutalBox(LIGHT_GRAY));
        etToken.setPadding(20, 18, 20, 18);
        etToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMargins(0, 0, 12, 0);
        etToken.setLayoutParams(tlp);
        Button btnPeek = miniButton("LIHAT", YELLOW);
        btnPeek.setOnClickListener(v -> {
            boolean showing = etToken.getInputType()
                    == android.text.InputType.TYPE_CLASS_TEXT;
            etToken.setInputType(showing
                    ? (android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    : android.text.InputType.TYPE_CLASS_TEXT);
            etToken.setSelection(etToken.getText().length());
            btnPeek.setText(showing ? "LIHAT" : "TUTUP");
        });
        tokenRow.addView(etToken);
        tokenRow.addView(btnPeek);
        wbInner.addView(tokenRow);

        LinearLayout wbBtns = new LinearLayout(this);
        wbBtns.setOrientation(LinearLayout.HORIZONTAL);
        wbBtns.setPadding(0, 16, 0, 0);

        Button btnSave = miniButton("SIMPAN", GREEN);
        btnSave.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button btnTest = miniButton("TEST", BLUE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        blp.setMargins(16, 0, 0, 0);
        btnTest.setLayoutParams(blp);
        wbBtns.addView(btnSave);
        wbBtns.addView(btnTest);
        wbInner.addView(wbBtns);
        tabStatus.addView(brutalWrap(wbInner));

        etWebhook.setText(prefs.getString("webhook",
                "https://gopay.wandalo.dev/api/notif"));
        etToken.setText(prefs.getString("webhook_token", ""));
        etDeviceName.setText(prefs.getString("device_name",
                android.os.Build.MODEL));
        btnSave.setOnClickListener(v -> savePrefs());
        btnTest.setOnClickListener(v -> testWebhook());

        // Filter
        tabStatus.addView(sectionLabel("FILTER NOTIFIKASI"));
        LinearLayout fInner = new LinearLayout(this);
        fInner.setOrientation(LinearLayout.VERTICAL);
        fInner.setBackground(brutalBox(WHITE));
        fInner.setPadding(28, 28, 28, 28);

        fInner.addView(fieldLabel("NOMINAL MINIMAL (RP, 0 = SEMUA)"));
        etMinAmount = new EditText(this);
        etMinAmount.setHint("0");
        etMinAmount.setTextSize(14);
        etMinAmount.setTextColor(BLACK);
        etMinAmount.setBackground(brutalBox(LIGHT_GRAY));
        etMinAmount.setPadding(20, 18, 20, 18);
        etMinAmount.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER);
        fInner.addView(etMinAmount);

        fInner.addView(fieldLabel("KATA KUNCI (PISAH KOMA, KOSONG = SEMUA)"));
        etKeywords = new EditText(this);
        etKeywords.setHint("cth: diterima, berhasil");
        etKeywords.setTextSize(14);
        etKeywords.setTextColor(BLACK);
        etKeywords.setBackground(brutalBox(LIGHT_GRAY));
        etKeywords.setPadding(20, 18, 20, 18);
        fInner.addView(etKeywords);

        Button btnSaveFilter = miniButton("SIMPAN FILTER", GREEN);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.setMargins(0, 16, 0, 0);
        btnSaveFilter.setLayoutParams(flp);
        btnSaveFilter.setOnClickListener(v -> savePrefs());
        fInner.addView(btnSaveFilter);
        tabStatus.addView(brutalWrap(fInner));

        etMinAmount.setText(prefs.getString("min_amount", "0"));
        etKeywords.setText(prefs.getString("keywords", ""));
    }

    private TextView fieldLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(12);
        tv.setTextColor(BLACK);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setPadding(4, 18, 0, 6);
        return tv;
    }

    private void paintDot(View dot, int color) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setStroke(BORDER, BLACK);
        dot.setBackground(d);
    }

    /** Coba buka pengaturan autostart MIUI, fallback ke info aplikasi. */
    private void openAutostartSettings() {
        try {
            Intent i = new Intent();
            i.setComponent(new android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(i);
            Toast.makeText(this, "Aktifkan autostart untuk Notif Forwarder",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e1) {
            try {
                Intent i = new Intent();
                i.setComponent(new android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"));
                startActivity(i);
            } catch (Exception e2) {
                Intent i = new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
                Toast.makeText(this,
                        "Cari menu Autostart/Baterai di info aplikasi",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Cek koneksi ke server (GET halaman utama). */
    private void checkServer() {
        tvServerStatus.setText("SERVER: MENGECEK...");
        paintDot(serverDot, GRAY);
        new Thread(() -> {
            boolean ok = false;
            try {
                String webhook = getSharedPreferences("cfg", MODE_PRIVATE)
                        .getString("webhook", "");
                if (!webhook.isEmpty()) {
                    java.net.URL u = new java.net.URL(webhook);
                    String base = u.getProtocol() + "://" + u.getHost()
                            + (u.getPort() > 0 ? ":" + u.getPort() : "");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(base + "/").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    ok = c.getResponseCode() == 200;
                    c.disconnect();
                }
            } catch (Exception ignored) {}
            final boolean res = ok;
            runOnUiThread(() -> {
                tvServerStatus.setText(res ? "SERVER: TERHUBUNG"
                        : "SERVER: TIDAK TERJANGKAU");
                paintDot(serverDot, res ? GREEN : PINK);
            });
        }).start();
    }

    private Button miniButton(String text, int fill) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fill == YELLOW ? BLACK : WHITE);
        b.setTextSize(14);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setBackground(brutalBox(fill));
        b.setPadding(0, 20, 0, 20);
        b.setAllCaps(true);
        return b;
    }

    // ---------- TAB: APLIKASI ----------

    private void buildTabApps() {
        tabApps.addView(sectionLabel("APLIKASI AKTIF"));
        LinearLayout selInner = new LinearLayout(this);
        selInner.setOrientation(LinearLayout.VERTICAL);
        selInner.setBackground(brutalBox(WHITE));
        selInner.setPadding(28, 28, 28, 28);
        selectedChips = new LinearLayout(this);
        selectedChips.setOrientation(LinearLayout.VERTICAL);
        selInner.addView(selectedChips);
        tabApps.addView(brutalWrap(selInner));

        tabApps.addView(sectionLabel("PILIH APLIKASI"));

        LinearLayout searchInner = new LinearLayout(this);
        searchInner.setOrientation(LinearLayout.VERTICAL);
        searchInner.setBackground(brutalBox(WHITE));
        searchInner.setPadding(28, 28, 28, 28);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        etSearch = new EditText(this);
        etSearch.setHint("Cari aplikasi...");
        etSearch.setTextSize(14);
        etSearch.setTextColor(BLACK);
        etSearch.setBackground(brutalBox(LIGHT_GRAY));
        etSearch.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        slp.setMargins(0, 0, 16, 0);
        etSearch.setLayoutParams(slp);

        Button btnRefresh = miniButton("MUAT ULANG", PINK);
        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, "Memuat ulang...", Toast.LENGTH_SHORT).show();
            savePrefsSilent();
            loadApps();
        });

        searchRow.addView(etSearch);
        searchRow.addView(btnRefresh);
        searchInner.addView(searchRow);

        tvCount = new TextView(this);
        tvCount.setTextSize(13);
        tvCount.setTextColor(GRAY);
        tvCount.setTypeface(tvCount.getTypeface(), Typeface.BOLD);
        tvCount.setPadding(4, 14, 0, 6);
        searchInner.addView(tvCount);

        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        searchInner.addView(appList);

        tabApps.addView(brutalWrap(searchInner));

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { filterApps(s.toString()); }
        });
    }

    // ---------- TAB: RIWAYAT ----------

    private void buildTabHistory() {
        tabHistory.addView(sectionLabel("RIWAYAT NOTIFIKASI"));

        // Kartu statistik hari ini
        LinearLayout statInner = new LinearLayout(this);
        statInner.setOrientation(LinearLayout.VERTICAL);
        statInner.setBackground(brutalBox(BLUE));
        statInner.setPadding(28, 24, 28, 24);
        TextView statLbl = new TextView(this);
        statLbl.setText("HARI INI");
        statLbl.setTextSize(12);
        statLbl.setTextColor(WHITE);
        statLbl.setTypeface(statLbl.getTypeface(), Typeface.BOLD);
        statInner.addView(statLbl);
        tvStats = new TextView(this);
        tvStats.setTextSize(16);
        tvStats.setTextColor(WHITE);
        tvStats.setTypeface(tvStats.getTypeface(), Typeface.BOLD);
        tvStats.setPadding(0, 8, 0, 0);
        statInner.addView(tvStats);
        tabHistory.addView(brutalWrap(statInner));

        tvHistoryEmpty = new TextView(this);
        tvHistoryEmpty.setText("Belum ada notifikasi.");
        tvHistoryEmpty.setTextColor(GRAY);
        tvHistoryEmpty.setTextSize(15);
        tvHistoryEmpty.setTypeface(tvHistoryEmpty.getTypeface(), Typeface.BOLD);
        tvHistoryEmpty.setGravity(Gravity.CENTER);
        tvHistoryEmpty.setPadding(0, 40, 0, 40);
        LinearLayout emptyInner = new LinearLayout(this);
        emptyInner.setBackground(brutalBox(WHITE));
        emptyInner.setPadding(28, 28, 28, 28);
        emptyInner.addView(tvHistoryEmpty);
        tabHistory.addView(brutalWrap(emptyInner));

        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        tabHistory.addView(historyList);

        tabHistory.addView(actionButton("Hapus Riwayat", PINK, v -> {
            new HistoryDb(this).clear();
            loadHistory();
            Toast.makeText(this, "Riwayat dihapus", Toast.LENGTH_SHORT).show();
        }));
    }

    // ---------- Data ----------

    private void savePrefs() {
        Set<String> sel = new HashSet<>();
        for (AppRow r : rows) {
            if (r.box != null && r.box.isChecked()) sel.add(r.pkg);
        }
        // Validasi nominal minimal
        String minAmt = etMinAmount.getText().toString().trim();
        if (minAmt.isEmpty()) minAmt = "0";
        try {
            if (Long.parseLong(minAmt) < 0) minAmt = "0";
        } catch (Exception e) {
            minAmt = "0";
        }
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
                .putString("webhook", etWebhook.getText().toString().trim())
                .putString("webhook_token", etToken.getText().toString().trim())
                .putString("device_name",
                        etDeviceName.getText().toString().trim())
                .putString("min_amount", minAmt)
                .putString("keywords", etKeywords.getText().toString().trim())
                .putStringSet("apps", sel).apply();
        Toast.makeText(this, sel.size() + " aplikasi tersimpan",
                Toast.LENGTH_SHORT).show();
        updateSelected();
        updateCount();
        checkServer();
    }

    private void savePrefsSilent() {
        Set<String> sel = new HashSet<>();
        for (AppRow r : rows) {
            if (r.box != null && r.box.isChecked()) sel.add(r.pkg);
        }
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
                .putStringSet("apps", sel).apply();
    }

    /** Daftar aplikasi aktif dengan logo, tiap baris dalam kartu brutal. */
    /** Tipe pembayaran per aplikasi: - / QRIS / TRANSFER. Ketuk untuk ganti. */
    private static final String[] PAY_TYPES = {"", "qris", "transfer"};
    private static final String[] PAY_LABELS = {"-", "QRIS", "TRANSFER"};

    private int payTypeColor(String t) {
        if ("qris".equals(t)) return BLUE;
        if ("transfer".equals(t)) return GREEN;
        return LIGHT_GRAY;
    }

    private void paintPayButton(Button b, String t) {
        int idx = 0;
        for (int i = 0; i < PAY_TYPES.length; i++) {
            if (PAY_TYPES[i].equals(t)) {
                idx = i;
                break;
            }
        }
        b.setText(PAY_LABELS[idx]);
        b.setTextColor(idx == 0 ? BLACK : WHITE);
        b.setBackground(brutalBox(payTypeColor(t)));
    }

    private void updateSelected() {
        selectedChips.removeAllViews();
        SharedPreferences prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        Set<String> sel = prefs.getStringSet("apps", new HashSet<>());
        if (sel.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Belum ada aplikasi dipilih.");
            tv.setTextColor(GRAY);
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            tv.setPadding(0, 12, 0, 12);
            selectedChips.addView(tv);
            return;
        }

        TextView typeHint = new TextView(this);
        typeHint.setText("KETUK LABEL UNTUK ATUR TIPE: QRIS / TRANSFER");
        typeHint.setTextSize(12);
        typeHint.setTextColor(GRAY);
        typeHint.setTypeface(typeHint.getTypeface(), Typeface.BOLD);
        typeHint.setPadding(4, 0, 0, 10);
        selectedChips.addView(typeHint);

        PackageManager pm = getPackageManager();
        for (String pkg : sel) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                String name = pm.getApplicationLabel(ai).toString();
                Drawable icon = pm.getApplicationIcon(ai);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(brutalBox(LIGHT_GRAY));
                row.setPadding(20, 16, 20, 16);

                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(96, 96);
                lp.setMargins(0, 0, 20, 0);
                iv.setLayoutParams(lp);

                TextView tv = new TextView(this);
                tv.setText(name);
                tv.setTextSize(16);
                tv.setTextColor(BLACK);
                tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                Button typeBtn = new Button(this);
                typeBtn.setTextSize(13);
                typeBtn.setTypeface(typeBtn.getTypeface(), Typeface.BOLD);
                typeBtn.setAllCaps(true);
                typeBtn.setPadding(18, 14, 18, 14);
                String cur = prefs.getString("app_type_" + pkg, "");
                paintPayButton(typeBtn, cur);
                typeBtn.setOnClickListener(v -> {
                    SharedPreferences p =
                            getSharedPreferences("cfg", MODE_PRIVATE);
                    String curT = p.getString("app_type_" + pkg, "");
                    int idx = 0;
                    for (int i = 0; i < PAY_TYPES.length; i++) {
                        if (PAY_TYPES[i].equals(curT)) {
                            idx = i;
                            break;
                        }
                    }
                    String next = PAY_TYPES[(idx + 1) % PAY_TYPES.length];
                    p.edit().putString("app_type_" + pkg, next).apply();
                    paintPayButton(typeBtn, next);
                });

                row.addView(iv);
                row.addView(tv);
                row.addView(typeBtn);
                selectedChips.addView(brutalWrap(row));
            } catch (Exception ignored) {}
        }
    }

    private void loadApps() {
        tvCount.setText("Memuat aplikasi...");
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed =
                    pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> enabled = getSharedPreferences("cfg", MODE_PRIVATE)
                    .getStringSet("apps", new HashSet<>());

            List<AppRow> userApps = new ArrayList<>();
            List<AppRow> sysApps = new ArrayList<>();
            for (ApplicationInfo ai : installed) {
                if (getPackageName().equals(ai.packageName)) continue;
                AppRow r = new AppRow();
                r.pkg = ai.packageName;
                try {
                    r.name = pm.getApplicationLabel(ai).toString();
                    r.icon = pm.getApplicationIcon(ai);
                } catch (Exception e) {
                    r.name = ai.packageName;
                }
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                    sysApps.add(r);
                } else {
                    userApps.add(r);
                }
            }
            Collections.sort(userApps, (a, b2) ->
                    a.name.compareToIgnoreCase(b2.name));
            Collections.sort(sysApps, (a, b2) ->
                    a.name.compareToIgnoreCase(b2.name));
            userApps.addAll(sysApps);

            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(userApps);
                appList.removeAllViews();
                for (AppRow r : userApps) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackground(brutalBox(WHITE));
                    row.setPadding(20, 16, 20, 16);

                    ImageView iv = new ImageView(this);
                    if (r.icon != null) iv.setImageDrawable(r.icon);
                    LinearLayout.LayoutParams lp =
                            new LinearLayout.LayoutParams(96, 96);
                    lp.setMargins(0, 0, 20, 0);
                    iv.setLayoutParams(lp);

                    CheckBox cb = new CheckBox(this);
                    cb.setText(r.name);
                    cb.setTextSize(15);
                    cb.setTextColor(BLACK);
                    cb.setTypeface(cb.getTypeface(), Typeface.BOLD);
                    cb.setChecked(enabled.contains(r.pkg));
                    cb.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    cb.setOnCheckedChangeListener((b2, c) -> updateCount());

                    row.addView(iv);
                    row.addView(cb);
                    row.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));

                    r.box = cb;
                    r.layout = row;
                    // Bungkus tiap baris dengan shadow
                    View wrapped = brutalWrap(row);
                    r.layout.setTag(wrapped);
                    appList.addView(wrapped);
                }
                updateCount();
                updateSelected();
            });
        }).start();
    }

    private void filterApps(String q) {
        q = q.toLowerCase().trim();
        for (AppRow r : rows) {
            boolean show = q.isEmpty() ||
                    r.name.toLowerCase().contains(q) ||
                    r.pkg.toLowerCase().contains(q);
            View wrapped = (View) r.layout.getTag();
            if (wrapped != null)
                wrapped.setVisibility(show ? View.VISIBLE : View.GONE);
            else if (r.layout != null)
                r.layout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateCount() {
        int n = 0;
        for (AppRow r : rows) if (r.box != null && r.box.isChecked()) n++;
        tvCount.setText(n + " APLIKASI DIPILIH");
    }

    /** Tiap riwayat jadi kartu brutal sendiri. */
    private void loadHistory() {
        historyList.removeAllViews();
        HistoryDb hdb = new HistoryDb(this);
        long[] stats = hdb.todayStats();
        String totalStr = "Rp " + String.format(Locale.getDefault(),
                "%,d", stats[1]).replace(',', '.');
        tvStats.setText(stats[0] + " transaksi\n" + totalStr);
        List<HistoryDb.Entry> entries = hdb.list(30);
        tvHistoryEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        // Sembunyikan kartu empty bila ada isi
        ((View) tvHistoryEmpty.getParent().getParent())
                .setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm",
                Locale.getDefault());
        for (HistoryDb.Entry e : entries) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(brutalBox(WHITE));
            card.setPadding(28, 24, 28, 24);

            // Baris atas: sumber + waktu
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvSource = new TextView(this);
            tvSource.setText(e.source.toUpperCase(Locale.getDefault()));
            tvSource.setTextSize(13);
            tvSource.setTextColor(WHITE);
            tvSource.setTypeface(tvSource.getTypeface(), Typeface.BOLD);
            tvSource.setBackground(brutalBox(BLACK));
            tvSource.setPadding(16, 8, 16, 8);
            tvSource.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvTime = new TextView(this);
            tvTime.setText(sdf.format(new Date(e.time)));
            tvTime.setTextSize(12);
            tvTime.setTextColor(GRAY);
            tvTime.setGravity(Gravity.END);

            topRow.addView(tvSource);
            topRow.addView(tvTime);
            card.addView(topRow);

            // Nominal besar
            if (e.amount > 0) {
                TextView tvAmt = new TextView(this);
                tvAmt.setText("Rp " + String.format(Locale.getDefault(),
                        "%,d", e.amount).replace(',', '.'));
                tvAmt.setTextSize(26);
                tvAmt.setTextColor(BLACK);
                tvAmt.setTypeface(tvAmt.getTypeface(), Typeface.BOLD);
                tvAmt.setPadding(0, 16, 0, 4);
                // Background highlight kuning untuk nominal
                LinearLayout amtWrap = new LinearLayout(this);
                amtWrap.setBackground(brutalBox(YELLOW));
                amtWrap.setPadding(16, 8, 16, 8);
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                alp.setMargins(0, 16, 0, 4);
                amtWrap.setLayoutParams(alp);
                amtWrap.addView(tvAmt);
                card.addView(amtWrap);
            }

            // Isi notif
            TextView tvBody = new TextView(this);
            String body = e.title;
            if (!e.title.isEmpty() && !e.text.isEmpty()) body += " — ";
            body += e.text;
            tvBody.setText(body);
            tvBody.setTextSize(14);
            tvBody.setTextColor(BLACK);
            tvBody.setPadding(0, 12, 0, 0);
            tvBody.setMaxLines(3);
            card.addView(tvBody);

            historyList.addView(brutalWrap(card));
        }
    }

    private void testWebhook() {
        String url = etWebhook.getText().toString().trim();
        String token = etToken.getText().toString().trim();
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection c =
                        (java.net.HttpURLConnection) u.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(10000);
                c.setDoOutput(true);
                String p = "{\"token\":" + jsonStr(token) +
                        ",\"source\":\"TEST\",\"title\":\"Tes koneksi\"," +
                        "\"text\":\"Koneksi OK Rp1.000\",\"amount\":1000,\"time\":" +
                        System.currentTimeMillis() + "}";
                c.getOutputStream().write(p.getBytes("UTF-8"));
                int code = c.getResponseCode();
                runOnUiThread(() -> Toast.makeText(this,
                        "Test: HTTP " + code, Toast.LENGTH_SHORT).show());
                c.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static String jsonStr(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = isListenerEnabled();
        tvStatus.setText(enabled ? "AKTIF — siap menerima" : "BELUM AKTIF");
        // Update dot warna
        try {
            LinearLayout cardInner =
                    (LinearLayout) ((View) tvStatus.getParent()).getParent();
            // cardInner adalah FrameLayout content; dot ada di statusRow
            LinearLayout statusRow = (LinearLayout) cardInner.getChildAt(0);
            View dot = statusRow.getChildAt(0);
            paintDot(dot, enabled ? GREEN : PINK);
        } catch (Exception ignored) {}

        // Terakhir terkirim + antrean
        SharedPreferences prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        String last = prefs.getString("last_sent", null);
        tvLastSent.setText(last != null ? last : "Belum ada pengiriman.");
        int pending = new PendingDb(this).count();
        if (pending > 0) {
            tvPending.setText("ANTREAN: " + pending + " belum terkirim");
            tvPending.setTextColor(PINK);
        } else {
            tvPending.setText("ANTREAN KOSONG");
            tvPending.setTextColor(GREEN);
        }

        // Cek server + flush antrean di background
        checkServer();
        new Thread(() -> Webhook.flushPending(this)).start();
    }
}
