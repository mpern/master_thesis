package at.tugraz.knowcenter.label.contentproviders;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public final class LabelContract {
	private LabelContract() {}
	
	public static final String AUTHORITY = "at.tugraz.knowcenter.labels";

    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
    
    private static final String DIR_MIME = ContentResolver.CURSOR_DIR_BASE_TYPE + "/";
    private static final String ITEM_MIME = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/";
    
    private static interface AllLabelColumns extends BaseColumns {
    	public static final String TEXT = "label_text";
    	public static final String LAST_USED = "last_used";
    	public static final String USAGE_COUNT = "usage_count";
    }
    
    public static final class AllLabels implements AllLabelColumns{
    	private AllLabels() {}
    	static final String TABLE_NAME = "all_labels";
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, TABLE_NAME);

    	public static final String CONTENT_TYPE = DIR_MIME + AUTHORITY + "." + TABLE_NAME;
    	public static final String CONTENT_ITEM_TYPE = ITEM_MIME + AUTHORITY + "." + TABLE_NAME;
    }
    
    public static final class Active implements AllLabelColumns {


        private Active() {}
    	static final String TABLE_NAME = "active_labels";
    	public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, TABLE_NAME);
    	
    	public static final String LABEL_ID = "label_id";
        public static final String ACTIVATED_TS = "activated_ts";
        public static final String DEACTIVATED_TS = "deactivated_ts";
    	
    	public static final String CONTENT_TYPE = DIR_MIME + AUTHORITY + "." + TABLE_NAME;
    	public static final String CONTENT_ITEM_TYPE = ITEM_MIME + AUTHORITY + "." + TABLE_NAME;
    }
    
    public static final class UnusedLabels implements AllLabelColumns {
    	private UnusedLabels() {}
    	static final String TABLE_NAME = "unusedLabels";
    	public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, TABLE_NAME);
    
    	public static final String CONTENT_TYPE = DIR_MIME + AUTHORITY + "." + TABLE_NAME;
    	public static final String CONTENT_ITEM_TYPE = ITEM_MIME + AUTHORITY + "." + TABLE_NAME;
    }

}
