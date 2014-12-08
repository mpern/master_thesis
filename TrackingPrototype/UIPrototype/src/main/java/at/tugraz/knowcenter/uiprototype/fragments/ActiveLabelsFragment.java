package at.tugraz.knowcenter.uiprototype.fragments;


import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.view.ActionMode;

import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.akalipetis.action_mode_list_fragment.ActionModeListFragment;
import com.akalipetis.action_mode_list_fragment.MultiChoiceModeListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import at.tugraz.knowcenter.label.contentproviders.LabelContract;
import at.tugraz.knowcenter.label.model.ActiveLabelDTO;
import at.tugraz.knowcenter.uiprototype.Dismissable;
import at.tugraz.knowcenter.label.model.LabelHelper;
import at.tugraz.knowcenter.uiprototype.LabelsWidgetProvider;
import at.tugraz.knowcenter.uiprototype.MainActivity;
import at.tugraz.knowcenter.uiprototype.R;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import de.timroes.swipetodismiss.SwipeDismissList;

import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.DATE_PATTERN;
import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.TAG;

/**
 * Created by Markus Perndorfer on 05.09.13.
 */
public class ActiveLabelsFragment extends Fragment implements MultiChoiceModeListener, Dismissable, ActiveLabelDetailsFragment.OnTimeChangedListener {

    public static final int[] LIST_TO = new int[]{R.id.list_item_checkable_active_text,
            R.id.list_item_active_id};
    public static final String[] LIST_FROM = new String[]{LabelContract.Active.TEXT,
            LabelContract.Active.ACTIVATED_TS};
    public static final String TIME_PICKER = "timePicker";
    private static String[] QUERY_PROJECTION = {LabelContract.Active._ID,
            LabelContract.Active.LABEL_ID,
            LabelContract.Active.TEXT,
            LabelContract.Active.LAST_USED,
            LabelContract.Active.USAGE_COUNT,
            LabelContract.Active.ACTIVATED_TS,
            LabelContract.Active.DEACTIVATED_TS
     };
    private TimePickerFragment newFragment;

    private ListFragment list;

