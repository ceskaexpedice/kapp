package cz.inovatika.impl;

import cz.inovatika.Constants;
import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Updater;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowConfig;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static cz.inovatika.Constants.Collection.TITLES_COLLECTION;

public class UpdaterImpl implements Updater {
    public static final Logger LOGGER = Logger.getLogger(UpdaterImpl.class.getName());
    public SolrClient solrClient = null;
    final int IN_BATCHSIZE = 100;
    final int OUT_BATCHSIZE = 200;
    final String USER = "Updater";
    private static final List<String> DATE_ISSUED_YEAR_FIELDS = List.of(
            AppComputedFields.DATE_ISSUED_YEAR,
            AppComputedFields.DATE_ISSUED_YEAR_KAPP_FIELD
    );
    private static final List<String> SPECIAL_AUTHORSHIP_FIELDS = List.of(
            AppComputedFields.IS_PSEUDONYMOUS_FIELD,
            AppComputedFields.IS_ANONYMOUS_FIELD,
            AppComputedFields.IS_COLLECTIVE_FIELD
    );
    private static final List<String> RELEASE_RULE_FLAG_FIELDS = List.of(
            AppComputedFields.IS_PSEUDONYMOUS_FIELD,
            AppComputedFields.IS_ANONYMOUS_FIELD,
            AppComputedFields.IS_COLLECTIVE_FIELD,
            AppComputedFields.IS_OFFICIAL_FIELD
    );

