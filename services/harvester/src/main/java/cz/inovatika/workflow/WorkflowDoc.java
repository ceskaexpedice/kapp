package cz.inovatika.workflow;

import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.conf.WorkflowConfig;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cz.inovatika.Constants.TitlesCollectionFields.*;

public record WorkflowDoc(
        Integer dateIssuedYear,
        Integer last_year_death,
        States state,
        String user,
        String model,
        boolean isHierarchical//,
        //LicensesAssignation assignation
){

    public static final   DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String DATE_JSON_KEY = "date";
    public static final String STATE_JSON_KEY = "state";
    public static final String USER_JSON_KEY = "user";
    public static final String REASON_JSON_KEY = "reason";


    public void mapToSolrDocument(SolrInputDocumentWrapper idoc, String message, WorkflowConfig workflowConfig) {
        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        idoc.setField(AppComputedFields.STATE_FIELD, this.state.name());
        idoc.setField(AppComputedFields.STATE_DATE_FIELD, d);
        idoc.setField(AppComputedFields.STATE_USER_FIELD, this.user);
        idoc.setField(AppComputedFields.STATE_REASON_FIELD,  message);
        idoc.addField(AppComputedFields.STATE_HISTORY_FIELD, historyItem(d, message).toString());

        Integer releaseYear = workflowConfig != null
                ? workflowConfig.calculateReleaseYear(this.dateIssuedYear, this.last_year_death)
                : null;
        Object pseudo= idoc.getFieldValue("is_pseudonymous");
        Object anon= idoc.getFieldValue("is_anonymous");
        Object collective= idoc.getFieldValue("is_collective");
        if(
                (pseudo instanceof Boolean && ((Boolean) pseudo)) ||
                (anon instanceof Boolean && ((Boolean) anon)) ||
                (collective instanceof Boolean && ((Boolean) collective))
        ) {
            if(this.dateIssuedYear!=null && workflowConfig != null){
                releaseYear = this.dateIssuedYear+workflowConfig.getOnsiteYear();
                System.out.println("Book is pseudo/anonymous/collective");
            }
        }

        Object officiality = idoc.getFieldValue("is_official");
        if(officiality instanceof Boolean && ((Boolean) officiality)){
            releaseYear = this.dateIssuedYear;
            System.out.println("Book is official");
        }

        if (releaseYear != null) {
            idoc.setField(AppComputedFields.RELEASE_YEAR, releaseYear);
        }
        switch (state.getLicensesAssignation()) {
            case PUBLIC -> {
                Map<String, Object> operation = new HashMap<>();
                operation.put("remove", "onsite");
                operation.put("add-distinct", "public");
                idoc.getDocument().setField(AppComputedFields.ASSIGNED_LICENSES, operation);
            }
            case ONSITE -> {
                Map<String, Object> operation = new HashMap<>();
                operation.put("remove", "public");
                operation.put("add-distinct", "onsite");
                idoc.getDocument().setField(AppComputedFields.ASSIGNED_LICENSES, operation);
            }
            case NONE -> {
                //TODO?
                Map<String, Object> operation = new HashMap<>();
                operation.put("remove", List.of("onsite","public"));
                idoc.getDocument().setField(AppComputedFields.ASSIGNED_LICENSES, operation);
            }
            case LOCAL -> {
                //TODO 2
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
