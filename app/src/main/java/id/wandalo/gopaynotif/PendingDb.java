package id.wandalo.gopaynotif;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Antrean payload yang gagal terkirim (offline) — dikirim ulang otomatis. */
public class PendingDb extends SQLiteOpenHelper {
    private static final String DB = "pending_queue.db";
    private static final int VER = 1;

    public static class Item {
        public long id, time;
        public String payload;
    }

    public PendingDb(Context ctx) {
        super(ctx, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pending (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "payload TEXT, time INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS pending");
        onCreate(db);
    }

    public void add(String payload, long time) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("payload", payload);
        v.put("time", time);
        db.insert("pending", null, v);
        db.execSQL("DELETE FROM pending WHERE id NOT IN " +
                "(SELECT id FROM pending ORDER BY time DESC LIMIT 500)");
        db.close();
    }

    public List<Item> list() {
        List<Item> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id,payload,time FROM pending ORDER BY time ASC", null);
        while (c.moveToNext()) {
            Item it = new Item();
            it.id = c.getLong(0);
            it.payload = c.getString(1);
            it.time = c.getLong(2);
            out.add(it);
        }
        c.close();
        db.close();
        return out;
    }

    public void remove(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("pending", "id=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public int count() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM pending", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        db.close();
        return n;
    }
}
