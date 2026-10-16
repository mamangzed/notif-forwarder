package id.wandalo.gopaynotif;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Simpan history notifikasi di HP (max 200). */
public class HistoryDb extends SQLiteOpenHelper {
    private static final String DB = "notif_history.db";
    private static final int VER = 1;

    public static class Entry {
        public long id, amount, time;
        public String source, pkg, title, text;
    }

    public HistoryDb(Context ctx) {
        super(ctx, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source TEXT, pkg TEXT, title TEXT, text TEXT," +
                "amount INTEGER, time INTEGER)");
        db.execSQL("CREATE INDEX idx_time ON history(time DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS history");
        onCreate(db);
    }

    public void add(String source, String pkg, String title,
                    String text, long amount, long time) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("source", source);
        v.put("pkg", pkg);
        v.put("title", title);
        v.put("text", text);
        v.put("amount", amount);
        v.put("time", time);
        db.insert("history", null, v);
        // Batasi 200
        db.execSQL("DELETE FROM history WHERE id NOT IN " +
                "(SELECT id FROM history ORDER BY time DESC LIMIT 200)");
        db.close();
    }

    public List<Entry> list(int limit) {
        List<Entry> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id,source,pkg,title,text,amount,time FROM history " +
                        "ORDER BY time DESC LIMIT ?", new String[]{String.valueOf(limit)});
        while (c.moveToNext()) {
            Entry e = new Entry();
            e.id = c.getLong(0);
            e.source = c.getString(1);
            e.pkg = c.getString(2);
            e.title = c.getString(3);
            e.text = c.getString(4);
            e.amount = c.getLong(5);
            e.time = c.getLong(6);
            out.add(e);
        }
        c.close();
        db.close();
        return out;
    }

    public void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM history");
        db.close();
    }

    /** Return {count, sumAmount} untuk hari ini (00:00 - sekarang). */
    public long[] todayStats() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COUNT(*), IFNULL(SUM(amount),0) FROM history " +
                        "WHERE time >= ?",
                new String[]{String.valueOf(start)});
        long[] out = new long[]{0, 0};
        if (c.moveToFirst()) {
            out[0] = c.getLong(0);
            out[1] = c.getLong(1);
        }
        c.close();
        db.close();
        return out;
    }
}
