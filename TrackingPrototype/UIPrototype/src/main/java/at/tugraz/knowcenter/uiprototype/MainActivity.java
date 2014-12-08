package at.tugraz.knowcenter.uiprototype;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.FilterQueryProvider;
import android.widget.TextView;
import android.widget.Toast;

import at.tugraz.knowcenter.label.contentproviders.LabelContract;
import at.tugraz.knowcenter.label.model.LabelHelper;
import at.tugraz.knowcenter.label.model.UnusedLabelDTO;
import at.tugraz.knowcenter.uiprototype.fragments.ActiveLabelsFragment;
import at.tugraz.knowcenter.uiprototype.fragments.UnusedLabelsFragment;
import de.keyboardsurfer.android.widget.crouton.Style;
import edu.mit.media.funf.Launcher;

import de.keyboardsurfer.android.widget.crouton.Crouton;

public class MainActivity extends ActionBarActivity implements ActionBar.TabListener {

    public static final int ACTIVE_LOADER_ID = 0;
    public static final int UNUSED_LOADER_ID = 1;

    LabelListsPagerAdapter tabAdapter;

    ViewPager pager;

    SimpleCursorAdapter addAdapter;

    AutoCompleteTextView completeTextView;

    LabelHelper labelHelper;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final ActionBar actionBar = getSupportActionBar();
//        actionBar.setDisplayHomeAsUpEnabled(false);
//        actionBar.setDisplayShowHomeEnabled(false);

        labelHelper = new LabelHelper(getContentResolver());

        tabAdapter = new LabelListsPagerAdapter(getSupportFragmentManager());
        actionBar.setHomeButtonEnabled(false);

        pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(tabAdapter);
        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
            }

            @Override
            public void onPageSelected(int i) {
                dismissFragment(LabelListsPagerAdapter.ACTIVE_FRAGMENT);
                dismissFragment(LabelListsPagerAdapter.UNUSED_FRAGMENT);
            }
            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        for (int i = 0; i < LabelListsPagerAdapter.NUM_FRAGMENTS; i++) {
            actionBar.addTab(actionBar.newTab().setText(tabAdapter.getPageTitle(i)).setTabListener(this));
        }

        addAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_dropdown_item_1line,
                getContentResolver().query(LabelContract.UnusedLabels.CONTENT_URI, UnusedLabelsFragment.QUERY_PROJECTION, null, null, null),
                new String[]{LabelContract.UnusedLabels.TEXT},
                new int[]{android.R.id.text1}, 0);

        addAdapter.setFilterQueryProvider( new FilterQueryProvider() {
            @Override
            public Cursor runQuery(CharSequence constraint) {
                if (constraint == null || "".equals(constraint)){
                    return getContentResolver().query(LabelContract.UnusedLabels.CONTENT_URI, UnusedLabelsFragment.QUERY_PROJECTION, null, null, null);
                } else {
                    return getContentResolver().query(LabelContract.UnusedLabels.CONTENT_URI,
                            UnusedLabelsFragment.QUERY_PROJECTION,
                            LabelContract.UnusedLabels.TEXT + " like ?",
                            new String[]{"%" + constraint + "%"},
                            null);
                }
            }
        });

        addAdapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter() {

            @Override
            public CharSequence convertToString(Cursor c) {
                return c.getString(c.getColumnIndexOrThrow(LabelContract.UnusedLabels.TEXT));
            }
        });

        if (!Launcher.isLaunched())
        {
            Launcher.launch(this);
        }
    }

    private void dismissFragment(int fragment_id) {
        String fragment_name;
        Fragment fragment;

        fragment_name = makeFragmentName(R.id.pager, fragment_id);
        fragment = getSupportFragmentManager().findFragmentByTag(fragment_name);

        ((Dismissable)fragment).dismiss();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.label_action_menu, menu);

        MenuItem item = menu.findItem(R.id.action_add_label);
        View actionView = MenuItemCompat.getActionView(item);

        completeTextView = (AutoCompleteTextView) actionView.findViewById(R.id.label_input);

        completeTextView.setAdapter(addAdapter);
        completeTextView.setThreshold(1);

        completeTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor c = (Cursor) addAdapter.getItem(position);

                UnusedLabelDTO label = LabelHelper.convertCursorToUnusedLabel(c);

                activateLabel(label);

                completeTextView.setText("");
            }
        });

        completeTextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if(actionId == EditorInfo.IME_ACTION_GO) {
                    String label_text = v.getText().toString();

                    try {
                        Uri new_uri = labelHelper.createNewLabel(label_text);
                        //Toast.makeText(getApplicationContext(), "New label: " + label_text, Toast.LENGTH_SHORT).show();
                        Crouton.makeText(MainActivity.this, "New label: " + label_text, Style.INFO).show();
                        completeTextView.setText("");
                        if (pager.getCurrentItem() == LabelListsPagerAdapter.ACTIVE_FRAGMENT) {
                            activateLabel(new UnusedLabelDTO(Long.valueOf(new_uri.getLastPathSegment()), label_text, 0, 0));
                            updateWidget();
                        }
                    } catch (SQLiteConstraintException e) {
                        //Toast.makeText(getApplicationContext(), "Could not create label " + label_text, Toast.LENGTH_SHORT).show();
                        Crouton.makeText(MainActivity.this, "Could not create label " + label_text, Style.ALERT).show();
                    }
                }
                return handled;
            }
        });


        MenuItem settings_btn = menu.findItem(R.id.action_settings);
        settings_btn.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
        });

