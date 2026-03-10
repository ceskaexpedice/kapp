package cz.inovatika.workflow;

import cz.inovatika.model.HarvesterContext;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.apache.solr.client.solrj.SolrClient;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Workflow {
/*
    public static final String DATE_JSON_KEY = "date";
    public static final String STATE_JSON_KEY = "state";
    public static final String USER_JSON_KEY = "user";
    public static final String REASON_JSON_KEY = "reason";
*/
    public States calculateAutomaticTransition(HarvesterContext ctx, WorkflowDoc currentDoc) {
        States state = currentDoc.state();
        return state != null ?  state.calculateNextState(ctx.item().getRootPid(),ctx.solrClient(),ctx.applicationConfig(),currentDoc) : null;
    }

    public States calculateCuratorTransition(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
        States state = currentDoc.state();
        States newState = state != null ?  currentDoc.state().transitionByCurator(rootPid,solrClient, currentDoc, desiredState) : null;
        String model = currentDoc.model();
        if(state!=newState && (model.equals("monographunit")) || model.equals("periodicalvolume")){
            States.updateParent(rootPid,solrClient);
        }
        return newState;
    }

    public void executeTransition(
            WorkflowDoc currentDoc,
            String user,
            String message,
            SolrInputDocumentWrapper idoc,
            States newState
    ) {
        // PARENT_TO_CONTROL
        //States newState = calculateAutomaticTransition(ctx, currentDoc);

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
