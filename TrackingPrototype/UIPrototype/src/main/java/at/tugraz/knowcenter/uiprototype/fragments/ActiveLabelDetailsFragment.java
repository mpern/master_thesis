package at.tugraz.knowcenter.uiprototype.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.ikovac.timepickerwithseconds.view.MyTimePickerDialog;
import com.ikovac.timepickerwithseconds.view.TimePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import at.tugraz.knowcenter.uiprototype.R;
import at.tugraz.knowcenter.uiprototype.util.FragmentUtils;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

import static at.tugraz.knowcenter.uiprototype.util.CommonConstants.DATE_PATTERN;

/**
 * Created by Markus Perndorfer on 21.01.14.
 */
public class ActiveLabelDetailsFragment extends DialogFragment implements MyTimePickerDialog.OnTimeSetListener{

    private TextView startTimeView;
    private TextView endTimeView;

    private static final String PICKER = "ActiveLabelDetailsFragment.TimePicker";
    private View dialogView;

    public interface OnTimeChangedListener {
        void onLabelTimeChanged(Calendar start, Calendar end);
    }

    ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        public SimpleDateFormat get() {
            return new SimpleDateFormat(DATE_PATTERN);
        }
    };

    private Calendar startTime;
    private Calendar endTime;

    private Calendar newStartTime;
    private Calendar newEndTime;

    private OnTimeChangedListener listener;

    private View.OnClickListener editButtonClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Calendar time = null;
            switch (v.getId()) {
                case R.id.editStart:
                    time = getCurrentStartTime();
                    editing = WHAT.START;
                    break;
                case R.id.editEnd:
                    time = getCurrentEndTime();
                    editing = WHAT.END;
                    break;
            }
            if (time == null)
                time = Calendar.getInstance();

            TimePickerFragment fragment = (TimePickerFragment) getChildFragmentManager().findFragmentByTag(PICKER);
            if(fragment == null) {
                fragment = new TimePickerFragment();
            }
            fragment.updateTime(time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), time.get(Calendar.SECOND));
            fragment.show(getChildFragmentManager(), PICKER);
        }
    };

    private static enum WHAT {START, END, NONE};

    private WHAT editing;

    public ActiveLabelDetailsFragment() {
        super();
        startTime = null;
        endTime = null;
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute, int seconds) {
        switch(editing) {
            case START:
                Calendar tempNewStart = Calendar.getInstance();
                tempNewStart.set(Calendar.HOUR_OF_DAY, hourOfDay);
                tempNewStart.set(Calendar.MINUTE, minute);
                tempNewStart.set(Calendar.SECOND, seconds);
                if (getCurrentEndTime() == null || tempNewStart.compareTo(getCurrentEndTime()) <= 0) {
                    newStartTime = tempNewStart;
                    updateStartTimeLabel();
                } else {
                    //Toast.makeText(getActivity(), getString(R.string.startBeforeEnd), Toast.LENGTH_SHORT).show();
                    Crouton.makeText(getActivity(), getString(R.string.startBeforeEnd), Style.ALERT, (ViewGroup)dialogView).show();
                }

                break;
            case END:
                Calendar tempNewEnd = Calendar.getInstance();
                //if(newEndTime == null)
                //    newEndTime = Calendar.getInstance();
                tempNewEnd.set(Calendar.HOUR_OF_DAY, hourOfDay);
                tempNewEnd.set(Calendar.MINUTE, minute);
                tempNewEnd.set(Calendar.SECOND, seconds);
                if(getCurrentStartTime() == null || tempNewEnd.compareTo(getCurrentStartTime()) > 0) {
                    newEndTime = tempNewEnd;
                    updateEndTimeLabel();
                } else {
                    //Toast.makeText(getActivity(), getString(R.string.endBeforeStart), Toast.LENGTH_SHORT).show();
                    Crouton.makeText(getActivity(), getString(R.string.endBeforeStart), Style.ALERT, (ViewGroup)dialogView).show();
                }
                break;
            case NONE:
                Log.d(null, "What the hell?!");
                break;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        listener = FragmentUtils.getParent(this, OnTimeChangedListener.class);
        if(listener == null) {
            throw new IllegalArgumentException("Parent activity or fragment must implement " + OnTimeChangedListener.class.getSimpleName());
        }
    }



    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        editing = WHAT.NONE;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        builder.setTitle(R.string.labeldetails_title);

        dialogView = inflater.inflate(R.layout.labeldetail, null);
        startTimeView = (TextView) dialogView.findViewById(R.id.startTimeView);
        updateStartTimeLabel();
        endTimeView = (TextView) dialogView.findViewById(R.id.endTimeView);
        updateEndTimeLabel();

        ImageButton editEnd = (ImageButton) dialogView.findViewById(R.id.editEnd);
        editEnd.setOnClickListener(editButtonClick);
        ImageButton editStart = (ImageButton) dialogView.findViewById(R.id.editStart);
        editStart.setOnClickListener(editButtonClick);

        builder.setView(dialogView);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Calendar start = null;
                Calendar end = null;
                if((newEndTime != null && endTime == null) || (newEndTime != null && newEndTime.compareTo(endTime) != 0))
                    end = getCurrentEndTime();
                if((newStartTime != null && startTime == null) || (newStartTime != null && newStartTime.compareTo(startTime) != 0))
                    start = getCurrentStartTime();
                if(start != null || end != null)
                    listener.onLabelTimeChanged(start, end);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        return builder.create();
    }

    private void updateEndTimeLabel() {
        Date dateToShow = getCurrentEndTime() != null ? getCurrentEndTime().getTime() : null;
        String text = "";
        if(dateToShow != null)
            text = dateFormat.get().format(dateToShow);
        endTimeView.setText(getString(R.string.endTimeText, text));
    }
    private Calendar getCurrentEndTime(){
        return newEndTime != null ? newEndTime : endTime;
    }

    private void updateStartTimeLabel() {
        Date dateToShow = getCurrentStartTime() != null ? getCurrentStartTime().getTime() : null;
        String text = "<multiple>";
        if(dateToShow != null)
            text = dateFormat.get().format(dateToShow);
        startTimeView.setText(getString(R.string.startTimeText, text));
    }

    private Calendar getCurrentStartTime() {
        return newStartTime != null ? newStartTime : startTime;
    }

    public void setStartTime(Calendar startTime) {
        this.startTime = startTime;
        this.newStartTime = null;
    }

    public void setEndTime(Calendar endTime) {
        this.endTime = endTime;
        this.newEndTime = null;
    }
}
