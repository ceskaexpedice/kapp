package cz.inovatika.impl;

import cz.inovatika.AutorData;
import cz.inovatika.Harvester;
import cz.inovatika.XmlProcessor;
import cz.inovatika.model.APIVersion;
import cz.inovatika.model.HarvesterContext;
import cz.inovatika.model.HarvestingItem;
import cz.inovatika.model.Instance;
import cz.inovatika.utils.XMLUtils;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static cz.inovatika.utils.CursorUtils.*;

import com.google.common.base.Stopwatch;

import java.util.concurrent.TimeUnit;


public class HarvesterImpl implements Harvester {

    public static final Logger LOGGER = Logger.getLogger(HarvesterImpl.class.getName());
    private static final String SOLR_URL = "http://localhost:8983/solr";
    private final int ROWS = 100;
    private static final int BATCH_SIZE = 100;
    private static SolrClient solrClient;

    @Override
    public void harvest(Instance config, Consumer<HarvesterContext> consumer) {
        try (HttpClient client = HttpClient.newHttpClient()) {
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
                XMLUtils.getElementsRecursive(element, (elm) -> {
                    return elm.getNodeName().equals("doc");
                }).forEach(docElm -> {
                    APIVersion version = config.getApiVersion();
                    HarvestingItem harvestingItem = version.createHarvestItemFromResultElement(docElm, config);

                    HarvesterContext context = new HarvesterContext(harvestingItem, client, config);
                    consumer.accept(context);
                    // consumer.accept(harvestingItem);
                });
                cursorMark = findCursorMark(element);
                queryCursorMark = findQueryCursorMark(element);

            } while ((cursorMark != null && queryCursorMark != null) && !cursorMark.equals(queryCursorMark));

            config.saveLastUpdatedToPath();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            String retval = IOUtils.toString(is, StandardCharsets.UTF_8);
            return retval;
        } else {
            throw new RuntimeException("HTTP error: " + (response != null ? response.statusCode() : "null"));
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    static LocalTime start;
    static LocalTime end;
    static long sleepSeconds = 2;
    static int intervalMinutes = 1;
    static boolean exitOnOutOfInterval = true;

    public static void main(String[] args) throws InterruptedException, IOException {
        // if (args.length == 0) {

        // System.err.println("Usage: java cz.inovatika.impl.HarvesterImpl <time-range> [sleep-seconds]");
        // System.err.println("Example: java cz.inovatika.impl.HarvesterImpl 22:00-06:00 60");
        // System.exit(1);
        // }

        start = LocalTime.now();
        end = start.plus(intervalMinutes, ChronoUnit.MINUTES);

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
        solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build();
        checkTime(sleepSeconds, start, end);
        // inovatika();
        nkp();
    }

    private static void checkTime(long sleepSeconds, LocalTime start, LocalTime end)
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
        HarvesterImpl h = new HarvesterImpl();
        Instance config = new Instance();
        config.setAcronym("nkp");
        config.setApiVersion(APIVersion.v5);
        config.setSolrCloud(false);
        config.updateLastUpdatedTimestampManually(Instant.parse("2025-01-21T04:55:21.221Z"));
        config.setUrl("https://ndk.cz/search/api/v5.0/");

        // h.harvest(config,System.out::println);
        try {
            System.out.println("nkp harvesting started");
            List<SolrInputDocument> buffer = new ArrayList<>();
            List<SolrInputDocument> authorbuffer = new ArrayList<>();
            h.harvest(config, ctx -> processHarvest(config, ctx, buffer, authorbuffer, solrClient));
            if (!buffer.isEmpty()) { // BATCH_SIZE
                sendBatch(solrClient, "titles", buffer);
                buffer.clear();

            }
            if (!authorbuffer.isEmpty()) { // BATCH_SIZE
                sendBatch(solrClient, "authors", authorbuffer);
                authorbuffer.clear();

            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static void inovatika() throws IOException {
        String udir = System.getProperty("user.dir");

        HarvesterImpl h = new HarvesterImpl();
        final Instance config = new Instance();
        config.setTimestampPath(
                udir + File.separator + "timestamps" + File.separator + "inovatika" + File.separator + "timestamp");

        config.setAcronym("inovatika");
        config.setApiVersion(APIVersion.v7);
        config.setSolrCloud(false);
        config.setUrl("https://k7.inovatika.dev/search/api/client/v7.0/");

        // h.harvest(config,System.out::println);
        try {
            System.out.println("inovatika harvesting started");
            List<SolrInputDocument> buffer = new ArrayList<>();
            List<SolrInputDocument> authorbuffer = new ArrayList<>();
            h.harvest(config, ctx -> processHarvest(config, ctx, buffer, authorbuffer, solrClient));
            if (!buffer.isEmpty()) { // BATCH_SIZE
                sendBatch(solrClient, "titles", buffer);
                buffer.clear();

            }
            if (!authorbuffer.isEmpty()) { // BATCH_SIZE
                sendBatch(solrClient, "authors", authorbuffer);
                authorbuffer.clear();

            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
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
            List<SolrInputDocument> buffer, List<SolrInputDocument> authorbuffer, final SolrClient solrClient) {
        {
            // System.out.println(ctx.item().getPid());
            InputStream inputStream;
            Stopwatch sw = Stopwatch.createStarted();
            try {
                // System.out.println("");
                SolrInputDocument doc = new SolrInputDocument();
                doc.addField("pid", ctx.item().getPid());
                doc.addField("licenses", ctx.item().getLicenses());
                doc.addField("model", ctx.item().getModel());
                doc.addField("root.title", ctx.item().getTitles());

                inputStream = config.getApiVersion().fetchMods(ctx.client(), ctx.config(), ctx.item());
                if (inputStream == null) {
                    System.out.println("Failed to get MODS for PID: " + ctx.item().getPid());
                    return;
                }
                // elapseAndReset(sw);
                Document parsed = XMLUtils.parseDocument(inputStream, true);
                if (parsed != null) {
                    Node date = parsed.getElementsByTagNameNS("http://www.loc.gov/mods/v3", "dateIssued").item(0);
                    if (date != null) {
                        String dateText = date.getTextContent();
                        doc.addField("date.str", dateText);
                        XmlProcessor.parseDateBasic(doc, dateText, 0, dateText);
                    }
                    NodeList phyicalLocation = parsed.getElementsByTagNameNS("http://www.loc.gov/mods/v3",
                            "physicalLocation");
                    if (phyicalLocation != null) {
                        List<String> locations = new ArrayList<>();
                        for (int i = 0; i < phyicalLocation.getLength(); i++) {
                            Node locNode = phyicalLocation.item(i);
                            String locText = locNode.getTextContent();
                            locations.add(locText);
                        }
                        doc.addField("physical_locations.facet", locations);
                    }
                }

                    // Vyplnit state podle 
                    // https://docs.google.com/document/d/1zMXdaKiAhshUANmxTD1MHnBibjituskJnGEq-LHz7jU/edit?tab=t.0
                    
                    
                    // if (doc.containsKey("licenses") && Arrays.asList(doc.getFieldValue("licenses")).contains("public")) {
                    //     doc.setField("state", "controlled_public"); // KONTROLOVANÉ - VOLNÉ
                    // } else 
                    if (("monograph".equals(ctx.item().getModel()) || "monographunit".equals(ctx.item().getModel())) && 
                        doc.containsKey("date_range_end.year") && 
                        1900 > (int)doc.getFieldValue("date_range_end.year")) {
                        doc.setField("state", "noncontrolled_public"); // NEKONTROLOVANÉ - ZPŘÍSTUPNĚNÉ
                    // } else if("periodicalvolume".equals(ctx.item().getModel()) && 
                    //             doc.containsKey("date_range_end.year") && 
                    //             110 > (LocalDate.now().getYear()) - (int)doc.getFieldValue("date_range_end.year")) {
                    //     doc.setField("state", "noncontrolled_public"); // NEKONTROLOVANÉ - ZPŘÍSTUPNĚNÉ
                    } else if (("monograph".equals(ctx.item().getModel()) || "monographunit".equals(ctx.item().getModel()))  && 
                                doc.containsKey("date_range_end.year") && 
                                70 > (LocalDate.now().getYear()) - (int)doc.getFieldValue("date_range_end.year")) {
                        doc.setField("state", "noncontrolled_private"); // NEKONTROLOVANÉ 
                    } else {
                        doc.setField("state", "to_control"); // KE KONTROLE
                    }
                

                AutorData[] autorData = XmlProcessor.processBiblioMods(parsed, doc);
                List<String> authorStrings = new ArrayList<>();
                for (AutorData autorD : autorData) {
                    if (autorD != null) {
                        authorStrings.add(autorD.full);
                    }
                }
                doc.addField("authors", authorStrings);
                XmlProcessor.trySetLastAuthor(doc, autorData);
                List<SolrInputDocument> ad = XmlProcessor.extractAuthorData(false, solrClient, doc, 0, autorData);
                if (ad != null) {
                    authorbuffer.addAll(ad);
                }
                // elapseAndReset(sw);
                buffer.add(doc);
                if (buffer.size() >= BATCH_SIZE) { // BATCH_SIZE
                    sendBatch(solrClient, "titles", buffer);
                    buffer.clear();
                    elapseAndReset(sw);
                    try {
                        checkTime(sleepSeconds, start, end);
                    } catch (InterruptedException | IOException e) {
                        System.err.println("Error during time check: " + e.getMessage());
                    }
                }
                if (authorbuffer.size() >= BATCH_SIZE) { // BATCH_SIZE
                    sendBatch(solrClient, "authors", authorbuffer);
                    authorbuffer.clear();
                    // elapseAndReset(sw);
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static void elapseAndReset(Stopwatch sw) {
        sw.stop();
        System.out.println(sw.elapsed(TimeUnit.MILLISECONDS) + " ms");
        sw.reset().start();
    }

}
