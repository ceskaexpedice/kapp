package cz.inovatika.impl;

import cz.inovatika.Constants;
import cz.inovatika.Constants.ContractsCollectionFields;
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
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static cz.inovatika.Constants.Collection.CONTRACTS_COLLECTION;
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
            System.out.println("Current year: "+currentYear);
            System.out.println("Public earliest year: "+threshold);
            System.out.println("Private latest year: "+workflowConfig.calculateOnsiteYear());
            System.out.println("Author dead year: "+workflowConfig.calculateAuthorDeadYear());

            applyContractLicenses();

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
                    boolean shouldWrite = labelMissingRootTitleIfNeeded(doc, updateDoc);

                    if (newState != null) {
                        if (newState != workflowDoc.state())
                            System.out.println(
                                    "Found book with pid: "+pid
                                            +"\n\t that had state: "+workflowDoc.state()+" that got changed to: "+newState);

                        copyTrueFlags(doc, updateDoc, RELEASE_RULE_FLAG_FIELDS);
                        Workflow.executeTransition(workflowDoc,USER,reason.value,updateDoc,newState,applicationConfig.workflow());
                        shouldWrite = true;
                    } else {
                        shouldWrite |= updateReleaseYearIfNeeded(doc, updateDoc, workflowDoc, workflowConfig);
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

    private void applyContractLicenses() throws SolrServerException, IOException {
        int start = 0;
        while (true) {
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("q", "*:*");
            params.set("rows", IN_BATCHSIZE);
            params.set("start", start);
            params.set("fl", String.join(",",
                    ContractsCollectionFields.FILENAME_FIELD,
                    ContractsCollectionFields.CCNB_FIELD,
                    ContractsCollectionFields.ISSN_FIELD,
                    ContractsCollectionFields.LICENSE_FIELD
            ));

            QueryResponse response = solrClient.query(CONTRACTS_COLLECTION, params);
            if (response.getResults().isEmpty()) {
                return;
            }

            for (SolrDocument contract : response.getResults()) {
                if (updateTitlesMatchingContract(contract)) {
                    solrClient.commit(TITLES_COLLECTION);
                }
            }

            start += response.getResults().size();
            if (start >= response.getResults().getNumFound()) {
                return;
            }
        }
    }

    private boolean updateTitlesMatchingContract(SolrDocument contract) throws SolrServerException, IOException {
        String contractName = contractName(contract);
        List<ContractLicense> contractLicenses = contractLicenses(contract, contractName);
        if (contractLicenses.isEmpty()) {
            return false;
        }

        String matchFilter = buildContractMatchFilter(contract);
        if (matchFilter == null) {
            return false;
        }

        String cursorMark = "*";
        boolean done = false;
        boolean changed = false;
        while (!done) {
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("q", "*:*");
            params.add("fq", matchFilter);
            params.set("rows", IN_BATCHSIZE);
            params.set("cursorMark", cursorMark);
            params.set("sort", "pid asc");
            params.set("fl", titleFieldsForLicenseCheck(contractLicenses));

            QueryResponse response = solrClient.query(TITLES_COLLECTION, params);
            String nextCursorMark = response.getNextCursorMark();
            List<SolrInputDocument> batch = new ArrayList<>();

            for (SolrDocument title : response.getResults()) {
                String pid = (String) title.getFieldValue(AppDataFields.PID_FIELD);
                SolrInputDocumentWrapper updateDoc = new SolrInputDocumentWrapper(
                        AppDataFields.PID_FIELD,
                        SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
                );
                updateDoc.setField(AppDataFields.PID_FIELD, pid);

                if (!addMissingContractLicenses(title, updateDoc, contractLicenses)) {
                    continue;
                }

                System.out.println(
                        "Found book with pid: "+pid
                                +"\n\t that matched contract: "+contractName
                                +"\n\t adding contract licenses: "+contractLicenses.stream().map(ContractLicense::license).toList()
                );
                batch.add(updateDoc.getDocument());
                changed = true;

                if (batch.size() >= OUT_BATCHSIZE) {
                    solrClient.add(TITLES_COLLECTION, batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                solrClient.add(TITLES_COLLECTION, batch);
            }

            if (cursorMark.equals(nextCursorMark)) {
                done = true;
            }
            cursorMark = nextCursorMark;
        }
        return changed;
    }

    static boolean addMissingContractLicenses(
            SolrDocument source,
            SolrInputDocumentWrapper target,
            List<ContractLicense> contractLicenses
    ) {
        Set<String> currentLicenses = stringFieldValues(source, AppDataFields.LICENSES_FIELD);
        Set<String> missingLicenses = new LinkedHashSet<>();
        Map<String, Set<String>> missingDigitalLibraryLicenses = new LinkedHashMap<>();

        for (ContractLicense contractLicense : contractLicenses) {
            String license = contractLicense.license();
            if (!currentLicenses.contains(license)) {
                missingLicenses.add(license);
            }

            String digitalLibraryField = contractLicense.digitalLibraryField();
            if (!stringFieldValues(source, digitalLibraryField).contains(license)) {
                missingDigitalLibraryLicenses
                        .computeIfAbsent(digitalLibraryField, ignored -> new LinkedHashSet<>())
                        .add(license);
            }
        }

        if (missingLicenses.isEmpty() && missingDigitalLibraryLicenses.isEmpty()) {
            return false;
        }

        if (!missingLicenses.isEmpty()) {
            target.addField(AppDataFields.LICENSES_FIELD, new ArrayList<>(missingLicenses));
        }
        for (Map.Entry<String, Set<String>> entry : missingDigitalLibraryLicenses.entrySet()) {
            target.addField(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return true;
    }

    static ContractLicense contractLicense(String license) {
        if (license == null) {
            return null;
        }
        String normalizedLicense = license.trim();
        int separatorIndex = normalizedLicense.indexOf('_');
        if (separatorIndex <= 0 || separatorIndex == normalizedLicense.length() - 1) {
            return null;
        }
        String acronym = normalizedLicense.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
        return new ContractLicense(normalizedLicense, acronym);
    }

    private static List<ContractLicense> contractLicenses(SolrDocument contract, String contractName) {
        Map<String, ContractLicense> contractLicenses = new LinkedHashMap<>();
        for (String license : stringFieldValues(contract, ContractsCollectionFields.LICENSE_FIELD)) {
            ContractLicense contractLicense = contractLicense(license);
            if (contractLicense == null) {
                System.out.println(
                        "Skipping contract license: "+license
                                +"\n\t from contract: "+contractName
                                +"\n\t because it does not contain an acronym prefix separated by '_'"
                );
                continue;
            }
            contractLicenses.putIfAbsent(contractLicense.license(), contractLicense);
        }
        return new ArrayList<>(contractLicenses.values());
    }

    private static String buildContractMatchFilter(SolrDocument contract) {
        List<String> clauses = new ArrayList<>();
        String ccnbFilter = fieldMatchesAny("id_ccnb", stringFieldValues(contract, ContractsCollectionFields.CCNB_FIELD));
        String issnFilter = fieldMatchesAny("id_issn", stringFieldValues(contract, ContractsCollectionFields.ISSN_FIELD));

        if (ccnbFilter != null) {
            clauses.add(ccnbFilter);
        }
        if (issnFilter != null) {
            clauses.add(issnFilter);
        }
        if (clauses.isEmpty()) {
            return null;
        }
        return "(" + String.join(" OR ", clauses) + ")";
    }

    private static String fieldMatchesAny(String field, Set<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        return field + ":(" + String.join(" OR ", values.stream().map(UpdaterImpl::quoteQueryValue).toList()) + ")";
    }

    private static String quoteQueryValue(String value) {
        return "\"" + ClientUtils.escapeQueryChars(value) + "\"";
    }

    private static String titleFieldsForLicenseCheck(List<ContractLicense> contractLicenses) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add(AppDataFields.PID_FIELD);
        fields.add(AppDataFields.LICENSES_FIELD);
        for (ContractLicense contractLicense : contractLicenses) {
            fields.add(contractLicense.digitalLibraryField());
        }
        return String.join(",", fields);
    }

    private static String contractName(SolrDocument contract) {
        Object filename = contract.getFieldValue(ContractsCollectionFields.FILENAME_FIELD);
        if (filename != null && !filename.toString().isBlank()) {
            return filename.toString();
        }
        return "unknown";
    }

    private static Set<String> stringFieldValues(SolrDocument document, String field) {
        Set<String> values = new LinkedHashSet<>();
        Collection<Object> fieldValues = document.getFieldValues(field);
        if (fieldValues != null) {
            fieldValues.forEach(value -> addStringValue(values, value));
            return values;
        }
        addStringValue(values, document.getFieldValue(field));
        return values;
    }

    private static void addStringValue(Set<String> values, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addStringValue(values, item));
            return;
        }
        String stringValue = value.toString().trim();
        if (!stringValue.isBlank()) {
            values.add(stringValue);
        }
    }

    record ContractLicense(String license, String acronym) {
        String digitalLibraryField() {
            return AppDataFields.LICENSES_DL_FIELD + acronym;
        }
    }

    static boolean labelMissingRootTitleIfNeeded(
            SolrDocument source,
            SolrInputDocumentWrapper target
    ) {
        Object rootTitle = source.getFieldValue(AppDataFields.ROOT_TITLE_FIELD);
        if (rootTitle != null && !rootTitle.toString().isBlank()) {
            return false;
        }
        target.addField(AppComputedFields.LABELS_FIELD, Constants.TitlesCollectionFields.Labels.MISSING_ROOT_TITLE);
        return true;
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
