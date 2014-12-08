package at.tugraz.knowcenter.funf.probes;

import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.tugraz.knowcenter.funf.probes.util.MillisecondToSecondConverterCell;
import at.tugraz.knowcenter.label.contentproviders.LabelContract;
import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.builtin.ContentProviderProbe;
import edu.mit.media.funf.time.DecimalTimeUnit;

/**
 * Created by Markus Perndorfer on 23.10.13.
 */
@Schedule.DefaultSchedule(interval =  1800)
@Probe.DisplayName("Active Labels Probe")
public class LabelProbe extends ContentProviderProbe implements Probe.ContinuableProbe {

    public static final String TAG = "LabelProbe";
    @Override
    protected Map<String, CursorCell<?>> getProjectionMap() {
        Map<String, CursorCell<?>> activeLabelsProjection = new HashMap<String, CursorCell<?>>();
        activeLabelsProjection.put(LabelContract.Active._ID, ContentProviderProbe.longCell());
        activeLabelsProjection.put(LabelContract.Active.LABEL_ID, ContentProviderProbe.longCell());
        activeLabelsProjection.put(LabelContract.Active.TEXT, ContentProviderProbe.stringCell());
        activeLabelsProjection.put(LabelContract.Active.ACTIVATED_TS, new MillisecondToSecondConverterCell());
        activeLabelsProjection.put(LabelContract.Active.DEACTIVATED_TS, new MillisecondToSecondConverterCell());

        return activeLabelsProjection;
    }


    protected Uri getContentProviderUri() {
        return LabelContract.Active.CONTENT_URI;
    }


    protected String getDateColumnName() {
        return LabelContract.Active.ACTIVATED_TS;
    }


    private BigDecimal latestTimestamp = null;

    @Override
    protected Cursor getCursor(String[] projection) {
        String dateColumn = getDateColumnName();
        // Used the code below when we specified projection exactly
        List<String> projectionList = Arrays.asList(projection);
        if (!Arrays.asList(projection).contains(dateColumn)) {
            projectionList = new ArrayList<String>(projectionList);
            projectionList.add(dateColumn);
            projection = new String[projectionList.size()];
            projectionList.toArray(projection);
        }
        String selection = "";
        String[] dateFilterParams = null;
        if (latestTimestamp != null) {
            selection = dateColumn + " > ?";
            dateFilterParams = new String[] {String.valueOf(String.valueOf(getDateColumnTimeUnit().convert(latestTimestamp, DecimalTimeUnit.SECONDS)))};
            selection += " AND ";
        }
        selection += " " + LabelContract.Active.DEACTIVATED_TS + " is not null";
        return getContext().getContentResolver().query(
                getContentProviderUri(),
                projection, // TODO: different platforms have different fields supported for content providers, need to resolve this
                selection,
                dateFilterParams,
                dateColumn + " DESC");
    }


    protected DecimalTimeUnit getDateColumnTimeUnit() {
        return DecimalTimeUnit.SECONDS;
    }


    @Override
    protected void sendData(JsonObject data) {
        super.sendData(data);
        latestTimestamp = getTimestamp(data);
        Long id = data.get(LabelContract.Active._ID).getAsLong();
        //cleanup exported data
        if(id != null) {
            Uri delete = Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, id.toString());
            getContext().getContentResolver().delete(delete, null, null);
            if(Log.isLoggable(TAG, Log.VERBOSE))
                Log.v(TAG, String.format("Deleted tracking data %s", delete));
        }

    }

    @Override
    protected BigDecimal getTimestamp(JsonObject data) {
        return getDateColumnTimeUnit().toSeconds(data.get(getDateColumnName()).getAsLong());
    }

    @Override
    public JsonElement getCheckpoint() {
        return getGson().toJsonTree(latestTimestamp);
    }

    @Override
    public void setCheckpoint(JsonElement checkpoint) {
        latestTimestamp = checkpoint == null || checkpoint.isJsonNull() ? null : checkpoint.getAsBigDecimal();
    }
}
