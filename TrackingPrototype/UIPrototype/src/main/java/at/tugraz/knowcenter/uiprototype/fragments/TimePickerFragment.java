package at.tugraz.knowcenter.uiprototype.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

import com.ikovac.timepickerwithseconds.view.MyTimePickerDialog;

import java.util.Calendar;

import at.tugraz.knowcenter.uiprototype.R;

/**
* Created by Markus Perndorfer on 20.01.14.
*/
public class TimePickerFragment extends DialogFragment {

    MyTimePickerDialog.OnTimeSetListener listener;

    private int hour_of_day = -1;
    private int minute = -1;
    private int second = -1;

    public boolean mIgnoreTimeSet = true;
    private MyTimePickerDialog timeDlg;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getCallback();
    }

    private void getCallback() {
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        if (listener == null) {
            Fragment parent = getParentFragment();
            try {
                listener = (MyTimePickerDialog.OnTimeSetListener) parent;
            } catch (ClassCastException e) {
                throw new ClassCastException(parent.toString()
                        + " must implement TimePickerDialog.OnTimeSetListener");
            }
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        if(hour_of_day < 0 || minute < 0 || second < 0) {
            final Calendar c = Calendar.getInstance();
            hour_of_day = c.get(Calendar.HOUR_OF_DAY);
            minute = c.get(Calendar.MINUTE);
            second = c.get(Calendar.SECOND);
        }

        // Create a new instance of TimePickerDialog and return it
        timeDlg = new MyTimePickerDialog(getActivity(), listener, hour_of_day, minute, second,
                android.text.format.DateFormat.is24HourFormat(getActivity()));

        timeDlg.setTitle(R.string.time_title);

        return timeDlg;
    }

    public void updateTime(int hour_of_day, int minute, int second) {
        this.hour_of_day = hour_of_day;
        this.minute = minute;
        this.second = second;
    }
}
