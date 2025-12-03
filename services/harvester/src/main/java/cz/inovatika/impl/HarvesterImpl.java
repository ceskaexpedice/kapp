package cz.inovatika.impl;

import cz.inovatika.authors.AuthorData;
import cz.inovatika.Harvester;
import cz.inovatika.XmlProcessor;
import cz.inovatika.authors.AuthorDataUtils;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowConfig;
import cz.inovatika.model.*;
import cz.inovatika.workflow.States;
import cz.inovatika.utils.XMLUtils;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.eclipse.jetty.util.StringUtil;
import org.json.JSONObject;
import org.noggit.JSONUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static cz.inovatika.utils.CursorUtils.*;

import com.google.common.base.Stopwatch;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class HarvesterImpl implements Harvester {

    public static final Logger LOGGER = Logger.getLogger(HarvesterImpl.class.getName());
    private static final String SOLR_URL = "http://localhost:8983/solr";
    private final int ROWS = 100;
    private static final int BATCH_SIZE = 200;
    private static final int AUTHOR_BATCH_SIZE = 250;
    private static SolrClient solrClient;

    @Override
    public void harvest(ApplicationConfig applicationConfig, Instance harvestingInstanceConfig, Consumer<HarvesterContext> consumer) {
        harvest(applicationConfig, harvestingInstanceConfig, consumer, HttpClient.newHttpClient());
    }

    public void harvest(ApplicationConfig applicationConfig, Instance config, Consumer<HarvesterContext> consumer, HttpClient client) {
        String cursorMark = null;
        String queryCursorMark;
        // loading timestamp
        config.loadLastUpdatedFromPath();
        do {

            APIVersion apiVersion = config.getApiVersion();
            String iterationQuery = apiVersion.iterationQuery();
            String[] fl = apiVersion.fieldList();
            String id = apiVersion.getIdentifier(config);
            String fq = apiVersion.incrementFQ(config);

            String query = pidCursorQuery(iterationQuery, cursorMark, ROWS, fq, "search", id,
                    config.getApiVersion().getIdentifier(config) + " asc", fl);
            Element element = executeQuery(client, config.getUrl(), query);


            List<HarvestingItem> harvestItems = XMLUtils.getElementsRecursive(element, (elm) -> {
                return elm.getNodeName().equals("doc");
            }).stream().map(docElm -> {
                APIVersion version = config.getApiVersion();
                HarvestingItem harvestingItem = version.createHarvestItemFromResultElement(docElm, config);
                return harvestingItem;
            }).toList();

            String subquery = config.getApiVersion().prepareSubquery(harvestItems);
            if (!StringUtil.isEmpty(subquery)) {
                Element subResult = executeQuery(client, config.getUrl(), subquery);
                config.getApiVersion().processSubqueryResult(harvestItems, subResult);

            }

            for (HarvestingItem harvestingItem : harvestItems) {
                HarvesterContext context = new HarvesterContext(applicationConfig, harvestingItem, client, config);
                consumer.accept(context);
            }


            cursorMark = findCursorMark(element);
            queryCursorMark = findQueryCursorMark(element);

        } while ((cursorMark != null && queryCursorMark != null) && !cursorMark.equals(queryCursorMark));

        config.saveLastUpdatedToPath();
    }

    public static Element executeQuery(HttpClient httpClient, String url, String query) {
        try {
            String t = executeSolrQuery(httpClient, url, query);
            Document doc = XMLUtils.parseDocument(new StringReader(t));
            return doc.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String executeSolrQuery(HttpClient client, String url, String query)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create(url + "/" + query))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response != null && response.statusCode() == 200) {
            InputStream is = response.body();
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } else {
            throw new RuntimeException("HTTP error: " + (response != null ? response.statusCode() : "null"));
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    static LocalTime start;
    static LocalTime end;
    static long sleepSeconds = 2;
    static int intervalMinutes = 60;
    static boolean exitOnOutOfInterval = true;

    public static void main(String[] args) {

        start = LocalTime.now();
        end = start.plusMinutes(intervalMinutes);

        if (args.length > 0) {
            String timeRange = args[0];
            sleepSeconds = args.length > 1 ? Long.parseLong(args[1]) : 60;

            String[] parts = timeRange.split("-");
            if (parts.length != 2) {
                System.err.println("Invalid time range. Use format HH:mm-HH:mm");
                System.exit(1);
            }

            start = LocalTime.parse(parts[0], TIME_FMT);
            end = LocalTime.parse(parts[1], TIME_FMT);
            exitOnOutOfInterval = false;
        }
        try {

            solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build();
            checkTime();
            // inovatika();
            nkp();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);

        }
    }

    private static void checkTime()
            throws InterruptedException, IOException {
        boolean inRange;
        while (true) {
            LocalTime now = LocalTime.now();
            if (start.isBefore(end)) {
                // Simple range like 08:00–18:00
                inRange = !now.isBefore(start) && now.isBefore(end);
            } else {
                // Overnight range like 22:00–06:00
                inRange = !now.isBefore(start) || now.isBefore(end);
            }

            if (!inRange) {
                System.out.println("Outside allowed time (" + now.format(TIME_FMT) + "). Sleeping...");
                if (exitOnOutOfInterval) {
                    System.out.println("Harvest finished");
                    System.exit(0);
                } else {
                    Thread.sleep(sleepSeconds * 1000);
                }
            } else
                return;
        }

    }

    private static void nkp() // throws IOException
    {


        Instance config = new Instance();
        config.setAcronym("nkp");
        config.setApiVersion(APIVersion.v5);
        config.setSolrCloud(false);
        config.updateLastUpdatedTimestampManually(Instant.parse("2025-01-01T00:00:21.221Z"));
        config.setUrl("https://ndk.cz/search/api/v5.0/");

        WorkflowConfig workflowConfig = new WorkflowConfig();
        workflowConfig.setOnsiteYear(70);
        workflowConfig.setPublicYear(110);

        ApplicationConfig applicationConfig = new ApplicationConfig(List.of(config), workflowConfig, null);

        // h.harvest(config,System.out::println);
        try {
            LOGGER.log(Level.INFO, "Starting nkp");
            Harvester h = new HarvesterImpl();
            h.harvestAndBatch(applicationConfig, config);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static void inovatika() {
        final Instance config = new Instance();
        config.setTimestampPath(
                String.join(File.separator, System.getProperty("user.dir"), "timestamps", "inovatika", "timestamp"));

        config.setAcronym("inovatika");
        config.setApiVersion(APIVersion.v7);
        config.setSolrCloud(false);
        config.setUrl("https://k7.inovatika.dev/search/api/client/v7.0/");

        WorkflowConfig workflowConfig = new WorkflowConfig();
        workflowConfig.setOnsiteYear(70);
        workflowConfig.setPublicYear(110);

        ApplicationConfig applicationConfig = new ApplicationConfig(List.of(config), workflowConfig, null);

        // h.harvest(config,System.out::println);
        try {

            System.out.println("inovatika harvesting started");
            Harvester h = new HarvesterImpl();
            h.harvestAndBatch(applicationConfig, config);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void harvestAndBatch(ApplicationConfig applicationConfig, Instance config) throws SolrServerException, IOException {
        List<SolrInputDocument> buffer = new ArrayList<>(BATCH_SIZE);
        ArrayList<SolrInputDocument> authorBuffer = new ArrayList<>(AUTHOR_BATCH_SIZE + 20);
        harvest(applicationConfig, config, ctx -> processHarvest(config, ctx, buffer, authorBuffer, solrClient));
        if (!buffer.isEmpty()) { // BATCH_SIZE
            sendBatch(solrClient, "titles", buffer);
            buffer.clear();

        }
        if (!authorBuffer.isEmpty()) { // BATCH_SIZE
            sendBatch(solrClient, "authors", authorBuffer);
            authorBuffer.clear();

        }
    }

    private static void sendBatch(final SolrClient solrClient, String collection, final List<SolrInputDocument> docs)
            throws SolrServerException, IOException {
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.add(docs);
        updateRequest.process(solrClient, collection);
        System.out.println("Indexed batch of " + docs.size() + " documents.");
    }

    private static void processHarvest(final Instance config, final HarvesterContext ctx,
                                       List<SolrInputDocument> buffer, List<SolrInputDocument> authorsBuffer, final SolrClient solrClient) {
        {

            Stopwatch sw = Stopwatch.createStarted();
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", ctx.item().getPid());
            doc.addField("licenses", ctx.item().getLicenses());
            doc.addField("model", ctx.item().getModel());
            doc.addField("root.title", ctx.item().getTitles());
            doc.setField("root.pid", ctx.item().getRootPid());
            doc.setField("title", ctx.item().getTitles());

            try {
                InputStream inputStream = config.getApiVersion().fetchMods(ctx.client(), ctx.config(), ctx.item());
                if (inputStream == null) {
                    LOGGER.log(Level.SEVERE, "Failed to get MODS for PID: " + ctx.item().getPid());
                    return;
                }
                BiblioModsEntity entity = BiblioModsEntity.create(inputStream);
                Document parsed = XMLUtils.parseDocument(new StringReader(entity.getBiblioMods()), true);
                if (parsed != null) {

                    doc.setField("biblio_mods", entity.getBiblioMods());
                    doc.setField("biblio_mods_md5", entity.getMd5Sum());

                    Element dateIssued = XMLUtils.findElement(parsed.getDocumentElement(), elm -> elm.getLocalName().equals("dateIssued"));
                    if (dateIssued != null) {
                        String dateText = dateIssued.getTextContent();
                        doc.addField("date.str", dateText);

                        // TODO: change it
                        dateText = XmlProcessor.replaceWordsWithQMarks(dateText, 0, dateText);
                        XmlProcessor.parseDateBasic(doc, dateText, 0, dateText);
                    }

                    List<String> physicalLocation = XMLUtils.getElementsRecursive(parsed.getDocumentElement(), elm -> elm.getLocalName().equals("physicalLocation")).stream().map(Element::getTextContent).toList();
                    if (!physicalLocation.isEmpty()) {
                        doc.addField("physical_locations.facet", physicalLocation);
                    }

                    List<AuthorData> authorData = AuthorDataUtils.parseAuthorData(parsed);
                    doc.addField("authors", authorData.stream().map(AuthorData::getFull).toList());
                    authorData.forEach(author -> {
                        doc.addField("authors_kramerius", author.toJsonString());
                        if (author.getNameIdentifier() != null && !author.getNameIdentifier().isEmpty()) {
                            doc.addField("nameIdentifier", author.getNameIdentifier());
                        }
                    });

                    XmlProcessor.trySetLastAuthor(doc, authorData);

                    List<SolrInputDocument> authorityDocuments = authorData.stream().map(AuthorData::toAuthoritySolrDocument).collect(Collectors.toList());
                    authorsBuffer.addAll(authorityDocuments);

                    JSONObject oldDoc = getExistingDocument((String) doc.getFieldValue("pid"));
                    JSONObject importedDoc = new JSONObject(JSONUtil.toJSON(doc));
                    storeState(config, doc, oldDoc, States.IMPORTED.nextState(ctx, oldDoc, importedDoc));
                    // elapseAndReset(sw);
                    buffer.add(doc);
                    if (buffer.size() >= BATCH_SIZE) { // BATCH_SIZE
                        sendBatch(solrClient, "titles", buffer);
                        buffer.clear();
                        try {
                            checkTime();
                        } catch (InterruptedException | IOException e) {
                            LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        }
                    }
                    if (authorsBuffer.size() >= BATCH_SIZE) { // BATCH_SIZE
                        sendBatch(solrClient, "authors", authorsBuffer);
                        authorsBuffer.clear();
                    }
                } else {
                    LOGGER.log(Level.SEVERE, "Failed to process MODS for PID: " + ctx.item().getPid());
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static boolean documentExists(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery("pid:\"" + pid + "\"")
                .setRows(0);
        try {
            return solrClient.query("titles", q).getResults().getNumFound() > 0;
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return false;
        }
    }

    private static void storeState(Instance config, SolrInputDocument idoc, JSONObject oldDoc, States nState) {

        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        JSONObject h = new JSONObject()
                .put("date", d)
                .put("reason", "import from " + config.getUrl())
                .put("user", "importer")
                .put("state", nState.toString());

        idoc.setField("state", nState.toString());
        idoc.setField("state_date", d);
        idoc.setField("state_user", "importer");
        idoc.setField("state_reason", "import from " + config.getUrl());


        if (oldDoc != null) {


            boolean historyExists = oldDoc.has("state_history");
            if (historyExists) {
                Map<String, Object> partialUpdate = new HashMap<>();
                partialUpdate.put("add", h.toString());
                idoc.addField("state_history", partialUpdate);
            } else {
                idoc.addField("state_history", h.toString());
            }
        } else {
            idoc.addField("state_history", h.toString());
        }

    }

    private static JSONObject getExistingDocument(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery("pid:\"" + pid + "\"")
                .setFields("state_history")
                .setRows(1);
        try {
            SolrDocumentList docs = solrClient.query("titles", q).getResults();
            return docs.getNumFound() > 0 ? new JSONObject(JSONUtil.toJSON(docs.getFirst())) : null;
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return null;
        }
    }

    private static void elapseAndReset(Stopwatch sw) {
        sw.stop();
        System.out.println(sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        sw.reset().start();
    }

}
