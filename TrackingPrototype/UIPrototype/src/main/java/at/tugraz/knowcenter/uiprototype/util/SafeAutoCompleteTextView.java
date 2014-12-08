package at.tugraz.knowcenter.uiprototype.util;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.AutoCompleteTextView;

/**
 * Created by Markus Perndorfer on 11.10.13.
 * http://stackoverflow.com/questions/11999098/illegalstateexception-support-loadermanager-with-autocompletetextview
 * https://gist.github.com/esilverberg/5606551
 */

public class SafeAutoCompleteTextView extends AutoCompleteTextView {

    public SafeAutoCompleteTextView(Context context) {
        super(context);
    }

    public SafeAutoCompleteTextView(Context c, AttributeSet s) {
        super(c, s);
    }

    public SafeAutoCompleteTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onFilterComplete (int count) {
        try {
            super.onFilterComplete(count);
        } catch (IllegalStateException e) {
        } catch (android.database.StaleDataException e) {
        }

    }

    @Override
    public void showDropDown() {
        try {
            super.showDropDown();
        } catch (IllegalStateException e) {
        } catch (android.database.StaleDataException e) {
        }
    }

}