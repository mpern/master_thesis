package at.tugraz.knowcenter.uiprototype.tasks;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import at.tugraz.knowcenter.label.contentproviders.LabelContract;
import at.tugraz.knowcenter.uiprototype.R;

import edu.mit.media.funf.util.FileUtil;

import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.TAG;

/**
* Created by Markus Perndorfer on 15.01.14.
*/
public class ExportLabelsTask extends AsyncTask<Object, Object, Boolean> {
    private final ContentResolver resolver;
    private final Context context;

    private static CellProcessor[] getProcessors() {

        final CellProcessor[] processors = new CellProcessor[]{
                new NotNull(), // ID
                new NotNull(), // Text
                new Optional(), //last used
                new Optional() // usage count
        };

        return processors;
    }

    public ExportLabelsTask() {
        throw new UnsupportedOperationException();
    }

    public ExportLabelsTask(ContentResolver resolver, Context context) {
        super();
        this.resolver = resolver;
        this.context = context;
    }

    private static final String[] PROJECTION = new String[]{LabelContract.AllLabels._ID, LabelContract.AllLabels.TEXT, LabelContract.AllLabels.LAST_USED, LabelContract.AllLabels.USAGE_COUNT};

    private String error_msg;
    private String file_name;

    @Override
    protected Boolean doInBackground(Object... params) {
        Cursor c = null;
        CsvMapWriter mapWriter = null;

        if(!isExternalStorageWritable()) {
            error_msg = "no external storage writeable!";
            return false;
        }

        try {

            String path = FileUtil.getSdCardPath(context) + "labels/";
            File archiveFile = new File(path + "labels_export.csv");
            archiveFile.mkdirs();
            int i = 0;
            while (archiveFile.exists()) {
                i++;
                file_name = String.format("%slabels_export_%d.csv", path, i);
                archiveFile = new File(file_name);
            }

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(tz);

            c = resolver.query(LabelContract.AllLabels.CONTENT_URI, PROJECTION, null, null, null);
            if (c != null && c.isBeforeFirst() && c.getCount() > 0) {

                mapWriter = new CsvMapWriter(new FileWriter(archiveFile),
                        CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
                mapWriter.writeHeader(PROJECTION);
                while (c.moveToNext()) {
                    Map<String, Object> line = new HashMap<>();
                    Long last_date = c.getLong(c.getColumnIndex(PROJECTION[2]));
                    String date_string = last_date > 0 ? df.format(new Date(last_date)) : null;
                    line.put(PROJECTION[0], c.getLong(c.getColumnIndex(PROJECTION[0]))); //id
                    line.put(PROJECTION[1], c.getString(c.getColumnIndex(PROJECTION[1])));//text
                    line.put(PROJECTION[2], date_string);//last used
                    line.put(PROJECTION[3], c.getLong(c.getColumnIndex(PROJECTION[3])));//usage count

                    mapWriter.write(line, PROJECTION, getProcessors());
                }
            } else {
                error_msg = "no labels to export";
                return false;
            }
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
            error_msg = ex.getMessage();
            return false;
        } finally {
            if (c != null) c.close();
            if (mapWriter != null)
                try {
                    mapWriter.close();
                } catch (IOException e) {
                    Log.e(TAG, "could not close csv", e);
                }
        }
        return true;
    }

    public boolean isExternalStorageWritable() {
        try {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                return true;
            }
        } catch (Exception ex) {
        }
        return false;
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        if(aBoolean != null && Boolean.TRUE.equals(aBoolean))
            Toast.makeText(context, context.getString(R.string.export_success, file_name), Toast.LENGTH_SHORT).show();
        else {
            Toast.makeText(context, error_msg, Toast.LENGTH_SHORT).show();
        }
    }
}
