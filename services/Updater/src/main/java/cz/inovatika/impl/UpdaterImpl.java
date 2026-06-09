package cz.inovatika.impl;

import cz.inovatika.Constants;
import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Updater;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.workflow.States;
import cz.inovatika.workflow.StringWrapper;
import cz.inovatika.workflow.Workflow;
import cz.inovatika.workflow.WorkflowDoc;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static cz.inovatika.Constants.Collection.TITLES_COLLECTION;

public class UpdaterImpl implements Updater {
    public static final Logger LOGGER = Logger.getLogger(UpdaterImpl.class.getName());
    public SolrClient solrClient = null;
    final int IN_BATCHSIZE = 100;
    final int OUT_BATCHSIZE = 200;
    final String USER = "Updater";

    @Override
    public void start(ApplicationConfig applicationConfig) {
        if (solrClient == null) {
            this.solrClient = buildSolrClient(applicationConfig);
        }
        try {
            String cursorMark = "*";
            boolean done = false;
            int currentYear = Year.now().getValue();
            int threshold =  applicationConfig.workflow().calculatePublicYear();
            System.out.println("Current year:"+currentYear);
            System.out.println("Public earliest year:"+threshold);
            System.out.println("Private latest year:"+applicationConfig.workflow().calculateOnsiteYear());
            System.out.println("Author dead year: "+applicationConfig.workflow().calculateAuthorDeadYear());

            while (!done) {
                ModifiableSolrParams params = new ModifiableSolrParams();
                params.set("q", "*:*");
                params.set("rows", IN_BATCHSIZE);
                params.set("cursorMark", cursorMark);
                params.set("sort", "pid asc");
                //params.add("fq", "-"+AppComputedFields.ASSIGNED_LICENSES+":public");
                params.add(
                        "fq",
                        "(" +
                                AppComputedFields.DATE_ISSUED_YEAR + ":{* TO " + threshold + "}" +
                                ") OR (" +
                                AppComputedFields.DATE_ISSUED_YEAR + ":{* TO "+ applicationConfig.workflow().calculateOnsiteYear()+"}" +
                                " AND " +
                                AppComputedFields.LAST_YEAR_DEATH + ":{* TO "+ applicationConfig.workflow().calculateAuthorDeadYear()+"}" +
                                ")"
                );
                //params.add("fq", AppComputedFields.DATE_ISSUED_YEAR+":{ * TO "+threshold+"}");

                QueryResponse response = solrClient.query(TITLES_COLLECTION, params);

                String nextCursorMark = response.getNextCursorMark();

                List<SolrInputDocument> batch = new ArrayList<>();
                for (SolrDocument doc : response.getResults()) {
                    String pid = (String) doc.getFieldValue(AppDataFields.PID_FIELD);
                    Object dateIssuedObj = doc.getFieldValue(AppDataFields.DATE_ISSUED_YEAR_KAPP_FIELD);
                    if (dateIssuedObj==null) dateIssuedObj = doc.getFieldValue(AppDataFields.DATE_ISSUED_YEAR_FIELD);
                    Object lastYearDeathObj = doc.getFieldValue(AppComputedFields.LAST_YEAR_DEATH);
                    Object statevalue = doc.getFieldValue(AppComputedFields.STATE_FIELD);
                    States state = (statevalue ==null) ? States.IMPORTED : States.valueOf(statevalue.toString());

                    Object modelvalue = doc.getFieldValue(AppDataFields.MODEL_FIELD);
                    String model = modelvalue ==null ? Constants.TitlesCollectionFields.ModelFields.MONOGRAPHUNIT : modelvalue.toString();

                    Object hierarchical_value = doc.getFieldValue(AppComputedFields.IS_HIERARCHICAL_FIELD);
                    boolean hierarchical = hierarchical_value != null && Boolean.parseBoolean(hierarchical_value.toString());

                    WorkflowDoc workflowDoc = new WorkflowDoc(
                            dateIssuedObj!=null? Integer.parseInt(dateIssuedObj.toString()):null,
                            lastYearDeathObj!=null?Integer.parseInt(lastYearDeathObj.toString()):null,
                            state,
                            USER,
                            model,
                            hierarchical//,state.getLicensesAssignation()
                    );
                    StringWrapper reason = new StringWrapper("unspecified") ;
                    Object root_pid_value = doc.getFieldValue(AppDataFields.ROOT_PID_FIELD);
                    String root_pid = root_pid_value == null ? pid : root_pid_value.toString();
                    States newState = workflowDoc.state().calculateNextState(root_pid,solrClient,applicationConfig,workflowDoc,reason);
                    if (newState==null)continue;
                    if (newState != workflowDoc.state())
                        System.out.println(
                                "Found book with pid: "+pid
                                        +"\n\t that had state: "+workflowDoc.state()+" that got changed to: "+newState);

                    SolrInputDocumentWrapper updateDoc = new SolrInputDocumentWrapper(pid, SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);
                    updateDoc.getDocument().setField(AppDataFields.PID_FIELD, pid);
                    Workflow.executeTransition(workflowDoc,USER,reason.value,updateDoc,newState);


                    batch.add(updateDoc.getDocument());

                    // send batch
                    if (batch.size() >= OUT_BATCHSIZE) {
                        solrClient.add(TITLES_COLLECTION, batch);
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    solrClient.add(TITLES_COLLECTION, batch);
                }

                solrClient.commit(TITLES_COLLECTION);

                if (cursorMark.equals(nextCursorMark)) {
                    done = true;
                }
                cursorMark = nextCursorMark;
            }
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static Http2SolrClient buildSolrClient(ApplicationConfig applicationConfig) {
        return new Http2SolrClient.Builder(applicationConfig.solr().getUrl())
                .withConnectionTimeout(200, TimeUnit.SECONDS)
                .withIdleTimeout(5, TimeUnit.MINUTES)
                .withRequestTimeout(5, TimeUnit.MINUTES).useHttp1_1(true)
                .build();
    }
}
