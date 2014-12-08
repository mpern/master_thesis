package at.tugraz.knowcenter.label.model;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import java.util.Date;

import at.tugraz.knowcenter.label.contentproviders.LabelContract;

/**
 * Created by Markus Perndorfer on 23.09.13.
 */
public class LabelHelper {
    ContentResolver resolver;

    public LabelHelper(ContentResolver r) {
        resolver = r;
    }

    private LabelHelper(){
        throw new UnsupportedOperationException();
    }

    public int deactivateLabel(Uri update_uri, long deactivateTS) {
        ContentValues cv = new ContentValues();
        cv.put(LabelContract.Active.DEACTIVATED_TS, deactivateTS);

        return resolver.update(update_uri, cv, null, null);
    }

    public int deactivateLabel(Uri update_uri, Date when) {
        return deactivateLabel(update_uri, when.getTime());
    }

    public int deactivateLabel(Uri update_uri) {
        return deactivateLabel(update_uri, System.currentTimeMillis());
    }

    public int deactivateLabel(long active_id, Date when) {
        Uri update_uri = Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, String.valueOf(active_id));

        return deactivateLabel(update_uri, when);
    }

    public int deactivateLabel(long active_id) {
        return deactivateLabel(active_id, new Date());
    }

    public Uri activateLabel(long all_label_id) {

        return activateLabel(all_label_id, System.currentTimeMillis());
    }

    public Uri activateLabel(long labelId, long activatedTS) {

        ContentValues currentLabel = new ContentValues();
        currentLabel.put(LabelContract.Active.LABEL_ID, labelId);
        currentLabel.put(LabelContract.Active.ACTIVATED_TS, activatedTS);
        final Uri active_label = resolver.insert(LabelContract.Active.CONTENT_URI, currentLabel);

        return active_label;
    }

    public Uri createNewLabel(String label_text) {
        Uri newUri;
        ContentValues newValues = new ContentValues();

        newValues.put(LabelContract.AllLabels.TEXT, label_text);

        newUri = resolver.insert(LabelContract.AllLabels.CONTENT_URI, newValues);

        return newUri;
    }

    public static AllLabelDTO convertCursorToLabel(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(LabelContract.AllLabels._ID));
        String text = c.getString(c.getColumnIndexOrThrow(LabelContract.AllLabels.TEXT));
        long last_used = c.getLong(c.getColumnIndexOrThrow(LabelContract.AllLabels.LAST_USED));
        long usage_count = c.getLong(c.getColumnIndexOrThrow(LabelContract.AllLabels.USAGE_COUNT));

        return new AllLabelDTO(id, text, last_used, usage_count);
    }

    public static ActiveLabelDTO convertCursorToActiveLabel(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(LabelContract.Active._ID));
        String text = c.getString(c.getColumnIndexOrThrow(LabelContract.Active.TEXT));
        long last_used = c.getLong(c.getColumnIndexOrThrow(LabelContract.Active.LAST_USED));
        long usage_count = c.getLong(c.getColumnIndexOrThrow(LabelContract.Active.USAGE_COUNT));
        long label_id = c.getLong(c.getColumnIndexOrThrow(LabelContract.Active.LABEL_ID));
        long activatedTs = c.getLong(c.getColumnIndexOrThrow(LabelContract.Active.ACTIVATED_TS));
        long deactivatedTs = c.getLong(c.getColumnIndexOrThrow(LabelContract.Active.DEACTIVATED_TS));

        return new ActiveLabelDTO(id, label_id, text, last_used, usage_count, activatedTs, deactivatedTs);
    }

    public static UnusedLabelDTO convertCursorToUnusedLabel(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(LabelContract.UnusedLabels._ID));
        String text = c.getString(c.getColumnIndexOrThrow(LabelContract.UnusedLabels.TEXT));
        long last_used = c.getLong(c.getColumnIndexOrThrow(LabelContract.UnusedLabels.LAST_USED));
        long usage_count = c.getLong(c.getColumnIndexOrThrow(LabelContract.UnusedLabels.USAGE_COUNT));

        return new UnusedLabelDTO(id, text, last_used, usage_count);
    }



}
