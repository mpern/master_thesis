package at.tugraz.knowcenter.label.model;

/**
 * Created by Markus Perndorfer on 13.11.13.
 */
public class UnusedLabelDTO extends AllLabelDTO {
    public UnusedLabelDTO(long id, String text, long last_used, long usage_count) {
        super(id,text,last_used,usage_count);
    }
}
