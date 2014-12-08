package at.tugraz.knowcenter.funf.probes;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONArray;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import at.tugraz.knowcenter.funf.probes.util.MillisecondToSecondConverterCell;
import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.builtin.ContentProviderProbe;

/**
 * Created by Markus Perndorfer on 27.01.14.
 * returns every calendar event for the current day (now)
 */
@Probe.DisplayName("Calendar Probe")
@Probe.RequiredPermissions(Manifest.permission.READ_CALENDAR)
@Schedule.DefaultSchedule(interval = 86400)
public class CalendarProbe extends ContentProviderProbe {

    public static final String ATTENDEES_KEY = "ATTENDEES";

    private static class NoOpCursor extends AbstractCursor {
        @Override
        public int getCount() {
            return 0;
        }

        @Override
        public String[] getColumnNames() {
            return new String[0];
        }

        @Override
        public String getString(int column) {
            return null;
        }

        @Override
        public short getShort(int column) {
            return 0;
        }

        @Override
        public int getInt(int column) {
            return 0;
        }

        @Override
        public long getLong(int column) {
            return 0;
        }

        @Override
        public float getFloat(int column) {
            return 0;
        }

        @Override
        public double getDouble(int column) {
            return 0;
        }

        @Override
        public boolean isNull(int column) {
            return true;
        }
    }
    @Override
    protected Map<String, CursorCell<?>> getProjectionMap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH){
            return Collections.EMPTY_MAP;
        }
        else {
            return getCalendarProjectionMap();
        }
    }

    private Map<String, CursorCell<?>> calendarProjectionMap;
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private Map<String, CursorCell<?>> getCalendarProjectionMap() {
        if(calendarProjectionMap == null) {
            Map<String,CursorCell<?>> projectionKeyToType = new HashMap<String, CursorCell<?>>();

            projectionKeyToType.put(CalendarContract.Instances._ID, longCell());
            projectionKeyToType.put(CalendarContract.Instances.EVENT_ID, longCell());
            projectionKeyToType.put(CalendarContract.Instances.BEGIN, new MillisecondToSecondConverterCell());
            projectionKeyToType.put(CalendarContract.Instances.END, new MillisecondToSecondConverterCell());
            projectionKeyToType.put(CalendarContract.Events.TITLE, sensitiveStringCell());
            projectionKeyToType.put(CalendarContract.Events.HAS_ATTENDEE_DATA, booleanCell());
            calendarProjectionMap = projectionKeyToType;
        }
        return calendarProjectionMap;
    }



    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected JsonObject parseData(Cursor cursor, String[] projection, Map<String, CursorCell<?>> projectionMap) {
       JsonObject data = super.parseData(cursor, projection, projectionMap);

       JsonElement hasAttendeeData = data.get(CalendarContract.Events.HAS_ATTENDEE_DATA);
        if(hasAttendeeData != null && hasAttendeeData.getAsBoolean()) {
            JsonArray attendees = new JsonArray();
            long id = data.get(CalendarContract.Instances.EVENT_ID).getAsLong();
            //Uri event = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
            Cursor c = CalendarContract.Attendees.query(getContext().getContentResolver(), id, attendee_projection);

            try {
                if(c != null) {
                    while(c.moveToNext()) {
                        JsonObject attendee = super.parseData(c, getAttendeeProjection(), getAttendeeProjectionMap());
                        attendees.add(attendee);
                    }
                    data.add(ATTENDEES_KEY, attendees);
                }
            } finally {
                if ( c != null) c.close();
            }
        }
        return data;
    }

    private  String[] attendee_projection;
    private String[] getAttendeeProjection() {
        if(attendee_projection == null)
            attendee_projection = getAttendeeProjectionMap().keySet().toArray(new String[0]);
        return  attendee_projection;
    }

    private Map<String, CursorCell<?>> attendeeProjectionMap;
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private Map<String, CursorCell<?>> getAttendeeProjectionMap() {
        if(attendeeProjectionMap == null) {
            Map<String,CursorCell<?>> projectionKeyToType = new HashMap<String, CursorCell<?>>();
            projectionKeyToType.put(CalendarContract.Attendees._ID, longCell());
            projectionKeyToType.put(CalendarContract.Attendees.ATTENDEE_NAME, sensitiveStringCell());
            projectionKeyToType.put(CalendarContract.Attendees.ATTENDEE_EMAIL, sensitiveStringCell());
            attendeeProjectionMap = projectionKeyToType;
        }
        return attendeeProjectionMap;
    }

    @Override
    protected BigDecimal getTimestamp(JsonObject data) {
        return data.get(CalendarContract.Instances.BEGIN).getAsBigDecimal();
    }

    @Override
    protected Cursor getCursor(String[] projection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH){
            return new NoOpCursor();
        }
        else {
            return queryCalendarData(projection);
        }

    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private Cursor queryCalendarData(String[] projection) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);

        long dayStart = now.getTimeInMillis();

        now.set(Calendar.HOUR_OF_DAY, 23);
        now.set(Calendar.MINUTE, 59);
        now.set(Calendar.SECOND, 59);

        long dayEnd = now.getTimeInMillis();

        return CalendarContract.Instances.query(getContext().getContentResolver(), projection, dayStart, dayEnd);
    }
}
