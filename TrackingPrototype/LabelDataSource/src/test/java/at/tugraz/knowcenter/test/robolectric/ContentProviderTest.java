package at.tugraz.knowcenter.test.robolectric;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.tugraz.knowcenter.label.contentproviders.LabelContentProvider;
import at.tugraz.knowcenter.label.contentproviders.LabelContract;
import at.tugraz.knowcenter.label.model.LabelHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.robolectric.Robolectric.shadowOf;


@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ContentProviderTest {

    private static String[] ALL_PROJECTION = { LabelContract.AllLabels._ID,
        LabelContract.AllLabels.TEXT, LabelContract.AllLabels.LAST_USED,
        LabelContract.AllLabels.USAGE_COUNT };

    private static String[] UNUSED_PROJECTION = {LabelContract.UnusedLabels._ID,
        LabelContract.UnusedLabels.TEXT, LabelContract.UnusedLabels.LAST_USED, LabelContract.UnusedLabels.USAGE_COUNT
    };
    
    private static List<String> SOME_LABELS = Arrays.asList("first","second", "third");


//    private ContentResolver contentResolver;
    private ShadowContentResolver contentResolver;

    private ContentResolver realContentResolver;
    ContentProvider provider;

    @Before
    public void setUp() {
        realContentResolver = Robolectric.getShadowApplication().getContentResolver();
        provider = new LabelContentProvider();
        assertThat(provider.onCreate()).isTrue();
        ShadowContentResolver.registerProvider(LabelContract.AUTHORITY, provider);
        contentResolver = shadowOf(realContentResolver);
    }

    @After
    public void tearDown() {
        provider.shutdown();
    }
    
    @Test
    public void shouldBeEmpty() {
        Cursor result = contentResolver.query(LabelContract.AllLabels.CONTENT_URI,
                ALL_PROJECTION, null, null, null);

        assertThat(result).isNotNull();

        assertThat(result.getCount()).isEqualTo(0);
    }

    @Test
    public void shouldInsertLables() {
        String first_label = "somelabel";

        insertLabel(first_label);

        Cursor result = contentResolver.query(LabelContract.AllLabels.CONTENT_URI,
                ALL_PROJECTION, null, null, null);

        assertThat(result).isNotNull();

        assertThat(result.getCount()).isEqualTo(1);

        while (result.moveToNext()) {
            validateNewLabel(result, 1, first_label);
        }
        
        insertSomeLabels();
        
        result = contentResolver.query(LabelContract.AllLabels.CONTENT_URI,
                ALL_PROJECTION, null, null, null);

        assertThat(result).isNotNull();

        assertThat(result.getCount()).isEqualTo(4);
        
        
        assertThat(result.moveToNext()).isTrue();
        validateNewLabel(result, 1, first_label);
        
        for(int i = 0; i < SOME_LABELS.size(); i++) {
            assertThat(result.moveToNext()).isTrue();
            validateNewLabel(result, i + 2, SOME_LABELS.get(i));
        }
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(LabelContract.AllLabels.CONTENT_URI);
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(LabelContract.UnusedLabels.CONTENT_URI);
    }
    private void insertLabel(String label_name) {
        ContentValues newValues = new ContentValues();
        newValues.put(LabelContract.AllLabels.TEXT, label_name);
        contentResolver.insert(LabelContract.AllLabels.CONTENT_URI, newValues);
    }
    
    private void validateNewLabel(Cursor result, int expected_id, String expected_label_text) {

        Map<String, Object> expected = new HashMap<String, Object>();
        expected.put(LabelContract.AllLabels._ID, Integer.valueOf(expected_id));
        expected.put(LabelContract.AllLabels.TEXT, expected_label_text);
        expected.put(LabelContract.AllLabels.LAST_USED, null);
        expected.put(LabelContract.AllLabels.USAGE_COUNT, null);
        
        validateLabel(result, expected);
        
    }
    private void validateLabel(Cursor result, Map<String, Object> expectedValues) {

        for(Map.Entry<String, Object> expected : expectedValues.entrySet()) {
            Object expectedValue = expected.getValue();
            int index = result.getColumnIndex(expected.getKey());
            assertThat(index).isGreaterThanOrEqualTo(0);
            if(expectedValue == null)
                assertThat(result.isNull(index)).isTrue();
            else if(LabelContract.AllLabels.LAST_USED.equals(expected.getKey()))
                assertThat(new Date(result.getLong(index))).isAfterOrEqualsTo((Date) expectedValue);
            else if(LabelContract.Active.ACTIVATED_TS.equals(expected.getKey()))
                assertThat(new Date(result.getLong(index))).isAfterOrEqualsTo((Date)expectedValue);
            else if(expectedValue instanceof Integer)
                assertThat(result.getInt(index)).isEqualTo(expectedValue);
            else if(expectedValue instanceof String)
                assertThat(result.getString(index)).isEqualTo(expectedValue);
        }
    } 
    
    
    @Test(expected=SQLException.class)
    public void failInsertNullLabel(){
        ContentValues faulty = new ContentValues();
        faulty.put(LabelContract.AllLabels.TEXT, (String)null);
        
        contentResolver.insert(LabelContract.AllLabels.CONTENT_URI, faulty);
    }
    
    @Test
    public void testInsertOfActiveLabel() {
        
        int all_labels_id = 2;
        
        insertSomeLabels();
        Date before_insert = new Date();

        LabelHelper lh = new LabelHelper(realContentResolver);
        Uri new_active = lh.activateLabel(all_labels_id);
        
        assertThat(new_active).isEqualTo(Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, "1"));
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(LabelContract.Active.CONTENT_URI);
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(LabelContract.UnusedLabels.CONTENT_URI);
        Uri updated_all_label = Uri.withAppendedPath(LabelContract.AllLabels.CONTENT_URI, String.valueOf(all_labels_id));
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(updated_all_label);
        contentResolver.getNotifiedUris().clear();

        Cursor result = contentResolver.query(new_active, null, null, null, null);
        result.moveToNext();
        validateActiveLabel(result, 1, 2, SOME_LABELS.get(all_labels_id - 1), before_insert, null );
        
        int numDel = contentResolver.delete(new_active, null, null);
        
        assertThat(numDel).isEqualTo(1);

        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(new_active);
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(LabelContract.UnusedLabels.CONTENT_URI);
    } 
    @Test
    public  void testDeactivateLabel() {
        int all_labels_id = 1;
        insertSomeLabels();


        LabelHelper lh = new LabelHelper(realContentResolver);
        Uri new_active = lh.activateLabel(all_labels_id);
        System.out.println(new_active);
        String last_segment = new_active.getLastPathSegment();
        System.out.println(last_segment);
        Long active_id = Long.parseLong(last_segment);
        System.out.println(active_id);
        Date before_deactivate = new Date();

        lh.deactivateLabel(active_id);

        Cursor result = contentResolver.query(new_active, null, null, null, null);
        result.moveToNext();
        validateActiveLabel(result,1,all_labels_id,SOME_LABELS.get(all_labels_id - 1), null, before_deactivate);



    }
    private void insertSomeLabels() {

        for(String label : SOME_LABELS)
        {
            insertLabel(label);
        }
    }
    private void validateActiveLabel(Cursor c, int expected_id, int label_id, String text, Date activated, Date deactivated)
    {
        Map<String, Object> expected = new HashMap<String, Object>();
        expected.put(LabelContract.Active._ID, Integer.valueOf(expected_id));
        expected.put(LabelContract.Active.LABEL_ID, Integer.valueOf(label_id));

        expected.put(LabelContract.Active.TEXT, text);

        if(activated != null)
            expected.put(LabelContract.Active.ACTIVATED_TS, activated);
        if(deactivated != null)
            expected.put(LabelContract.Active.DEACTIVATED_TS, deactivated);
        
        validateLabel(c, expected);
    }

    private Uri insertActiveLabel(int all_label_id) {

        ContentValues currentLabel = new ContentValues();
        currentLabel.put(LabelContract.Active.LABEL_ID, all_label_id);
        Uri active_label = contentResolver.insert(LabelContract.Active.CONTENT_URI, currentLabel);

        return active_label;
    }


    @Test
    public void testUnused() {

        insertSomeLabels();

        validateUnusedLabelList(SOME_LABELS);

        LabelHelper lh = new LabelHelper(realContentResolver);

        Uri new_current_label = lh.activateLabel(2);
        
        Cursor result = contentResolver.query(LabelContract.UnusedLabels.CONTENT_URI,
                UNUSED_PROJECTION, null, null, null);

        assertThat(result).isNotNull();

        assertThat(result.getCount()).isEqualTo(SOME_LABELS.size() - 1);
        
        result.moveToNext();
        validateUnusedLabel(result, 1, SOME_LABELS.get(0));
        result.moveToNext();
        validateUnusedLabel(result, 3, SOME_LABELS.get(2));

        contentResolver.getNotifiedUris().clear();
        int numDel = lh.deactivateLabel(new_current_label);
        assertThat(numDel).isEqualTo(1);

        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(LabelContract.UnusedLabels.CONTENT_URI);
        assertThat(contentResolver.getNotifiedUris()).extracting("uri", Uri.class).contains(new_current_label);
        validateUnusedLabelList(SOME_LABELS);
    }

    private void validateUnusedLabelList(List<String> labels) {

        Cursor result = contentResolver.query(LabelContract.UnusedLabels.CONTENT_URI,
                UNUSED_PROJECTION, null, null, null);

        assertThat(result).isNotNull();

        assertThat(result.getCount()).isEqualTo(labels.size());

        int i = 1;
        while (result.moveToNext()) {
            validateUnusedLabel(result, i, labels.get(i-1));
            i++;
        }
    }


    private void validateUnusedLabel(Cursor result, int expectedId, String expectedText) {
        Map<String, Object> expectedValues = new HashMap<String, Object>();
        expectedValues.put(LabelContract.UnusedLabels._ID, Integer.valueOf(expectedId));
        expectedValues.put(LabelContract.UnusedLabels.TEXT, expectedText);
        
        validateLabel(result, expectedValues);
        
    }
    
    @Test
    public void useLabelShouldUpdateLabelStats() {
        
        insertSomeLabels();
        
        int label_id = 3;

        
        Map<String, Object> expected = new HashMap<String, Object>();
        expected.put(LabelContract.AllLabels.LAST_USED, new Date());
        
        Uri new_current_label = insertActiveLabel(label_id);

       
        Cursor label_data = querySingleLabelData(label_id);
        
        expected.put(LabelContract.AllLabels.USAGE_COUNT, 1);
        
        validateLabel(label_data, expected);
        
        contentResolver.delete(new_current_label, null, null);
        
        insertActiveLabel(label_id);
        
        label_data = querySingleLabelData(label_id);
        
        expected.put(LabelContract.AllLabels.USAGE_COUNT, 2);
        validateLabel(label_data, expected);
    }
    
    private Cursor querySingleLabelData(int label_id){
        Uri label_uri = Uri.withAppendedPath(LabelContract.AllLabels.CONTENT_URI,  String.valueOf(label_id));

        Cursor label_data = contentResolver.query(label_uri,
                ALL_PROJECTION, null, null, null);
        
        assertThat(label_data.getCount()).isEqualTo(1);
        assertThat(label_data.moveToNext()).isTrue();
        
        return label_data;
    }
    
