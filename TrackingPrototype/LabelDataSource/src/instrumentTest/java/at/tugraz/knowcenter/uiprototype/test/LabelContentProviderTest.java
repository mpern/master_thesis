package at.tugraz.knowcenter.uiprototype.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.test.ProviderTestCase2;

import at.tugraz.knowcenter.label.contentproviders.LabelContentProvider;
import at.tugraz.knowcenter.label.contentproviders.LabelContract;

public class LabelContentProviderTest extends
		ProviderTestCase2<LabelContentProvider> {

	private static String[] ALL_PROJECTION = { LabelContract.AllLabels._ID,
			LabelContract.AllLabels.TEXT, LabelContract.AllLabels.LAST_USED,
			LabelContract.AllLabels.USAGE_COUNT };

	private static String[] UNUSED_PROJECTION = {
			LabelContract.UnusedLabels._ID, LabelContract.UnusedLabels.TEXT,
			LabelContract.UnusedLabels.LAST_USED,
			LabelContract.UnusedLabels.USAGE_COUNT };

	public LabelContentProviderTest() {
		super(LabelContentProvider.class, LabelContract.AUTHORITY);
	}

	private static List<String> SOME_LABELS = Arrays.asList("first", "second",
			"third");

	public void testInsertAllLabels() {
		ContentResolver resolver = getMockContentResolver();
		String first_label = "first";

		addLabel(first_label);

		Cursor result = resolver.query(LabelContract.AllLabels.CONTENT_URI,
				ALL_PROJECTION, null, null, null);

		assertNotNull(result);

		assertEquals(1, result.getCount());

		while (result.moveToNext()) {
			validateNewLabel(result, 1, first_label);
		}
	}

	private void addLabel(String label_name) {
		ContentResolver resolver = getMockContentResolver();
		ContentValues newValues = new ContentValues();
		newValues.put(LabelContract.AllLabels.TEXT, label_name);
		resolver.insert(LabelContract.AllLabels.CONTENT_URI, newValues);
	}

	private void validateNewLabel(Cursor result, int expected_id,
			String expected_label_text) {

		Map<String, Object> expected = new HashMap<String, Object>();
		expected.put(LabelContract.AllLabels._ID, Integer.valueOf(expected_id));
		expected.put(LabelContract.AllLabels.TEXT, expected_label_text);
		expected.put(LabelContract.AllLabels.LAST_USED, null);
		expected.put(LabelContract.AllLabels.USAGE_COUNT, null);

		validateLabel(result, expected);

	}

	private void validateLabel(Cursor result, Map<String, Object> expectedValues) {

		for (Map.Entry<String, Object> expected : expectedValues.entrySet()) {
			Object expectedValue = expected.getValue();
			int index = result.getColumnIndex(expected.getKey());
			assertTrue(
					String.format("Column index of %s < 0!", expected.getKey()),
					(index >= 0));
			if (expectedValue == null)
				assertTrue(expected.getKey(), result.isNull(index));
			else if (expectedValue instanceof Integer)
				assertEquals(expected.getKey(), expectedValue,
						result.getInt(index));
			else if (expectedValue instanceof String)
				assertEquals(expected.getKey(), expectedValue,
						result.getString(index));
		}
	}

	public void testWrongAllLabel() {
		ContentResolver resolver = getMockContentResolver();

		ContentValues faulty = new ContentValues();
		faulty.put(LabelContract.AllLabels.TEXT, (String) null);

		try {
			resolver.insert(LabelContract.AllLabels.CONTENT_URI, faulty);
			fail("Expected SQL Exception");
		} catch (SQLException e) {

		}

	}

	public void testActive() {
		ContentResolver resolver = getMockContentResolver();

		int all_labels_id = 2;

		insertSomeLabels();

		Uri new_current = insertActiveLabel(all_labels_id);
		assertEquals(
				Uri.withAppendedPath(LabelContract.Active.CONTENT_URI, "1"),
				new_current);

		Cursor result = resolver.query(new_current, null, null, null, null);
		result.moveToNext();
		validateActiveLabel(result, 1, 2, SOME_LABELS.get(all_labels_id - 1));

		int numDel = resolver.delete(new_current, null, null);

		assertEquals(1, numDel);
	}

	private List<String> insertSomeLabels() {

		for (String label : SOME_LABELS) {
			addLabel(label);
		}
		return SOME_LABELS;
	}

	private void validateActiveLabel(Cursor c, int expected_id, int label_id,
			String text) {
		Map<String, Object> expected = new HashMap<String, Object>();
		expected.put(LabelContract.Active._ID, Integer.valueOf(expected_id));
		expected.put(LabelContract.Active.LABEL_ID, Integer.valueOf(label_id));
		expected.put(LabelContract.Active.TEXT, text);

		validateLabel(c, expected);
	}

	private Uri insertActiveLabel(int all_label_id) {

		ContentResolver resolver = getMockContentResolver();

		ContentValues currentLabel = new ContentValues();
		currentLabel.put(LabelContract.Active.LABEL_ID, all_label_id);
		Uri active_label = resolver.insert(LabelContract.Active.CONTENT_URI,
				currentLabel);

		return active_label;
	}

	public void testUnused() {

		ContentResolver resolver = getMockContentResolver();

		insertSomeLabels();

		validateUnusedLabelList(SOME_LABELS);

		Uri new_current_label = insertActiveLabel(2);

		Cursor result = resolver.query(LabelContract.UnusedLabels.CONTENT_URI,
				UNUSED_PROJECTION, null, null, null);

		assertNotNull(result);

		assertEquals(SOME_LABELS.size() - 1, result.getCount());

		result.moveToNext();
		validateUnusedLabel(result, 1, SOME_LABELS.get(0));
		result.moveToNext();
		validateUnusedLabel(result, 3, SOME_LABELS.get(2));

		int numDel = resolver.delete(new_current_label, null, null);
		assertEquals(1, numDel);

		validateUnusedLabelList(SOME_LABELS);
	}

	private void validateUnusedLabelList(List<String> labels) {

		ContentResolver resolver = getMockContentResolver();
		Cursor result = resolver.query(LabelContract.UnusedLabels.CONTENT_URI,
				UNUSED_PROJECTION, null, null, null);

		assertNotNull(result);

		assertEquals(labels.size(), result.getCount());

		int i = 1;
		while (result.moveToNext()) {
			validateUnusedLabel(result, i, labels.get(i - 1));
			i++;
		}
	}

	private void validateUnusedLabel(Cursor result, int expectedId,
			String expectedText) {
		Map<String, Object> expectedValues = new HashMap<String, Object>();
		expectedValues.put(LabelContract.UnusedLabels._ID,
				Integer.valueOf(expectedId));
		expectedValues.put(LabelContract.UnusedLabels.TEXT, expectedText);

		validateLabel(result, expectedValues);

	}

	public void testFaultyActiveLabel() {
		ContentValues cv = new ContentValues();

		cv.put(LabelContract.Active.LABEL_ID, 1);
		ContentResolver contentResolver = getMockContentResolver();
		try {
			contentResolver.insert(LabelContract.Active.CONTENT_URI, cv);
			fail("Expected SQL exception");
		} catch (SQLException e) {
		}
	}

}
