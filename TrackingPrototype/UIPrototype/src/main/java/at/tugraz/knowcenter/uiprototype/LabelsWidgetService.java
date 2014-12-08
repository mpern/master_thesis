package at.tugraz.knowcenter.uiprototype;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.SimpleDateFormat;
import java.util.Date;

import at.tugraz.knowcenter.label.contentproviders.LabelContract;

import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.DATE_PATTERN;
/**
 * Created by Markus Perndorfer on 08.12.13.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class LabelsWidgetService extends RemoteViewsService {


    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new LabelsRemoveViewFactory(this.getApplicationContext(), intent);
    }

    private static class LabelsRemoveViewFactory implements RemoteViewsFactory {
        private Context context;
        private Cursor cursor = null;

        private static final String[] QUERY_PROJECTION = new String[]{LabelContract.Active._ID, LabelContract.Active.TEXT, LabelContract.Active.ACTIVATED_TS};
        private static final String SELECTION =  LabelContract.Active.DEACTIVATED_TS + " is null";

        ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
            @Override
            public SimpleDateFormat get() {
               return new SimpleDateFormat(DATE_PATTERN);
            }
        };

        public LabelsRemoveViewFactory(Context applicationContext, Intent intent) {
            context = applicationContext;
        }

        @Override
        public void onCreate() {
            cursor = context.getContentResolver().query(LabelContract.Active.CONTENT_URI, QUERY_PROJECTION, SELECTION, null, null);
        }

        @Override
        public void onDataSetChanged() {
            if(cursor != null){
                cursor.close();
                cursor = null;
            }
            cursor = context.getContentResolver().query(LabelContract.Active.CONTENT_URI, QUERY_PROJECTION, SELECTION, null, null);
        }

        @Override
        public void onDestroy() {
            if(cursor != null) {
                cursor.close();
            }
        }

        @Override
        public int getCount() {
            if(cursor != null)
                return cursor.getCount();
            return 0;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if(cursor != null) {
                cursor.moveToPosition(position);
                String labelText = cursor.getString(cursor.getColumnIndexOrThrow(LabelContract.Active.TEXT));
                Long ts = cursor.getLong(cursor.getColumnIndexOrThrow(LabelContract.Active.ACTIVATED_TS));
                String dateString = dateFormat.get().format(new Date(ts));

                RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item);
                rv.setTextViewText(R.id.widget_text_view, labelText);
                rv.setTextViewText(R.id.widget_date, dateString);

                return rv;
            }
            return null;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            if(cursor != null) {
                cursor.moveToPosition(position);
                return cursor.getLong(cursor.getColumnIndexOrThrow(LabelContract.Active._ID));
            }
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
