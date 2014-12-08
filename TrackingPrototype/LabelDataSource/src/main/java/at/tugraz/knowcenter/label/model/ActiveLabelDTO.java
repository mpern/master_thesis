package at.tugraz.knowcenter.label.model;

/**
* Created by Markus Perndorfer on 13.11.13.
*/
public class ActiveLabelDTO extends AllLabelDTO{
    public final long labelId;
    public  final long activatedTS;
    public final long deactivatedTS;

    public ActiveLabelDTO(long id,
                          long label_id,
                          String text,
                          long last_used,
                          long usage_count,
                          long activated_ts,
                          long deactivated_ts) {
        super(id, text, last_used, usage_count);
        labelId = label_id;
        activatedTS = activated_ts;
        deactivatedTS = deactivated_ts;
    }
}

