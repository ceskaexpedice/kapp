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
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields.LAST_AUTHOR_EXCEL_FIELD;

public class LastAuthorUpdaterImpl implements LastAuthorUpdater, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(LastAuthorUpdaterImpl.class.getName());
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
            int createdAuthors = 0;
            List<SolrInputDocument> batch = new ArrayList<>(options.batchSize());
            List<SolrInputDocument> authorBatch = new ArrayList<>(options.batchSize());
            Set<String> queuedAuthorIds = new HashSet<>();

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
                    List<String> lastAuthorsExcel = LastAuthorMatcher.asStrings(doc.getFieldValues(LAST_AUTHOR_EXCEL_FIELD));
                    JSONArray authorsKapp = LastAuthorMatcher.parseAuthorsKapp(doc.getFieldValue(AppDataFields.AUTHORS_KAPP_FIELD));
                    JSONArray authorsKramerius = LastAuthorMatcher.parseAuthorsKramerius(doc.getFieldValues(AppDataFields.AUTHORS_KRAMERIUS_FIELD));
                    JSONArray authorsCollection = findAuthorsInAuthorsCollection(lastAuthorsExcel);
                    LastAuthorMatcher.UpdatePlan updatePlan = matcher.prepareUpdate(
                            lastAuthorsExcel,
                            authorsKapp,
                            authorsKramerius,
                            authorsCollection,
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
                        for (JSONObject author : updatePlan.createdAuthors()) {
                            if (queueCreatedAuthor(author, authorBatch, queuedAuthorIds, options.batchSize())) {
                                System.out.println("Created author: " + author);
                                createdAuthors++;
                            }
                        }
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
                sendAuthorBatch(authorBatch);
                if (createdAuthors > 0) {
                    solrClient.commit(Constants.Collection.AUTHORS_COLLECTION);
                }
                solrClient.commit(Constants.Collection.TITLES_COLLECTION);
            }

            LOGGER.log(
                    Level.INFO,
                    "Last author update finished. Processed: {0}, matched: {1}, updated: {2}, createdAuthors: {3}, dryRun: {4}",
                    new Object[]{processed, matched, updated, createdAuthors, options.dryRun()}
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
        if (updatePlan.updateAuthors()) {
            addAtomicField(doc, "set", updatePlan.updatedAuthorsKapp().toString(), AppDataFields.AUTHORS_KAPP_FIELD);
        }
        if (updatePlan.lastYearDeath() != null) {
            addAtomicField(doc, "set", updatePlan.lastYearDeath(), AppComputedFields.LAST_YEAR_DEATH);
        }
        if (updatePlan.updateAuthors()) {
            addAtomicField(doc, "set", titleAuthorFields.ids(), AppDataFields.AUTHORS_IDS);
            addAtomicField(doc, "set", titleAuthorFields.nameIdentifiers(), AppDataFields.NAME_IDENTIFIER_FIELD);
            addAtomicField(doc, "set", titleAuthorFields.names(), AppDataFields.AUTHORS_FIELD);
        }
        List<String> labels = new ArrayList<>(updatePlan.labels());
        if (updatePlan.updateAuthors() || updatePlan.lastYearDeath() != null) {
            addDistinctString(labels, Constants.TitlesCollectionFields.Labels.AUTHOR_MODIFIED);
        }
        if (!labels.isEmpty()) {
            addAtomicField(doc, "add-distinct", labels, AppComputedFields.LABELS_FIELD);
        }
        addAtomicField(doc, "add-distinct", USER, "modified_by_user");
        return doc;
    }

    private JSONArray findAuthorsInAuthorsCollection(List<String> lastAuthorsExcel) throws SolrServerException, IOException {
        JSONArray authors = new JSONArray();
        Set<String> seenIds = new HashSet<>();
        for (LastAuthorMatcher.ExcelAuthor excelAuthor : LastAuthorMatcher.parseExcelAuthors(lastAuthorsExcel)) {
            Map<String, JSONObject> bestMatches = new LinkedHashMap<>();
            ModifiableSolrParams params = authorLookupParams(excelAuthor.name());
            QueryResponse response = solrClient.query(Constants.Collection.AUTHORS_COLLECTION, params);
            for (SolrDocument authorDocument : response.getResults()) {
                JSONObject author = toAuthorJson(authorDocument, excelAuthor);
                String key = authorDedupeKey(author);
                JSONObject existing = bestMatches.get(key);
                if (existing == null || preferAuthor(author, existing)) {
                    bestMatches.put(key, author);
                }
            }

            for (JSONObject author : bestMatches.values()) {
                String id = optPresentString(author, "id");
                if (id != null && seenIds.add(id)) {
                    authors.put(author);
                }
            }
        }
        return authors;
    }

    private static ModifiableSolrParams authorLookupParams(String name) {
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", authorLookupQuery(name));
        params.set("rows", 10);
        params.set(
                "fl",
                String.join(
                        ",",
                        "id",
                        "old_id",
                        "nameIdentifier",
                        "full",
                        "family",
                        "given",
                        "labels",
                        "termsOfAddress",
                        "date",
                        "date_birth",
                        "date_death",
                        "year_birth",
                        "year_death",
                        "confirmed_start_year",
                        "confirmed_end_year"
                )
        );
        return params;
    }

    private static String authorLookupQuery(String name) {
        List<String> clauses = new ArrayList<>();
        for (String variant : authorNameVariants(name)) {
            clauses.add("full:" + quoteSolrPhrase(variant));
        }

        String terms = authorLookupTerms(name);
        if (!terms.isBlank()) {
            clauses.add("full_text:(" + terms + ")");
        }

        return clauses.isEmpty() ? "id:__no_author_name__" : String.join(" OR ", clauses);
    }

    private static List<String> authorNameVariants(String name) {
        List<String> variants = new ArrayList<>();
        String trimmed = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        addDistinctString(variants, trimmed);
        if (trimmed.isBlank()) {
            return variants;
        }

        if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",", 2);
            String family = parts[0].trim();
            String given = parts.length > 1 ? parts[1].trim() : "";
            addDistinctString(variants, joinNameParts(given, family));
        } else {
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length > 1) {
                String family = tokens[tokens.length - 1];
                String given = String.join(" ", List.of(tokens).subList(0, tokens.length - 1));
                addDistinctString(variants, family + ", " + given);
            }
        }
        return variants;
    }

    private static String authorLookupTerms(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (cleaned.isBlank()) {
            return "";
        }

        List<String> terms = new ArrayList<>();
        for (String term : cleaned.split("\\s+")) {
            if (!term.isBlank()) {
                terms.add(ClientUtils.escapeQueryChars(term));
            }
        }
        return String.join(" AND ", terms);
    }

    private static String quoteSolrPhrase(String value) {
        return "\"" + ClientUtils.escapeQueryChars(value) + "\"";
    }

    private static String joinNameParts(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }

    private static void addDistinctString(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    private static String authorDedupeKey(JSONObject author) {
        String full = optPresentString(author, "full");
        if (full == null) {
            full = optPresentString(author, "id");
        }
        return normalizeAuthorKey(full)
                + "|"
                + Objects.toString(author.opt("year_birth"), "")
                + "|"
                + Objects.toString(author.opt("year_death"), "");
    }

    private static boolean preferAuthor(JSONObject candidate, JSONObject existing) {
        boolean candidateHasNameIdentifier = optPresentString(candidate, "nameIdentifier") != null;
        boolean existingHasNameIdentifier = optPresentString(existing, "nameIdentifier") != null;
        if (candidateHasNameIdentifier != existingHasNameIdentifier) {
            return candidateHasNameIdentifier;
        }
        return false;
    }

    private static String normalizeAuthorKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static JSONObject toAuthorJson(SolrDocument authorDocument, LastAuthorMatcher.ExcelAuthor excelAuthor) {
        JSONObject author = new JSONObject();
        copySolrField(author, authorDocument, "id");
        copySolrField(author, authorDocument, "old_id");
        copySolrField(author, authorDocument, "nameIdentifier");
        copySolrField(author, authorDocument, "full");
        copySolrField(author, authorDocument, "family");
        copySolrField(author, authorDocument, "given");
        copySolrField(author, authorDocument, "labels");
        copySolrField(author, authorDocument, "termsOfAddress");
        copySolrField(author, authorDocument, "date");
        copySolrField(author, authorDocument, "date_birth");
        copySolrField(author, authorDocument, "date_death");
        copySolrField(author, authorDocument, "year_birth");
        copySolrField(author, authorDocument, "year_death");
        copySolrField(author, authorDocument, "confirmed_start_year");
        copySolrField(author, authorDocument, "confirmed_end_year");

        mergeExcelYears(author, excelAuthor);
        if (optPresentString(author, "date") == null) {
            String date = buildDate(
                    LastAuthorMatcher.asInteger(author.opt("year_birth")),
                    LastAuthorMatcher.asInteger(author.opt("year_death"))
            );
            if (!date.isBlank()) {
                author.put("date", date);
            }
        }
        if (!author.has("role")) {
            author.put("role", new JSONArray());
        }
        author.put("isLast", false);
        return author;
    }

    private static void copySolrField(JSONObject author, SolrDocument authorDocument, String fieldName) {
        Object value = authorDocument.getFieldValue(fieldName);
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            JSONArray values = new JSONArray();
            for (Object item : collection) {
                values.put(item);
            }
            author.put(fieldName, values);
        } else {
            author.put(fieldName, value);
        }
    }

    private static void mergeExcelYears(JSONObject author, LastAuthorMatcher.ExcelAuthor excelAuthor) {
        if (!author.has("year_birth") && excelAuthor.birthYear() != null) {
            author.put("year_birth", excelAuthor.birthYear());
        }
        if (excelAuthor.deathYear() != null) {
            author.put("year_death", excelAuthor.deathYear());
            author.put("confirmed_end_year", true);
        }
        if (!author.has("confirmed_start_year")) {
            author.put("confirmed_start_year", author.has("year_birth"));
        }
        if (!author.has("confirmed_end_year")) {
            author.put("confirmed_end_year", author.has("year_death"));
        }
    }

    private static String buildDate(Integer birthYear, Integer deathYear) {
        if (birthYear == null && deathYear == null) {
            return "";
        }
        return (birthYear == null ? "" : birthYear) + "-" + (deathYear == null ? "" : deathYear);
    }

    private boolean queueCreatedAuthor(
            JSONObject author,
            List<SolrInputDocument> authorBatch,
            Set<String> queuedAuthorIds,
            int batchSize
    ) throws SolrServerException, IOException {
        String id = optPresentString(author, "id");
        if (id == null || !queuedAuthorIds.add(id)) {
            return false;
        }
        if (solrClient.getById(Constants.Collection.AUTHORS_COLLECTION, id) != null) {
            return false;
        }

        authorBatch.add(toAuthorDocument(author));
        if (authorBatch.size() >= batchSize) {
            sendAuthorBatch(authorBatch);
        }
        return true;
    }

    private static SolrInputDocument toAuthorDocument(JSONObject author) {
        SolrInputDocument doc = new SolrInputDocument();
        setFieldIfPresent(doc, author, "id");
        setFieldIfPresent(doc, author, "old_id");
        setFieldIfPresent(doc, author, "nameIdentifier");
        setFieldIfPresent(doc, author, "full");
        setFieldIfPresent(doc, author, "family");
        setFieldIfPresent(doc, author, "given");
        setFieldIfPresent(doc, author, "labels");
        setFieldIfPresent(doc, author, "termsOfAddress");
        setFieldIfPresent(doc, author, "date");
        setFieldIfPresent(doc, author, "date_birth");
        setFieldIfPresent(doc, author, "date_death");
        setFieldIfPresent(doc, author, "year_birth");
        setFieldIfPresent(doc, author, "year_death");
        setFieldIfPresent(doc, author, "confirmed_start_year");
        setFieldIfPresent(doc, author, "confirmed_end_year");
        doc.setField("modified_by_user", USER);
        return doc;
    }

    private static void setFieldIfPresent(SolrInputDocument doc, JSONObject author, String fieldName) {
        if (!author.has(fieldName) || author.isNull(fieldName)) {
            return;
        }
        Object value = author.get(fieldName);
        if (value instanceof JSONArray array) {
            doc.setField(fieldName, array.toList());
        } else if (optPresentString(author, fieldName) != null || value instanceof Number || value instanceof Boolean) {
            doc.setField(fieldName, value);
        }
    }

    private static String optPresentString(JSONObject author, String fieldName) {
        if (!author.has(fieldName) || author.isNull(fieldName)) {
            return null;
        }
        String value = author.optString(fieldName, null);
        if (value == null || value.isBlank() || "null".equals(value.trim())) {
            return null;
        }
        return value;
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

    private void sendAuthorBatch(List<SolrInputDocument> batch) throws SolrServerException, IOException {
        if (batch.isEmpty()) {
            return;
        }
        solrClient.add(Constants.Collection.AUTHORS_COLLECTION, batch);
        LOGGER.log(Level.INFO, "Sent author creation batch with {0} documents", batch.size());
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