    private ActionMode mode = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_current_labels, container, false);

        list = (ListFragment) getChildFragmentManager().findFragmentByTag(ListFragment.class.getCanonicalName());
        if(list == null) {
            list = new ListFragment();
        }
        list.setMultiChoiceModeListener(this);

        getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, list, ListFragment.class.getCanonicalName())
                .commit();

        getChildFragmentManager().executePendingTransactions();

        return rootView;

    }

    @Override
    public void onItemCheckedStateChanged(ActionMode actionMode, int position, long id, boolean checked) {

    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        mode = actionMode;
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        getActivity().getMenuInflater().inflate(R.menu.actionmode_active, menu);
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.active_select_all:
                selectAll();
                break;
            case R.id.active_delete:
                deletedSelected();
                break;
            case R.id.active_edit_end:
                editEndTime();
        }
        return true;
    }

    private void editEndTime() {
//        newFragment = (TimePickerFragment) getChildFragmentManager().findFragmentByTag(TIME_PICKER);
//        if(newFragment == null)
//            newFragment = new TimePickerFragment();
//
//        //newFragment.updateTime(20, 00, 00);
//
//        newFragment.show(getChildFragmentManager(), TIME_PICKER);
        List<ActiveLabelDTO> selected = list.getSelected();
        Calendar start = null;

        if(selected.size() == 1) {
            start = Calendar.getInstance();
            start.setTimeInMillis(selected.get(0).activatedTS);
        }
        ActiveLabelDetailsFragment fragment = (ActiveLabelDetailsFragment) getChildFragmentManager().findFragmentByTag(TIME_PICKER);
        if(fragment == null) {
            fragment = new ActiveLabelDetailsFragment();
        }
        fragment.setStartTime(start);
        fragment.show(getChildFragmentManager(), TIME_PICKER);
    }

    private void selectAll() {
        ListView listView = list.getListView();

        for (int i = 0, lim = listView.getAdapter().getCount(); i < lim; ++i)
            listView.setItemChecked(i, true);
    }

    private void deletedSelected() {
        list.deleteSelected();
        mode.finish();
    }


    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mode = null;
    }

    @Override
    public void dismiss() {
        if (mode != null) {
            mode.finish();
        }
        list.dismiss();
    }

    @Override
    public void onLabelTimeChanged(final Calendar start, final Calendar end) {
        List<ActiveLabelDTO> selected = list.getSelected();
        int faulty = 0;

        for(final ActiveLabelDTO l : selected) {
            final Uri update_uri = Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, Long.toString(l.id));
            ContentValues values = new ContentValues();
            if(start != null) {
                values.put(LabelContract.Active.ACTIVATED_TS, start.getTimeInMillis());
            }

            if(end != null) {
                Calendar compareTo = Calendar.getInstance();
                compareTo.setTimeInMillis(l.activatedTS);
                if(start != null)
                    compareTo = start;
                if(compareTo.compareTo(end) <= 0)
                    values.put(LabelContract.Active.DEACTIVATED_TS, end.getTimeInMillis());
                else
                    faulty++;
            }
            if(values.size() > 0) {
                list.queueUndo(new SwipeDismissList.Undoable() {
                    @Override
                    public void undo() {
                        ContentValues undo_values = new ContentValues();
                        if(start != null) {
                            undo_values.put(LabelContract.Active.ACTIVATED_TS, l.activatedTS);
                        }
                        if(end != null) {
                            undo_values.putNull(LabelContract.Active.DEACTIVATED_TS);
                        }
                        getActivity().getContentResolver().update(update_uri, undo_values, null, null);
                    }

                    @Override
                    public String getTitle() {
                        if(end != null)
                            return getString(R.string.deactivate_label, l.labelText);
                        else
                            return getString(R.string.update_label, l.labelText);
                    }
                });
                getActivity().getContentResolver().update(update_uri, values, null, null);
            }
        }
        if(faulty > 0) {
            //Toast.makeText(getActivity(), getString(R.string.not_ended, faulty), Toast.LENGTH_LONG).show();
            Crouton.makeText(getActivity(), getString(R.string.not_ended, faulty), Style.ALERT).show();
        }
        mode.finish();
        list.showUndoPopup();

    }

    public static class ListFragment extends ActionModeListFragment implements LoaderManager.LoaderCallbacks<Cursor>, SwipeDismissList.OnDismissCallback {

        private static final String QUERY_SELECTION = LabelContract.Active.DEACTIVATED_TS + " is null";
        SimpleCursorAdapter adapter;

        private SwipeDismissList swipeDismissList;

        private LabelHelper labelHelper;

        ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
            @Override
            public SimpleDateFormat get() {
                return new SimpleDateFormat(DATE_PATTERN);
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getLoaderManager().initLoader(MainActivity.ACTIVE_LOADER_ID, null, this);

            labelHelper = new LabelHelper(getActivity().getContentResolver());

            adapter = new SimpleCursorAdapter(getActivity(), R.layout.list_item_active, null,
                    LIST_FROM, LIST_TO, 0);

            adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor cursor, int i) {
                    TextView textView;
                    switch(i) {
                        case 5:
                            textView = (TextView)view;
                            String dateTimeString = dateFormat.get().format(new Date(cursor.getLong(i)));
                            textView.setText(getString(R.string.output_activedts, dateTimeString));
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setListAdapter(adapter);

            swipeDismissList = new SwipeDismissList(getListView(), this, SwipeDismissList.UndoMode.COLLAPSED_UNDO);
            swipeDismissList.setSwipeDisabled(true); //want only the undo
            swipeDismissList.setUndoMultipleString(getString(R.string.deactivate_n));
            swipeDismissList.setRequireTouchBeforeDismiss(false);
        }

        public void dismiss() {
            swipeDismissList.discardUndo();
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, final long id) {
            super.onListItemClick(l, v, position, id);
            int choiceMode = l.getChoiceMode();
            if (choiceMode == ListView.CHOICE_MODE_MULTIPLE) return;

            ActiveLabelDTO label = getLabelAtPosition(position);

            deactivateLabel(label);

            swipeDismissList.showUndoPopup();
        }

        public void deactivateLabel(final ActiveLabelDTO label) {

            labelHelper.deactivateLabel(label.id);
            updateWidget();

            queueDeactivateUndo(label);

        }

        public void deactivateLabel(final ActiveLabelDTO label, Date deactivatedTS) {
            labelHelper.deactivateLabel(label.id, deactivatedTS);
            updateWidget();
            queueDeactivateUndo(label);
        }

        void queueUndo(SwipeDismissList.Undoable undoable) {
            swipeDismissList.queueUndoAction(undoable);
        }

        private void queueDeactivateUndo(final ActiveLabelDTO label) {
            swipeDismissList.queueUndoAction(new SwipeDismissList.Undoable() {
                @Override
                public void undo() {
                    ContentResolver resolver = getActivity().getContentResolver();
                    Uri delete_uri = Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, Long.toString(label.id));
                    try {
                        resolver.delete(delete_uri, null, null);
                        labelHelper.activateLabel(label.labelId, label.activatedTS);
                        updateWidget();
                    } catch (SQLiteConstraintException e) {
                        Log.e(TAG, "Could not activate label " + Long.toString(label.labelId), e);
                    } catch (SQLiteException e) {
                        Log.d(TAG, "could not delete " + delete_uri, e);
                    }

                }

                @Override
                public String getTitle() {
                    return getString(R.string.deactivate_label, label.labelText);
                }
            });
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        private void updateWidget(){
            if(Build.VERSION.SDK_INT  >= Build.VERSION_CODES.HONEYCOMB) {
                AppWidgetManager mgr = AppWidgetManager.getInstance(getActivity().getApplicationContext());
                int[] widgetIds = mgr.getAppWidgetIds(new ComponentName(getActivity().getApplicationContext(), LabelsWidgetProvider.class));
                mgr.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_listview);
            }
        }

        //Loader
        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(getActivity(), LabelContract.Active.CONTENT_URI, QUERY_PROJECTION, QUERY_SELECTION, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            adapter.swapCursor(cursor);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
            adapter.swapCursor(null);
        }
        //Loader end

        //Undo

        @Override
        public SwipeDismissList.Undoable onDismiss(AbsListView listView, int position) {
            return null;
        }

        synchronized List<ActiveLabelDTO> getSelected() {
            ListView list = getListView();
            SparseBooleanArray checked = list.getCheckedItemPositions();
            List<ActiveLabelDTO> selectedList = new ArrayList<ActiveLabelDTO>();
            for (int i = 0, lim = checked.size(); i < lim; ++i) {
                if (checked.valueAt(i)) {
                    int position = checked.keyAt(i);
                    ActiveLabelDTO label = getLabelAtPosition(position);
                    selectedList.add(label);
                }
            }
            return selectedList;
        }

        public void deleteSelected() {
            List<ActiveLabelDTO> selected = getSelected();
            if (selected.size() > 0) {
                for (ActiveLabelDTO l : selected)
                    deleteLabel(l);
                swipeDismissList.showUndoPopup();
            }
        }
        private void deleteLabel(final ActiveLabelDTO label) {
            ContentResolver resolver = getActivity().getContentResolver();
            Uri delete_uri = Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, Long.toString(label.id));
            resolver.delete(delete_uri, null, null);
            updateWidget();

            swipeDismissList.queueUndoAction(new SwipeDismissList.Undoable() {
                @Override
                public void undo() {
                    labelHelper.activateLabel(label.labelId, label.activatedTS);
                }

                @Override
                public String getTitle() {
                    return getString(R.string.discard_label, label.labelText);
                }
            });
        }

//        public void endSelected(Date deactivateTS) {
//            int notEndedCount = 0;
//            List<ActiveLabelDTO> selected = getSelected();
//            if (selected.size() > 0) {
//                for(ActiveLabelDTO l : selected) {
//                    if(l.activatedTS <= deactivateTS.getTime())
//                        deactivateLabel(l, deactivateTS);
//                    else
//                        notEndedCount++;
//                }
//                if(notEndedCount < selected.size())
//                    swipeDismissList.showUndoPopup();
//                if(notEndedCount > 0)
//                    Toast.makeText(getActivity(), getString(R.string.not_ended, Integer.toString(notEndedCount)), Toast.LENGTH_LONG).show();
//            }
//        }

        ActiveLabelDTO getLabelAtPosition(int position) {

            Cursor c = (Cursor) adapter.getItem(position);
            return LabelHelper.convertCursorToActiveLabel(c);
        }

        void showUndoPopup() {
            swipeDismissList.showUndoPopup();
        }
    }
}
