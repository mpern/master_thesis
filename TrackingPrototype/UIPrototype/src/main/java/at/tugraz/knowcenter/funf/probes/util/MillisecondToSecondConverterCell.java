package at.tugraz.knowcenter.funf.probes.util;

import android.database.Cursor;

import edu.mit.media.funf.probe.builtin.ContentProviderProbe;
import edu.mit.media.funf.time.DecimalTimeUnit;

/**
* Created by Markus Perndorfer on 27.01.14.
*/
public class MillisecondToSecondConverterCell extends ContentProviderProbe.CursorCell.LongCell {
    @Override
    public Long getData(Cursor cursor, int columnIndex) {
        Long timeStampInMs = super.getData(cursor, columnIndex);
        if(timeStampInMs != null ){
            return DecimalTimeUnit.MILLISECONDS.toSeconds(timeStampInMs).longValue();
        } else {
            throw new ArithmeticException();
        }
    }
}