//    @Test(expected = SQLException.class)
//    public void failMultipleUsageOfSameLabel() {
//        int label_id = 1;
//        insertSomeLabels();
//        insertActiveLabel(label_id);
//        insertActiveLabel(label_id);
//    }
    
    @Test(expected = SQLException.class)
    public void failDuplicateLabelText() {
        insertLabel("same text");
        insertLabel("same text");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void failInsertLabelWithTooMuchData() {
        ContentValues cv = new ContentValues();
        
        cv.put(LabelContract.AllLabels.TEXT, "some label");
        cv.put(LabelContract.AllLabels.USAGE_COUNT, 4);
        cv.put(LabelContract.AllLabels.LAST_USED, new Date().getTime());
        
        contentResolver.insert(LabelContract.AllLabels.CONTENT_URI, cv);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void failInsertLabelWithWrongData() {
        ContentValues cv = new ContentValues();
        
        cv.put(LabelContract.AllLabels.USAGE_COUNT, 4);
        cv.put(LabelContract.AllLabels.LAST_USED, new Date().getTime());
        
        contentResolver.insert(LabelContract.AllLabels.CONTENT_URI, cv);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void failInsertActiveLabelWithTooMuchData() {
        ContentValues cv = new ContentValues();
        
        cv.put(LabelContract.Active.LABEL_ID, 1);
        cv.put(LabelContract.Active.USAGE_COUNT, 4);
        cv.put(LabelContract.Active.LAST_USED, new Date().getTime());
        
        contentResolver.insert(LabelContract.Active.CONTENT_URI, cv);
    }

    @Test(expected = SQLException.class)
    public void failInsertActiveLabelWithoutBackingLabel() {
        ContentValues cv = new ContentValues();
        
        cv.put(LabelContract.Active.LABEL_ID, 1);
        
        contentResolver.insert(LabelContract.Active.CONTENT_URI, cv);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void failInsertActiveLabelWrongData() {
        ContentValues cv = new ContentValues();
        
        cv.put(LabelContract.Active.USAGE_COUNT, 4);
        
        contentResolver.insert(LabelContract.Active.CONTENT_URI, cv);
    }
    
    
    
}
