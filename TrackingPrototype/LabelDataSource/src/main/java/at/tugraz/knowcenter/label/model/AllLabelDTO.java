package at.tugraz.knowcenter.label.model;

/**
 * Created by Markus Perndorfer on 13.11.13.
 */
public class AllLabelDTO {
    public final long id;
    public final String labelText;
    public final long lastUsed;
    public final long usageCount;

    private AllLabelDTO() {
        throw new UnsupportedOperationException();
    }

    public AllLabelDTO(long id, String text, long last_used, long usage_count) {
        this.id = id;
        this.labelText = text;
        this.lastUsed = last_used;
        this.usageCount = usage_count;
    }
}