    @Override
    public void start(ApplicationConfig applicationConfig) {
        if (solrClient == null) {
            this.solrClient = buildSolrClient(applicationConfig);
        }
        try {
            String cursorMark = "*";
            boolean done = false;
            WorkflowConfig workflowConfig = applicationConfig.workflow();
            int currentYear = Year.now().getValue();
            int threshold =  workflowConfig.calculatePublicYear();
            String candidateFilterQuery = buildCandidateFilterQuery(workflowConfig, currentYear);
            System.out.println("Current year:"+currentYear);
            System.out.println("Public earliest year:"+threshold);
            System.out.println("Private latest year:"+workflowConfig.calculateOnsiteYear());
            System.out.println("Author dead year: "+workflowConfig.calculateAuthorDeadYear());

            while (!done) {
                ModifiableSolrParams params = new ModifiableSolrParams();
                params.set("q", "*:*");
                params.set("rows", IN_BATCHSIZE);
                params.set("cursorMark", cursorMark);
                params.set("sort", "pid asc");
                //params.add("fq", "-"+AppComputedFields.ASSIGNED_LICENSES+":public");
                //params.add("fq", candidateFilterQuery);
                //params.add("fq", AppComputedFields.DATE_ISSUED_YEAR+":{ * TO "+threshold+"}");

                QueryResponse response = solrClient.query(TITLES_COLLECTION, params);

                String nextCursorMark = response.getNextCursorMark();

                List<SolrInputDocument> batch = new ArrayList<>();
                for (SolrDocument doc : response.getResults()) {
                    String pid = (String) doc.getFieldValue(AppDataFields.PID_FIELD);
                    Object dateIssuedObj = doc.getFieldValue(AppComputedFields.DATE_ISSUED_YEAR_KAPP_FIELD);
                    if (dateIssuedObj==null) dateIssuedObj = doc.getFieldValue(AppComputedFields.DATE_ISSUED_YEAR);
                    Object lastYearDeathObj = doc.getFieldValue(AppComputedFields.LAST_YEAR_DEATH);
                    Object statevalue = doc.getFieldValue(AppComputedFields.STATE_FIELD);
                    States state = (statevalue ==null) ? States.IMPORTED : States.valueOf(statevalue.toString());

                    Object modelvalue = doc.getFieldValue(AppDataFields.MODEL_FIELD);
                    String model = modelvalue ==null ? Constants.TitlesCollectionFields.ModelFields.MONOGRAPHUNIT : modelvalue.toString();

                    Object hierarchical_value = doc.getFieldValue(AppComputedFields.IS_HIERARCHICAL_FIELD);
                    boolean hierarchical = hierarchical_value != null && Boolean.parseBoolean(hierarchical_value.toString());

                    boolean isPseudonymous = isTrue(doc.getFieldValue(AppComputedFields.IS_PSEUDONYMOUS_FIELD));
                    boolean isAnonymous = isTrue(doc.getFieldValue(AppComputedFields.IS_ANONYMOUS_FIELD));
                    boolean isCollective = isTrue(doc.getFieldValue(AppComputedFields.IS_COLLECTIVE_FIELD));
                    boolean isOfficial = isTrue(doc.getFieldValue(AppComputedFields.IS_OFFICIAL_FIELD));

                    WorkflowDoc workflowDoc = new WorkflowDoc(
                            dateIssuedObj!=null? Integer.parseInt(dateIssuedObj.toString()):null,
                            lastYearDeathObj!=null?Integer.parseInt(lastYearDeathObj.toString()):null,
                            state,
                            USER,
                            model,
                            hierarchical,
                            isPseudonymous,
                            isAnonymous,
                            isCollective,
                            isOfficial//,state.getLicensesAssignation()
                    );
                    StringWrapper reason = new StringWrapper("unspecified") ;
                    Object root_pid_value = doc.getFieldValue(AppDataFields.ROOT_PID_FIELD);
                    String root_pid = root_pid_value == null ? pid : root_pid_value.toString();
                    States newState = workflowDoc.state().calculateNextState(root_pid,solrClient,applicationConfig,workflowDoc,reason);
                    SolrInputDocumentWrapper updateDoc = new SolrInputDocumentWrapper(AppDataFields.PID_FIELD, SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);
                    updateDoc.setField(AppDataFields.PID_FIELD, pid);
                    boolean shouldWrite = false;

                    if (newState != null) {
                        if (newState != workflowDoc.state())
                            System.out.println(
                                    "Found book with pid: "+pid
                                            +"\n\t that had state: "+workflowDoc.state()+" that got changed to: "+newState);

                        copyTrueFlags(doc, updateDoc, RELEASE_RULE_FLAG_FIELDS);
                        Workflow.executeTransition(workflowDoc,USER,reason.value,updateDoc,newState,applicationConfig.workflow());
                        shouldWrite = true;
                    } else {
                        shouldWrite = updateReleaseYearIfNeeded(doc, updateDoc, workflowDoc, workflowConfig);
                    }

                    if (!shouldWrite) continue;

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

    static boolean updateReleaseYearIfNeeded(
            SolrDocument source,
            SolrInputDocumentWrapper target,
            WorkflowDoc workflowDoc,
            WorkflowConfig workflowConfig
    ) {
        Integer currentReleaseYear = asInteger(source.getFieldValue(AppComputedFields.RELEASE_YEAR));
        Integer desiredReleaseYear = workflowDoc.releaseYear(workflowConfig);
        if (Objects.equals(currentReleaseYear, desiredReleaseYear)) {
            return false;
        }
        target.setField(AppComputedFields.RELEASE_YEAR, desiredReleaseYear);
        return true;
    }

    static String buildCandidateFilterQuery(WorkflowConfig workflowConfig, int currentYear) {
        String publicByIssuedYear = issuedYearTo(workflowConfig.calculatePublicYear());
        String publicByAuthorDeath = FilterQuery.and(
                issuedYearTo(workflowConfig.calculateOnsiteYear()),
                FilterQuery.rangeTo(AppComputedFields.LAST_YEAR_DEATH, workflowConfig.calculateAuthorDeadYear())
        );
        String publicByConfiguredReleaseYear = FilterQuery.rangeTo(AppComputedFields.RELEASE_YEAR, currentYear);
        String publicBySpecialAuthorship = FilterQuery.and(
                FilterQuery.anyTrue(SPECIAL_AUTHORSHIP_FIELDS),
                issuedYearTo(workflowConfig.calculateOnsiteYear())
        );
        String publicByOfficiality = FilterQuery.and(
                FilterQuery.isTrue(AppComputedFields.IS_OFFICIAL_FIELD),
                issuedYearTo(currentYear)
        );

        return FilterQuery.or(
                publicByConfiguredReleaseYear,
                publicByIssuedYear,
                publicByAuthorDeath,
                publicBySpecialAuthorship,
                publicByOfficiality
        );
    }

    private static String issuedYearTo(int year) {
        return FilterQuery.or(
                DATE_ISSUED_YEAR_FIELDS.stream()
                        .map(field -> FilterQuery.rangeTo(field, year))
                        .toArray(String[]::new)
        );
    }

    private static void copyTrueFlags(
            SolrDocument source,
            SolrInputDocumentWrapper target,
            List<String> fields
    ) {
        for (String field : fields) {
            if (isTrue(source.getFieldValue(field))) {
                target.setField(field, true);
            }
        }
    }

    private static boolean isTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Http2SolrClient buildSolrClient(ApplicationConfig applicationConfig) {
        return new Http2SolrClient.Builder(applicationConfig.solr().getUrl())
                .withConnectionTimeout(200, TimeUnit.SECONDS)
                .withIdleTimeout(5, TimeUnit.MINUTES)
                .withRequestTimeout(5, TimeUnit.MINUTES).useHttp1_1(true)
                .build();
    }

    private static final class FilterQuery {
        private FilterQuery() {
        }

        private static String and(String... clauses) {
            return group(" AND ", clauses);
        }

        private static String or(String... clauses) {
            return group(" OR ", clauses);
        }

        private static String rangeTo(String field, int year) {
            return field + ":{* TO " + year + "}";
        }

        private static String anyTrue(List<String> fields) {
            return or(fields.stream()
                    .map(FilterQuery::isTrue)
                    .toArray(String[]::new));
        }

        private static String isTrue(String field) {
            return field + ":true";
        }

        private static String group(String operator, String... clauses) {
            return "(" + String.join(operator, clauses) + ")";
        }
    }
}
