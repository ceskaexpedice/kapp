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
        boolean isHierarchical,
        boolean isPseudonymous,
        boolean isAnonymous,
        boolean isCollective,
        boolean isOfficial//,
        //LicensesAssignation assignation
){

    public static final   DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    public static final String DATE_JSON_KEY = "date";
    public static final String STATE_JSON_KEY = "state";
    public static final String USER_JSON_KEY = "user";
    public static final String REASON_JSON_KEY = "reason";

    public WorkflowDoc(
            Integer dateIssuedYear,
            Integer last_year_death,
            States state,
            String user,
            String model,
            boolean isHierarchical
    ) {
        this(dateIssuedYear, last_year_death, state, user, model, isHierarchical, false, false, false, false);
    }

    public void mapToSolrDocument(SolrInputDocumentWrapper idoc, String message, WorkflowConfig workflowConfig) {
        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        idoc.setField(AppComputedFields.STATE_FIELD, this.state.name());
        idoc.setField(AppComputedFields.STATE_DATE_FIELD, d);
        idoc.setField(AppComputedFields.STATE_USER_FIELD, this.user);
        idoc.setField(AppComputedFields.STATE_REASON_FIELD,  message);
        idoc.addField(AppComputedFields.STATE_HISTORY_FIELD, historyItem(d, message).toString());

        Integer releaseYear = releaseYear(workflowConfig);

        if (releaseYear != null || idoc.getTypeOfChange() == SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE) {
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
        idoc.markModified(d);
        
    }

    public Integer releaseYear(WorkflowConfig workflowConfig) {
        if (workflowConfig == null) {
            return null;
        }
        Integer priorityReleaseYear = priorityReleaseYear(workflowConfig);
        if (hasPriorityReleaseRule()) {
            return priorityReleaseYear;
        }
        return workflowConfig.calculateReleaseYear(this.dateIssuedYear, this.last_year_death);
    }

    public Integer priorityReleaseYear(WorkflowConfig workflowConfig) {
        if (workflowConfig == null || this.dateIssuedYear == null) {
            return null;
        }
        if (this.isOfficial) {
            return this.dateIssuedYear;
        }
        if (hasSpecialAuthorshipRule()) {
            return this.dateIssuedYear + workflowConfig.getAuthorDeadEnoughYear();
        }
        return null;
    }

    public boolean hasSpecialAuthorshipRule() {
        return this.isPseudonymous || this.isAnonymous || this.isCollective;
    }

    public boolean hasPriorityReleaseRule() {
        return hasSpecialAuthorshipRule() || this.isOfficial;
    }

    private JSONObject historyItem(String d, String message) {
        return new JSONObject()
                .put(DATE_JSON_KEY, d)
                .put(REASON_JSON_KEY, message)
                .put(USER_JSON_KEY, this.user)
                .put(STATE_JSON_KEY, this.state.name());
    }
}
