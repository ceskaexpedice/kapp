package cz.inovatika.workflow;

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
        idoc.setField(AppComputedFields.STATE_FIELD, this.state.name());
        idoc.setField(AppComputedFields.STATE_DATE_FIELD, d);
        idoc.setField(AppComputedFields.STATE_USER_FIELD, this.user);
        idoc.setField(AppComputedFields.STATE_REASON_FIELD,  message);
        idoc.addField(AppComputedFields.STATE_HISTORY_FIELD, historyItem(d, message).toString());
        switch (this.assignation) {
            case PUBLIC -> {
                idoc.setField(AppComputedFields.DECISION_DATE,dateRangeEndYear);
                idoc.removeField(AppComputedFields.ASSIGNED_LICENSES, "onsite");
                idoc.addField(AppComputedFields.ASSIGNED_LICENSES, "public");
            }
            case ONSITE -> {
                idoc.setField(AppComputedFields.DECISION_DATE,dateRangeEndYear);
                idoc.removeField(AppComputedFields.ASSIGNED_LICENSES, "public");
                idoc.addField(AppComputedFields.ASSIGNED_LICENSES, "onsite");
            }
            default -> {
                //no license assigned
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
