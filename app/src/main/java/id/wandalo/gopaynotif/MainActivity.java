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
    private EditText etWebhook, etSearch;
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

        TextView subtitle = new TextView(this);
        subtitle.setText("Teruskan notifikasi ke server. Realtime.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(GRAY);
        subtitle.setPadding(4, 0, 0, 20);
        root.addView(subtitle);

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
        // Kartu status
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

        TextView hint = new TextView(this);
        hint.setText("Aplikasi membaca notifikasi dari aplikasi yang dipilih, " +
                "lalu mengirimkannya ke webhook secara langsung.");
        hint.setTextSize(13);
        hint.setTextColor(GRAY);
        hint.setPadding(0, 16, 0, 0);
        cardInner.addView(hint);

        tabStatus.addView(brutalWrap(cardInner));
        // simpan dot untuk update
        cardInner.setTag(dot);

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

        // Webhook
        tabStatus.addView(sectionLabel("WEBHOOK SERVER"));
        LinearLayout wbInner = new LinearLayout(this);
        wbInner.setOrientation(LinearLayout.VERTICAL);
        wbInner.setBackground(brutalBox(WHITE));
        wbInner.setPadding(28, 28, 28, 28);

        etWebhook = new EditText(this);
        etWebhook.setHint("https://...");
        etWebhook.setTextSize(14);
        etWebhook.setTextColor(BLACK);
        etWebhook.setBackground(brutalBox(LIGHT_GRAY));
        etWebhook.setPadding(20, 18, 20, 18);
        wbInner.addView(etWebhook);

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

        SharedPreferences prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        etWebhook.setText(prefs.getString("webhook",
                "https://gopay.wandalo.dev/api/notif"));
        btnSave.setOnClickListener(v -> savePrefs());
        btnTest.setOnClickListener(v -> testWebhook());
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
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
                .putString("webhook", etWebhook.getText().toString().trim())
                .putStringSet("apps", sel).apply();
        Toast.makeText(this, sel.size() + " aplikasi tersimpan",
                Toast.LENGTH_SHORT).show();
        updateSelected();
        updateCount();
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

                View badge = new View(this);
                badge.setBackground(brutalBox(GREEN));
                badge.setLayoutParams(new LinearLayout.LayoutParams(28, 28));

                row.addView(iv);
                row.addView(tv);
                row.addView(badge);
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
        List<HistoryDb.Entry> entries = new HistoryDb(this).list(30);
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
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                java.net.HttpURLConnection c =
                        (java.net.HttpURLConnection) u.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(10000);
                c.setDoOutput(true);
                String p = "{\"source\":\"TEST\",\"title\":\"Tes koneksi\"," +
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
            GradientDrawable d = new GradientDrawable();
            d.setColor(enabled ? GREEN : PINK);
            d.setStroke(BORDER, BLACK);
            dot.setBackground(d);
        } catch (Exception ignored) {}
    }
}
