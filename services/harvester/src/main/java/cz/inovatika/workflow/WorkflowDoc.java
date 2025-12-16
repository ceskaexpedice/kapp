package cz.inovatika.workflow;

import cz.inovatika.Constants;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static cz.inovatika.Constants.TitlesCollectionFields.*;

public record WorkflowDoc(
        Integer dateRangeEndYear,
        States state,
        String user,
        String date,
        String model,
        boolean isHierarchical,
        LicensesAssignation assignation
){

    public static final   DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String DATE_JSON_KEY = "date";
    public static final String STATE_JSON_KEY = "state";
    public static final String USER_JSON_KEY = "user";
    public static final String REASON_JSON_KEY = "reason";


    public void mapToSolrDocument(SolrInputDocumentWrapper idoc, String message) {
        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        JSONObject h = historyItem(d, message);
        idoc.setField(Constants.TitlesCollectionFields.ApplFields.STATE_FIELD, this.state.name());
        idoc.setField(ApplFields.STATE_DATE_FIELD, d);
        idoc.setField(ApplFields.STATE_USER_FIELD, this.user);
        idoc.setField(ApplFields.STATE_REASON_FIELD,  message);
        idoc.addField(ApplFields.STATE_HISTORY_FIELD, h.toString());
        if (this.assignation != null) {
            switch (this.assignation) {
                case PUBLIC:
                    idoc.setField(ApplFields.DECISION_DATE,dateRangeEndYear);
                    idoc.setField(ApplFields.ASSIGNED_LICENSE, "public");
                break;
                case ONSITE:
                    idoc.setField(ApplFields.DECISION_DATE,dateRangeEndYear);
                    idoc.setField(ApplFields.ASSIGNED_LICENSE, "onsite");
                break;
            }
        }
    }

    private JSONObject historyItem(String d, String message) {
        return new JSONObject()
                .put(DATE_JSON_KEY, d)
                .put(REASON_JSON_KEY, message)
                .put(USER_JSON_KEY, this.user)
                .put(STATE_JSON_KEY, this.state.name());
    }
}
