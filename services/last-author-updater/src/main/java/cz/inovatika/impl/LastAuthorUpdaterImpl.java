package cz.inovatika.impl;

import cz.inovatika.Constants;
import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.LastAuthorUpdater;
import cz.inovatika.conf.ApplicationConfig;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LastAuthorUpdaterImpl implements LastAuthorUpdater, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(LastAuthorUpdaterImpl.class.getName());
    private static final String LAST_AUTHOR_EXCEL_FIELD = "last_author_excel";
    private static final String USER = "LastAuthorUpdater";

    private SolrClient solrClient;

    public LastAuthorUpdaterImpl() {
    }

    @Override
    public void start(ApplicationConfig applicationConfig, LastAuthorUpdaterOptions options) {
        if (solrClient == null) {
            solrClient = buildSolrClient(applicationConfig);
        }

        try {
            LastAuthorMatcher matcher = new LastAuthorMatcher();
            String cursorMark = "*";
            boolean done = false;
            int processed = 0;
            int matched = 0;
            int updated = 0;
            List<SolrInputDocument> batch = new ArrayList<>(options.batchSize());

            while (!done) {
                ModifiableSolrParams params = queryParams(cursorMark, options.rows());
                QueryResponse response = solrClient.query(Constants.Collection.TITLES_COLLECTION, params);
                String nextCursorMark = response.getNextCursorMark();

                for (SolrDocument doc : response.getResults()) {
                    if (options.limit() > 0 && processed >= options.limit()) {
                        done = true;
                        break;
                    }
                    processed++;

                    String pid = Objects.toString(doc.getFieldValue(AppDataFields.PID_FIELD), "");
                    JSONArray authorsKapp = LastAuthorMatcher.parseAuthorsKapp(doc.getFieldValue(AppDataFields.AUTHORS_KAPP_FIELD));
                    JSONArray authorsKramerius = LastAuthorMatcher.parseAuthorsKramerius(doc.getFieldValues(AppDataFields.AUTHORS_KRAMERIUS_FIELD));
                    LastAuthorMatcher.UpdatePlan updatePlan = matcher.prepareUpdate(
                            LastAuthorMatcher.asStrings(doc.getFieldValues(LAST_AUTHOR_EXCEL_FIELD)),
                            authorsKapp,
                            authorsKramerius,
                            LastAuthorMatcher.asInteger(doc.getFieldValue(AppComputedFields.LAST_YEAR_DEATH))
                    );

                    if (updatePlan.matched()) {
                        matched++;
                    }
                    if (!updatePlan.shouldUpdate()) {
                        logVerbose(options, "No update for " + pid + ": " + updatePlan.reason());
                        continue;
                    }

                    updated++;
                    logVerbose(
                            options,
                            String.format(
                                    "Updating %s: %s -> %s",
                                    pid,
                                    updatePlan.matchedExcelAuthor(),
                                    updatePlan.matchedAuthor().optString("full")
                            )
                    );

                    if (!options.dryRun()) {
                        batch.add(toUpdateDocument(pid, updatePlan));
                        if (batch.size() >= options.batchSize()) {
                            sendBatch(batch);
                        }
                    }
                }

                if (cursorMark.equals(nextCursorMark)) {
                    done = true;
                }
                cursorMark = nextCursorMark;
            }

            if (!options.dryRun()) {
                sendBatch(batch);
                solrClient.commit(Constants.Collection.TITLES_COLLECTION);
            }

            LOGGER.log(
                    Level.INFO,
                    "Last author update finished. Processed: {0}, matched: {1}, updated: {2}, dryRun: {3}",
                    new Object[]{processed, matched, updated, options.dryRun()}
            );
        } catch (SolrServerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ModifiableSolrParams queryParams(String cursorMark, int rows) {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", LAST_AUTHOR_EXCEL_FIELD + ":*");
        params.set("rows", rows);
        params.set("cursorMark", cursorMark);
        params.set("sort", AppDataFields.PID_FIELD + " asc");
        params.set(
                "fl",
                String.join(
                        ",",
                        AppDataFields.PID_FIELD,
                        LAST_AUTHOR_EXCEL_FIELD,
                        AppDataFields.AUTHORS_KAPP_FIELD,
                        AppDataFields.AUTHORS_KRAMERIUS_FIELD,
                        AppComputedFields.LAST_YEAR_DEATH
                )
        );
        return params;
    }

    private SolrInputDocument toUpdateDocument(String pid, LastAuthorMatcher.UpdatePlan updatePlan) {
        SolrInputDocument doc = new SolrInputDocument();
        LastAuthorMatcher.TitleAuthorFields titleAuthorFields = updatePlan.titleAuthorFields();
        doc.setField(AppDataFields.PID_FIELD, pid);
        addAtomicField(doc, "set", updatePlan.updatedAuthorsKapp().toString(), AppDataFields.AUTHORS_KAPP_FIELD);
        addAtomicField(doc, "set", updatePlan.lastYearDeath(), AppComputedFields.LAST_YEAR_DEATH);
        addAtomicField(doc, "set", titleAuthorFields.ids(), AppDataFields.AUTHORS_IDS);
        addAtomicField(doc, "set", titleAuthorFields.nameIdentifiers(), AppDataFields.NAME_IDENTIFIER_FIELD);
        addAtomicField(doc, "set", titleAuthorFields.names(), AppDataFields.AUTHORS_FIELD);
        addAtomicField(doc, "add-distinct", Constants.TitlesCollectionFields.Labels.AUTHOR_MODIFIED, AppComputedFields.LABELS_FIELD);
        addAtomicField(doc, "add-distinct", USER, "modified_by_user");
        return doc;
    }

    private static void addAtomicField(SolrInputDocument doc, String operation, Object value, String fieldName) {
        Map<String, Object> partialUpdate = new HashMap<>();
        partialUpdate.put(operation, value);
        doc.setField(fieldName, partialUpdate);
    }

    private void sendBatch(List<SolrInputDocument> batch) throws SolrServerException, IOException {
        if (batch.isEmpty()) {
            return;
        }
        solrClient.add(Constants.Collection.TITLES_COLLECTION, batch);
        LOGGER.log(Level.INFO, "Sent last-author update batch with {0} documents", batch.size());
        batch.clear();
    }

    private static void logVerbose(LastAuthorUpdaterOptions options, String message) {
        if (options.verbose()) {
            LOGGER.info(message);
        }
    }

    private static Http2SolrClient buildSolrClient(ApplicationConfig applicationConfig) {
        return new Http2SolrClient.Builder(applicationConfig.solr().getUrl())
                .withConnectionTimeout(200, TimeUnit.SECONDS)
                .withIdleTimeout(5, TimeUnit.MINUTES)
                .withRequestTimeout(5, TimeUnit.MINUTES)
                .useHttp1_1(true)
                .build();
    }

    @Override
    public void close() throws IOException {
        if (solrClient != null) {
            solrClient.close();
        }
    }
}
