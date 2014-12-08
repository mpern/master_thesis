package at.tugraz.knowcenter.uiprototype.fragments;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.database.Cursor;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.akalipetis.action_mode_list_fragment.ActionModeListFragment;
import com.akalipetis.action_mode_list_fragment.MultiChoiceModeListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import at.tugraz.knowcenter.label.contentproviders.LabelContract;
import at.tugraz.knowcenter.label.model.UnusedLabelDTO;
import at.tugraz.knowcenter.uiprototype.Dismissable;
import at.tugraz.knowcenter.label.model.LabelHelper;
import at.tugraz.knowcenter.uiprototype.LabelsWidgetProvider;
import at.tugraz.knowcenter.uiprototype.MainActivity;
import at.tugraz.knowcenter.uiprototype.R;

import de.timroes.swipetodismiss.SwipeDismissList;

import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.DATE_PATTERN;
import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.TAG;

/**
 * Created by Markus Perndorfer on 05.09.13.
 */
public class UnusedLabelsFragment extends Fragment implements MultiChoiceModeListener, Dismissable {
    public static final String[] LIST_FROM = new String[]{LabelContract.UnusedLabels.TEXT,
                                                          LabelContract.UnusedLabels.USAGE_COUNT,
                                                          LabelContract.UnusedLabels.LAST_USED};
    public static final int[] LIST_TO = new int[]{R.id.list_item_unused_text,
                                                  R.id.list_item_unused_count,
                                                  R.id.list_item_unused_date};

    public static final String[] QUERY_PROJECTION = {LabelContract.UnusedLabels._ID,
                                                     LabelContract.UnusedLabels.TEXT,
                                                     LabelContract.UnusedLabels.USAGE_COUNT,
                                                     LabelContract.UnusedLabels.LAST_USED};

    private ActionMode mode = null;