//        checkbox = menu.findItem(R.id.pipeline_enabled);
//        checkbox.setEnabled(false);

        return super.onCreateOptionsMenu(menu);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void updateWidget(){
        if(Build.VERSION.SDK_INT  >= Build.VERSION_CODES.HONEYCOMB) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(getApplicationContext());
            int[] widgetIds = mgr.getAppWidgetIds(new ComponentName(getApplicationContext(), LabelsWidgetProvider.class));
            mgr.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_listview);
        }
    }

    private void activateLabel(UnusedLabelDTO label) {
        String fragment_name = makeFragmentName(R.id.pager, LabelListsPagerAdapter.UNUSED_FRAGMENT);
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(fragment_name);

        ((UnusedLabelsFragment)fragment).activateLabel(label);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        pager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    //http://stackoverflow.com/questions/8785221/retrieve-a-fragment-from-a-viewpager
    private static String makeFragmentName(int viewId, int position)
    {
        return "android:switcher:" + viewId + ":" + position;
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(addAdapter != null && addAdapter.getCursor() != null && !addAdapter.getCursor().isClosed())
            addAdapter.getCursor().close();

        Crouton.cancelAllCroutons();
    }

    private static class LabelListsPagerAdapter extends FragmentPagerAdapter {
        public static final int NUM_FRAGMENTS = 2;
        public static final int ACTIVE_FRAGMENT = 0;
        public static final int UNUSED_FRAGMENT = 1;

        private LabelListsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int index) {
//            Log.d(TAG + "pager", "called getItem " + index);
            switch (index) {
                case ACTIVE_FRAGMENT:
                    new ActiveLabelsFragment();
                    return new ActiveLabelsFragment();
                case UNUSED_FRAGMENT:
                    return new UnusedLabelsFragment();
                default:
                    throw new IllegalArgumentException();
            }
        }

        @Override
        public int getCount() {
            return NUM_FRAGMENTS;
        }

        @Override
        public CharSequence getPageTitle(int position) {

            switch (position) {
                case ACTIVE_FRAGMENT:
                    return "Active";
                case UNUSED_FRAGMENT:
                    return "Unused";
                default:
                    throw new IllegalArgumentException();
            }
        }
    }



}
