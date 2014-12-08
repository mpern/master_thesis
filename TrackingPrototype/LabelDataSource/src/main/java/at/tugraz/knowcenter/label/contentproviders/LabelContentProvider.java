package at.tugraz.knowcenter.label.contentproviders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class LabelContentProvider extends ContentProvider {
    private static final String TAG = "LabelContentProvider";

    private static final int DB_VERSION = 3;

    private static final int ACTIVE_LABELS_ID = 3;

    private static final int ACTIVE_LABELS = 2;

    private static final int ALL_LABELS_ID = 1;

    private static final int ALL_LABELS = 0;

    private static final String DBNAME = "LabelsDb";


    private static final UriMatcher URI_MATCHER = new UriMatcher(
            UriMatcher.NO_MATCH);

    private static final int UNUSED_LABELS_ID = 4;

    private static final int UNUSED_LABELS = 5;

    static {
        URI_MATCHER.addURI(LabelContract.AUTHORITY,
                LabelContract.AllLabels.TABLE_NAME, ALL_LABELS);
        URI_MATCHER.addURI(LabelContract.AUTHORITY,
                LabelContract.AllLabels.TABLE_NAME + "/#", ALL_LABELS_ID);

        URI_MATCHER.addURI(LabelContract.AUTHORITY,
                LabelContract.Active.TABLE_NAME, ACTIVE_LABELS);
        URI_MATCHER.addURI(LabelContract.AUTHORITY,
                LabelContract.Active.TABLE_NAME + "/#", ACTIVE_LABELS_ID);

        URI_MATCHER.addURI(LabelContract.AUTHORITY,
                LabelContract.UnusedLabels.TABLE_NAME, UNUSED_LABELS);
        URI_MATCHER.addURI(LabelContract.AUTHORITY,
                LabelContract.UnusedLabels.TABLE_NAME + "/#", UNUSED_LABELS_ID);
    }

    private static final Set<String> ALL_ALLOWED_INSERT = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList(LabelContract.AllLabels.TEXT)
    ));

    private static final Set<String> ACTIVE_ALLOWED_INSERT = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList(LabelContract.Active.LABEL_ID,
                          LabelContract.Active.ACTIVATED_TS,
                          LabelContract.Active.DEACTIVATED_TS)
    ));


    private SQLiteOpenHelper dbHelper;

    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case ALL_LABELS:
                return LabelContract.AllLabels.CONTENT_TYPE;
            case ALL_LABELS_ID:
                return LabelContract.AllLabels.CONTENT_ITEM_TYPE;
            case ACTIVE_LABELS:
                return LabelContract.Active.CONTENT_TYPE;
            case ACTIVE_LABELS_ID:
                return LabelContract.Active.CONTENT_ITEM_TYPE;
        }
        return null;
    }


    @Override
    public boolean onCreate() {
        dbHelper = new SqlHelper(getContext(), DBNAME, null, DB_VERSION);
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        long id;
        Uri newItem;
        String table = null;
        Uri base_uri = null;
        Set<String> keySet = getKeysFromContentValues(values);
        int match = URI_MATCHER.match(uri);
        switch (URI_MATCHER.match(uri)) {
            case ALL_LABELS:
                keySet.removeAll(ALL_ALLOWED_INSERT);
                table = LabelContract.AllLabels.TABLE_NAME;
                base_uri = LabelContract.AllLabels.CONTENT_URI;
                break;
            case ACTIVE_LABELS:
                keySet.removeAll(ACTIVE_ALLOWED_INSERT);
                table = LabelContract.Active.TABLE_NAME;
                base_uri = LabelContract.Active.CONTENT_URI;
                if(values.get(LabelContract.Active.ACTIVATED_TS) == null) {
                    values.put(LabelContract.Active.ACTIVATED_TS, new Date().getTime());
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid URI " + uri);
        }

        if (keySet.size() > 0)
            throw new IllegalArgumentException("Column(s) " + keySet.toString() + " not allowed for this operation");

        SQLiteDatabase db = null;
        try {

            db = dbHelper.getWritableDatabase();


            id = db.insertOrThrow(table, null, values);
            Log.d(TAG, "Created element " + uri);
            newItem = Uri.withAppendedPath(base_uri, String.valueOf(id));
            getContext().getContentResolver().notifyChange(uri, null);

            switch (match) {
                case ACTIVE_LABELS:
                    String label_id = values.getAsString(LabelContract.Active.LABEL_ID);
                    Uri label_uri = Uri.withAppendedPath(
                            LabelContract.AllLabels.CONTENT_URI,
                            label_id);
                    Cursor current_values = query(label_uri,
                            new String[]{LabelContract.AllLabels.USAGE_COUNT}, null, null, null);

                    int usage = 0;
                    if (!current_values.moveToNext()) {
                        RuntimeException ex = new RuntimeException("Could not find Label with ID " + label_id + " when trying to update usage stats");
                        Log.wtf(TAG, ex.getMessage(), ex);
                        throw ex;
                    }
                    int idx = current_values.getColumnIndexOrThrow(LabelContract.AllLabels.USAGE_COUNT);
                    if (!current_values.isNull(idx)) {
                        usage = current_values.getInt(idx);
                    }
                    usage += 1;

                    ContentValues update_values = new ContentValues();
                    update_values.put(LabelContract.AllLabels.LAST_USED, new Date().getTime());
                    update_values.put(LabelContract.AllLabels.USAGE_COUNT, usage);

                    update(label_uri, update_values, null, null);

                    current_values.close();

                case ALL_LABELS:
                    getContext().getContentResolver().notifyChange(LabelContract.UnusedLabels.CONTENT_URI, null);

            }
        } finally {
//            if (db != null)
//                db.close();
        }
        return newItem;
    }

    private Set<String> getKeysFromContentValues(ContentValues v) {
        Set<String> keySet = new HashSet<String>();

        for (Entry<String, Object> e : v.valueSet()) {
            keySet.add(e.getKey());
        }
        return keySet;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();

        List<String> args = selectionArgs == null ? new ArrayList<String>()
                : new ArrayList<String>(Arrays.asList(selectionArgs));

        String current_labels_join = "(select active." + LabelContract.Active._ID
                + ", active." + LabelContract.Active.LABEL_ID
                + ", active." + LabelContract.Active.ACTIVATED_TS
                + ", active." + LabelContract.Active.DEACTIVATED_TS
                + ", a." + LabelContract.Active.TEXT
                + ", a." + LabelContract.Active.LAST_USED
                + ", a." + LabelContract.Active.USAGE_COUNT
                + " from (" + LabelContract.Active.TABLE_NAME + " as active join "
                + LabelContract.AllLabels.TABLE_NAME + " as a on "
                + "active." + LabelContract.Active.LABEL_ID + " = "
                + "a." + LabelContract.AllLabels._ID + ") )";

        switch (URI_MATCHER.match(uri)) {
            case ALL_LABELS:
                builder.setTables(LabelContract.AllLabels.TABLE_NAME);

                break;
            case ALL_LABELS_ID:
                builder.setTables(LabelContract.AllLabels.TABLE_NAME);
                builder.appendWhere(LabelContract.AllLabels._ID + " = ?");
                args.add(0, uri.getLastPathSegment());
                break;
            case ACTIVE_LABELS:
                builder.setTables(current_labels_join);
                break;
            case ACTIVE_LABELS_ID:
                builder.setTables(current_labels_join);
                builder.appendWhere(LabelContract.Active._ID + " = ?");
                args.add(0, uri.getLastPathSegment());
                break;
            case UNUSED_LABELS:
                builder.setTables(LabelContract.AllLabels.TABLE_NAME);
                builder.appendWhere(LabelContract.AllLabels._ID + " not in ("
                        + " select " + LabelContract.Active.LABEL_ID + " from "
                        + LabelContract.Active.TABLE_NAME + " where " +
                        LabelContract.Active.DEACTIVATED_TS + " is null "
                        + " )");
                break;
            case UNUSED_LABELS_ID:
                builder.setTables(LabelContract.AllLabels.TABLE_NAME);
                builder.appendWhere(LabelContract.AllLabels._ID + " not in ("
                        + " select " + LabelContract.Active.LABEL_ID + " from "
                        + LabelContract.Active.TABLE_NAME + " where " +
                        LabelContract.Active.DEACTIVATED_TS + " is null "
                        + " ) and "
                        + LabelContract.AllLabels._ID + " = ?");
                args.add(0, uri.getLastPathSegment());
            default:
                throw new IllegalArgumentException("Unknwon URI " + uri);

        }
        if (TextUtils.isEmpty(sortOrder))
            sortOrder = "_ID ASC";

        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();
            Cursor cursor = builder.query(db, projection, selection,
                    args.toArray(new String[0]), null, null, sortOrder);
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
            return cursor;
        } finally {
//            if (db != null)
//                db.close();
        }

    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        String table_name;

        List<String> args = selectionArgs == null ? new ArrayList<String>()
                : new ArrayList<String>(Arrays.asList(selectionArgs));
        switch (URI_MATCHER.match(uri)) {
//            case ALL_LABELS:
//                table_name = LabelContract.AllLabels.TABLE_NAME;
//                break;
//            case ALL_LABELS_ID:
//                table_name = LabelContract.AllLabels.TABLE_NAME;
//                selection = LabelContract.AllLabels._ID
//                        + " = ? "
//                        + (TextUtils.isEmpty(selection) ? ""
//                        : (" AND " + selection));
//                args.add(0, uri.getLastPathSegment());
//                break;
            case ACTIVE_LABELS:
                table_name = LabelContract.Active.TABLE_NAME;
                break;
            case ACTIVE_LABELS_ID:
                table_name = LabelContract.Active.TABLE_NAME;
                selection = LabelContract.Active._ID
                        + " = ? "
                        + (TextUtils.isEmpty(selection) ? ""
                        : (" AND " + selection));
                args.add(0, uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknwon URI " + uri);

        }

        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();
            int numDel = db.delete(table_name, selection,
                    args.toArray(new String[0]));
            if (numDel > 0) {
                getContext().getContentResolver().notifyChange(uri, null);
                getContext().getContentResolver().notifyChange(LabelContract.UnusedLabels.CONTENT_URI, null);
            }

            return numDel;
        } finally {
//            if (db != null)
//                db.close();
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {

        String table_name;

        List<String> args = selectionArgs == null ? new ArrayList<String>()
                : new ArrayList<String>(Arrays.asList(selectionArgs));

        Set<String> keySet = getKeysFromContentValues(values);

        switch (URI_MATCHER.match(uri)) {
            case ALL_LABELS:
                table_name = LabelContract.AllLabels.TABLE_NAME;
                break;
            case ALL_LABELS_ID:
                table_name = LabelContract.AllLabels.TABLE_NAME;
                selection = LabelContract.AllLabels._ID
                        + " = ? "
                        + (TextUtils.isEmpty(selection) ? ""
                        : (" AND " + selection));
                args.add(0, uri.getLastPathSegment());
                break;
            case ACTIVE_LABELS:
                table_name = LabelContract.Active.TABLE_NAME;
                break;
            case ACTIVE_LABELS_ID:
                //throw new IllegalArgumentException("No update allowed for current labels");
                table_name = LabelContract.Active.TABLE_NAME;
                selection = LabelContract.Active._ID
                        + " = ? "
                        + (TextUtils.isEmpty(selection) ? ""
                        : (" AND " + selection));
                args.add(0, uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknwon URI " + uri);

        }

        SQLiteDatabase db = null;

        try {
            db = dbHelper.getWritableDatabase();
            int numUpd = db.update(table_name, values, selection,
                    args.toArray(new String[0]));
            if (numUpd > 0) {
                getContext().getContentResolver().notifyChange(uri, null);
                if (LabelContract.Active.TABLE_NAME == table_name &&
                    keySet.contains(LabelContract.Active.DEACTIVATED_TS))
                    getContext().getContentResolver().notifyChange(LabelContract.UnusedLabels.CONTENT_URI, null);
            }


            return numUpd;
        } finally {
//            if (db != null)
//                db.close();
        }


    }

    @Override
    public void shutdown() {
        dbHelper.getWritableDatabase().close();
    }

    protected static class SqlHelper extends SQLiteOpenHelper {

        public SqlHelper(Context context, String name, CursorFactory factory,
                         int version) {
            super(context, name, factory, version);
        }

        private static final String CREATE_ALL_SQL = "create table "
                + LabelContract.AllLabels.TABLE_NAME + " ( "
                + LabelContract.AllLabels._ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + LabelContract.AllLabels.TEXT + " TEXT UNIQUE NOT NULL, "
                + LabelContract.AllLabels.LAST_USED + " INTEGER, "
                + LabelContract.AllLabels.USAGE_COUNT + " INTEGER " + " ) ";

        private static final String CREATE_ACTIVE_SQL = "create table "
                + LabelContract.Active.TABLE_NAME + " ( "
                + LabelContract.Active._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + LabelContract.Active.LABEL_ID + " INTEGER NOT NULL, "
                + LabelContract.Active.ACTIVATED_TS + " LONG NOT NULL, "
                + LabelContract.Active.DEACTIVATED_TS + " LONG, "
                + " FOREIGN KEY ( " + LabelContract.Active.LABEL_ID + " ) REFERENCES " + LabelContract.AllLabels.TABLE_NAME + " ("
                + LabelContract.AllLabels._ID + ")" + " ) ";

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_ALL_SQL);
            db.execSQL(CREATE_ACTIVE_SQL);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "killing db");
            db.execSQL("drop table if exists "
                    + LabelContract.AllLabels.TABLE_NAME);
            db.execSQL("drop table if exists "
                    + LabelContract.Active.TABLE_NAME);
            onCreate(db);
        }

        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            if (!db.isReadOnly())
                db.execSQL("PRAGMA foreign_keys=ON;");
        }

    }

}
