package cz.inovatika.workflow;

import cz.inovatika.model.HarvesterContext;
import cz.inovatika.model.Instance;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.json.JSONObject;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Workflow {

    public static final String DATE_JSON_KEY = "date";
    public static final String STATE_JSON_KEY = "state";
    public static final String USER_JSON_KEY = "user";
    public static final String REASON_JSON_KEY = "reason";

    public States calculateAutomaticTransition(HarvesterContext ctx, WorkflowDoc currentDoc) {
        States state = currentDoc.state();
        return state != null ?  state.calculateNextState(ctx, currentDoc) : null;
    }

    public States calculateCuratorTransition(HarvesterContext ctx, WorkflowDoc currentDoc, States desiredState) {
        States state = currentDoc.state();
        return state != null ?  currentDoc.state().transitionByCurator(ctx, currentDoc, desiredState) : null;
    }

    public void executeTransition(
            HarvesterContext ctx,
            WorkflowDoc currentDoc,
            String user,
            String message,
            SolrInputDocumentWrapper idoc
    ) {
        // PARENT_TO_CONTROLL
        States newState = calculateAutomaticTransition(ctx, currentDoc);

        WorkflowDoc newDoc = new WorkflowDoc(
                currentDoc.dateRangeEndYear(),
                newState,
                user,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")),
                currentDoc.model(),
                currentDoc.isHierarchical(),
                newState.getLicensesAssignation());
        newDoc.mapToSolrDocument(idoc, message);
    }


}
