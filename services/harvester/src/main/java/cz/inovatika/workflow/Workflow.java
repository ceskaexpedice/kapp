package cz.inovatika.workflow;

import cz.inovatika.Constants.TitlesCollectionFields.ModelFields;
import cz.inovatika.model.HarvesterContext;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.apache.solr.client.solrj.SolrClient;

public class Workflow {
    public static States calculateAutomaticTransition(HarvesterContext ctx, WorkflowDoc currentDoc) {
        States state = currentDoc.state();
        StringWrapper reason = new StringWrapper(null);//unused
        return state != null ?  state.calculateNextState(ctx.item().getRootPid(),ctx.solrClient(),ctx.applicationConfig(),currentDoc,reason) : States.TO_CONTROL;
    }

    public static States calculateCuratorTransition(
            String rootPid,
            SolrClient solrClient,
            WorkflowDoc currentDoc,
            States desiredState
    ) {
        States state = currentDoc.state();
        States newState = state != null ?  state.transitionByCurator(rootPid,solrClient, currentDoc, desiredState) : null;
        String model = currentDoc.model();
        if(state!=newState && (model.equals(ModelFields.MONOGRAPHUNIT)) || model.equals(ModelFields.PERIODICALVOLUME)){
            States.updateParent(rootPid,solrClient);
        }
        return newState;
    }

    public static void executeTransition(
            WorkflowDoc currentDoc,
            String user,
            String message,
            SolrInputDocumentWrapper idoc,
            States newState
    ) {
        // PARENT_TO_CONTROL
        //States newState = calculateAutomaticTransition(ctx, currentDoc);

        new WorkflowDoc(
                currentDoc.dateIssuedYear(),
                currentDoc.last_year_death(),
                newState,
                user,
                currentDoc.model(),
                currentDoc.isHierarchical()//, newState.getLicensesAssignation()
        ).mapToSolrDocument(idoc, message);
    }


}