    private ListFragment list;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_unused_labels, container, false);



        RadioButton b = (RadioButton) rootView.findViewById(R.id.sort_mru);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRadioButtonClicked(v);
            }
        });
        b = (RadioButton) rootView.findViewById(R.id.sort_count);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRadioButtonClicked(v);
            }
        });
        b = (RadioButton) rootView.findViewById(R.id.sort_text);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRadioButtonClicked(v);
            }
        });

        list = (ListFragment) getChildFragmentManager().findFragmentByTag(ListFragment.class.getCanonicalName());
        if(list == null) {
            list = new ListFragment();
        }
        list.setMultiChoiceModeListener(this);

        getChildFragmentManager().beginTransaction()
                                 .replace(R.id.unusedFragmentContainer, list, ListFragment.class.getCanonicalName())
                                 .commit();
        getChildFragmentManager().executePendingTransactions();
        return rootView;
    }

    public void onRadioButtonClicked(View view) {
        String sort = "";
        switch (view.getId()) {
            case R.id.sort_mru:
                sort = LabelContract.UnusedLabels.LAST_USED + " desc";
                break;
            case R.id.sort_count:
                sort = LabelContract.UnusedLabels.USAGE_COUNT + " desc";
                break;
            case R.id.sort_text:
                sort = LabelContract.UnusedLabels.TEXT + " ASC";
                break;
        }
        list.setSortOrder(sort);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    //ActionMode
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
        menu.clear();
        getActivity().getMenuInflater().inflate(R.menu.actionmode_unused, menu);

        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem item) {
        switch(item.getItemId()) {
            case R.id.unused_select_all:
                selectAll();
                return true;
            case R.id.unused_activate:
                activateSelected();
                return true;
            default:
                return false;
        }
    }

    private void activateSelected() {
        list.activateSelected();
        mode.finish();
    }

    private void selectAll() {
        ListView listView = list.getListView();

        for (int i = 0, lim = listView.getAdapter().getCount(); i < lim; ++i)
            listView.setItemChecked(i, true);
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mode = null;
    }

    @Override
    public void dismiss() {
        if (mode != null)
            mode.finish();
        list.dismiss();
    }

    //ActionMode end
    public void activateLabel(UnusedLabelDTO label) {
        list.activateLabel(label);
    }

    public static class ListFragment extends ActionModeListFragment implements  LoaderManager.LoaderCallbacks<Cursor>, SwipeDismissList.OnDismissCallback  {

        SimpleCursorAdapter adapter;

        private SwipeDismissList swipeDismissList;

        private LabelHelper labelHelper;


        String sortOrder = LabelContract.UnusedLabels.TEXT + " ASC";

        ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
            @Override
            public SimpleDateFormat get() {
                return new SimpleDateFormat(DATE_PATTERN);
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getLoaderManager().initLoader(MainActivity.UNUSED_LOADER_ID, null, this);

            labelHelper = new LabelHelper(getActivity().getContentResolver());

            adapter = new SimpleCursorAdapter(getActivity(), R.layout.list_item_unused, null,
                    LIST_FROM, LIST_TO, 0);
            adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor cursor, int idx) {
                    TextView textView;
                    switch(idx) {
                        case 2:
                            textView = (TextView) view;
                            textView.setText(getString(R.string.output_count, cursor.getInt(idx)));
                            return true;
                        case 3:
                            textView = (TextView) view;
                            String dateTimeString = dateFormat.get().format(new Date(cursor.getLong(idx)));
                            textView.setText(getString(R.string.ouput_lastused, dateTimeString));
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

            swipeDismissList = new SwipeDismissList(getListView(),this, SwipeDismissList.UndoMode.COLLAPSED_UNDO);
            swipeDismissList.setSwipeDisabled(true); //want only the undo
            swipeDismissList.setUndoMultipleString(getString(R.string.activate_n));
            swipeDismissList.setRequireTouchBeforeDismiss(false);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            int choiceMode = l.getChoiceMode();
            if (choiceMode == ListView.CHOICE_MODE_MULTIPLE) return;

            Cursor c = (Cursor) adapter.getItem(position);

            UnusedLabelDTO label = LabelHelper.convertCursorToUnusedLabel(c);

            activateLabel(label);
        }

        public void activateSelected() {
            List<UnusedLabelDTO> activateList = getSelectedLabels();
            if(activateList.size() > 0) {
                for(UnusedLabelDTO unused : activateList) {
                    activateLabel(unused);
                }
                swipeDismissList.showUndoPopup();
            }
        }

        private synchronized List<UnusedLabelDTO> getSelectedLabels() {
            ListView list = getListView();
            SparseBooleanArray checked = list.getCheckedItemPositions();
            List<UnusedLabelDTO> activateList = new ArrayList<>();
            for (int i = 0, lim = checked.size(); i < lim; ++i) {
                if (checked.valueAt(i)) {
                    int position = checked.keyAt(i);
                    UnusedLabelDTO label = getLabelAtPosition(position);
                    activateList.add(label);
                }
            }
            return activateList;
        }

        private UnusedLabelDTO getLabelAtPosition(int position) {
            Cursor c = (Cursor) adapter.getItem(position);
            return LabelHelper.convertCursorToUnusedLabel(c);
        }

        public void activateLabel(final UnusedLabelDTO label){
            final Uri active = labelHelper.activateLabel(label.id);
            updateWidget();

            swipeDismissList.queueUndoAction(new SwipeDismissList.Undoable() {
                @Override
                public void undo() {
                    ContentResolver resolver = getActivity().getContentResolver();
                    try {
                        resolver.delete(active, null, null);
                        updateWidget();
                    } catch (SQLiteException e) {
                        Log.d(TAG, "could not delete " + active, e);
                    }
                }

                @Override
                public String getTitle() {
                    return getString(R.string.activate_label, label.labelText);
                }
            });
            swipeDismissList.showUndoPopup();
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
            return new CursorLoader(getActivity(), LabelContract.UnusedLabels.CONTENT_URI, QUERY_PROJECTION, null, null, sortOrder);
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

        public void dismiss() {
            swipeDismissList.discardUndo();
        }

        public void setSortOrder(String order) {
            sortOrder = order;
            getLoaderManager().restartLoader(MainActivity.UNUSED_LOADER_ID, null, this);
        }
    }
}

