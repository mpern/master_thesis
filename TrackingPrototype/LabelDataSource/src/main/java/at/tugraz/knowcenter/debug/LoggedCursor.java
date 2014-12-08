package at.tugraz.knowcenter.debug;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.util.Log;

/**
 * Created by Markus Perndorfer on 11.10.13.
 */
public class LoggedCursor extends SQLiteCursor {
    private static final String TAG = "CURSOR";

    public LoggedCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
        super(db, driver, editTable, query);
    }


    @Override
    public void close() {
        Log.d(TAG, "Cursor closed by:", new RuntimeException("Stack trace"));
        super.close();
    }
}