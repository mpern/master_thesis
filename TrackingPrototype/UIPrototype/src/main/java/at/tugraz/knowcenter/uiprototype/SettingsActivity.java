package at.tugraz.knowcenter.uiprototype;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;

import at.tugraz.knowcenter.funf.probes.LabelProbe;
import at.tugraz.knowcenter.uiprototype.tasks.ExportLabelsTask;
import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.pipeline.BasicPipeline;
import edu.mit.media.funf.pipeline.Pipeline;

import android.support.v7.widget.Toolbar;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;
    static final String PIPELINE_ENABLED = "pipeline_enabled";
    private static final String ARCHIVE_NOW = "archive_now";
    private static final String EXPORT_LABES = "export_labels";

    boolean isBound = false;

    public static final String PIPELINE_NAME = "default";
    private FunfManager funfManager;
    private BasicPipeline pipeline;

    private Toolbar actionBar;


    private ServiceConnection funfManagerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isBound = true;
            funfManager = ((FunfManager.LocalBinder)service).getManager();

            pipeline = (BasicPipeline) funfManager.getRegisteredPipeline(PIPELINE_NAME);

            boolean isPipelineActive = pipeline.isEnabled();

            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
            SharedPreferences.Editor edit = defaultPrefs.edit();
            edit.putBoolean(SettingsActivity.PIPELINE_ENABLED, isPipelineActive);
            edit.commit();

            defaultPrefs.registerOnSharedPreferenceChangeListener(SettingsActivity.this);

            findPreference(PIPELINE_ENABLED).setEnabled(isBound);
            getListView().setEnabled(isBound);
            getListView().requestLayout();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            funfManager = null;
        }



    };
    private Handler handler;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(SettingsActivity.PIPELINE_ENABLED.equals(key)) {
            boolean shouldBeEnabled = sharedPreferences.getBoolean(key, false);
            if(pipeline != null && pipeline.isEnabled() != shouldBeEnabled) {
                if(shouldBeEnabled) {
                    funfManager.enablePipeline(PIPELINE_NAME);
                    pipeline = (BasicPipeline) funfManager.getRegisteredPipeline(PIPELINE_NAME);
                } else {
                    funfManager.disablePipeline(PIPELINE_NAME);
                }
            }
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupSimplePreferencesScreen();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();
        actionBar.setTitle(getTitle());
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Bind to the service, to create the connection with FunfManager
        bindService(new Intent(this, FunfManager.class), funfManagerConn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(isBound) {
            unbindService(funfManagerConn);
            isBound = false;
        }
    }

    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_general);
        findPreference(PIPELINE_ENABLED).setEnabled(isBound);
        getListView().setEnabled(isBound);
        getListView().requestLayout();
        setupOnClick();

    }

    private void setupOnClick() {
        Preference archive = findPreference(ARCHIVE_NOW);
        archive.setOnPreferenceClickListener(preferenceClickListener);
        Preference label = findPreference(EXPORT_LABES);
        label.setOnPreferenceClickListener(preferenceClickListener);
    }

    @Override
    public void setContentView(int layoutResID) {
        ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.settings_activity, new LinearLayout(this), false);

        actionBar = (Toolbar) contentView.findViewById(R.id.action_bar);
        actionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ViewGroup contentWrapper = (ViewGroup) contentView.findViewById(R.id.content_wrapper);
        LayoutInflater.from(this).inflate(layoutResID, contentWrapper, true);

        getWindow().setContentView(contentView);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    private Preference.OnPreferenceClickListener preferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (ARCHIVE_NOW.equals(preference.getKey())) {
                if (funfManager != null) {
                    final Pipeline pipeline = funfManager.getRegisteredPipeline(PIPELINE_NAME);
                    if (pipeline != null && pipeline.isEnabled()) {
                        Gson gson = funfManager.getGson();
                        //Force collection of label data
                        final LabelProbe probe = gson.fromJson(new JsonObject(), LabelProbe.class);
                        probe.registerListener((BasicPipeline)pipeline);

                        // delay archive to allow probe to finish (dirty hack)
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                pipeline.onRun(BasicPipeline.ACTION_ARCHIVE, null);
                                Toast.makeText(getBaseContext(), getString(R.string.archived), Toast.LENGTH_SHORT).show();
                            }
                        }, 3000L);

                    } else {
                        Toast.makeText(getBaseContext(), getString(R.string.archived_not_enabled, PIPELINE_NAME), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getBaseContext(), getString(R.string.archived_not_enabled, PIPELINE_NAME), Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (EXPORT_LABES.equals(preference.getKey())) {
                new ExportLabelsTask(getContentResolver(), getApplicationContext()).execute();
                return true;
            }
            return false;
        }
    };

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

//    /**
//     * This fragment shows general preferences only. It is used when the
//     * activity is showing a two-pane settings UI.
//     */
//    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
//    @SuppressLint("Instantiatable")
//    public class GeneralPreferenceFragment extends PreferenceFragment {
//
//        public GeneralPreferenceFragment() {        }
//
//        @Override
//        public void onCreate(Bundle savedInstanceState) {
//            super.onCreate(savedInstanceState);
//            addPreferencesFromResource(R.xml.pref_general);
//
//            setupOnClick();
//
//        }
//    }

}

