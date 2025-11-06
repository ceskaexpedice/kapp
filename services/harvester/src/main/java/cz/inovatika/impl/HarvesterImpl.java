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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static cz.inovatika.utils.CursorUtils.*;

import com.google.common.base.Stopwatch;

import java.util.concurrent.TimeUnit;

import org.w3c.dom.DOMException;

public class HarvesterImpl implements Harvester {

    public static final Logger LOGGER = Logger.getLogger(HarvesterImpl.class.getName());
    private static final String SOLR_URL = "http://localhost:8983/solr";
    private final int ROWS = 100;
    private static  final int BATCH_SIZE = 100;

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

                String query = pidCursorQuery(iterationQuery, cursorMark, ROWS, fq, "search", id, config.getApiVersion().getIdentifier(config) + " asc", fl);
                Element element = executeQuery(client, config.getUrl(), query);
                XMLUtils.getElementsRecursive(element, (elm) -> {
                    return elm.getNodeName().equals("doc");
                }).forEach(docElm -> {
                    APIVersion version = config.getApiVersion();
                    HarvestingItem harvestingItem = version.createHarvestItemFromResultElement(docElm, config);

                    HarvesterContext context = new HarvesterContext(harvestingItem, client, config);
                    consumer.accept(context);
                    //consumer.accept(harvestingItem);
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

    private static String executeSolrQuery(HttpClient client, String url, String query) throws IOException, InterruptedException {
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

    public static void main(String[] args) throws IOException {
        //inovatika();
        nkp();
    }

    private static void nkp() throws IOException {
        HarvesterImpl h = new HarvesterImpl();
        Instance config = new Instance();
        config.setAcronym("nkp");
        config.setApiVersion(APIVersion.v5);
        config.setSolrCloud(false);
        config.updateLastUpdatedTimestampManually(Instant.parse("2025-01-21T04:55:21.221Z"));
        config.setUrl("https://ndk.cz/search/api/v5.0/");
        
        //h.harvest(config,System.out::println);
        try (SolrClient solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build()) {
            List<SolrInputDocument> buffer = new ArrayList<>();
            List<SolrInputDocument> authorbuffer = new ArrayList<>();
            h.harvest(config, ctx -> processHarvest(config, ctx,buffer,authorbuffer, solrClient));
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
        config.setTimestampPath(udir + File.separator + "timestamps" + File.separator + "inovatika" + File.separator + "timestamp");

        config.setAcronym("inovatika");
        config.setApiVersion(APIVersion.v7);
        config.setSolrCloud(false);
        config.setUrl("https://k7.inovatika.dev/search/api/client/v7.0/");

//        h.harvest(config,System.out::println);
        try (SolrClient solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build()) {
            System.out.println("x");
            List<SolrInputDocument> buffer = new ArrayList<>();
            List<SolrInputDocument> authorbuffer = new ArrayList<>();
            h.harvest(config, ctx -> processHarvest(config, ctx, buffer,authorbuffer, solrClient));
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

    private static void sendBatch(final SolrClient solrClient, String collection, final List<SolrInputDocument> docs) throws SolrServerException, IOException {
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.add(docs);
        updateRequest.process(solrClient, collection);
        System.out.println("Indexed batch of " + docs.size() + " documents.");
    }

    private static void processHarvest(final Instance config,final  HarvesterContext ctx, List<SolrInputDocument> buffer,List<SolrInputDocument> authorbuffer,final SolrClient solrClient) {
        {
                //System.out.println(ctx.item().getPid());
                InputStream inputStream;
                try {
                    //System.out.println("");
                    Stopwatch sw = Stopwatch.createStarted();
                    inputStream = config.getApiVersion().fetchMods(ctx.client(), ctx.config(), ctx.item());
                    //elapseAndReset(sw);
                    Document parsed = XMLUtils.parseDocument(inputStream, true);
                    SolrInputDocument doc = new SolrInputDocument();
                    if(parsed!=null){
                        Node date = parsed.getElementsByTagNameNS("http://www.loc.gov/mods/v3","dateIssued").item(0);
                        if(date!=null){
                            String dateText = date.getTextContent();
                            doc.addField("date.str", dateText);
                            XmlProcessor.parseDateBasic(doc, dateText, 0, dateText);
                        }
                        NodeList phyicalLocation = parsed.getElementsByTagNameNS("http://www.loc.gov/mods/v3","physicalLocation");
                        if(phyicalLocation!=null){
                            List<String> locations = new ArrayList<>();
                            for(int i=0;i<phyicalLocation.getLength();i++){
                                Node locNode = phyicalLocation.item(i);
                                String locText = locNode.getTextContent();
                                locations.add(locText);
                            }
                            doc.addField("physical_locations.facet", locations);
                        }
                    }
                    doc.addField("pid", ctx.item().getPid());
                    doc.addField("licenses", ctx.item().getLicenses());
                    doc.addField("model", ctx.item().getModel());
                    doc.addField("root.title", ctx.item().getTitles());
                    AutorData[] autorData = XmlProcessor.processBiblioMods(parsed, doc);
                    List<String> authorStrings = new ArrayList<>();
                    for (AutorData autorData2 : autorData) {
                        if(autorData2!=null)
                            authorStrings.add(autorData2.full);
                    }
                    doc.addField("authors", authorStrings);
                    XmlProcessor.trySetLastAuthor(doc, autorData);
                    List<SolrInputDocument> ad = XmlProcessor.extractAuthorData(false, solrClient, doc, 0, autorData);
                    if(ad!=null)authorbuffer.addAll(ad);
                    //elapseAndReset(sw);
                    buffer.add(doc);
                    if (buffer.size() >= BATCH_SIZE) { // BATCH_SIZE
                        sendBatch(solrClient, "titles", buffer);
                        buffer.clear();
                        elapseAndReset(sw);
                    }
                    if (authorbuffer.size() >= BATCH_SIZE) { // BATCH_SIZE
                        sendBatch(solrClient, "authors", authorbuffer);
                        authorbuffer.clear();
                        //elapseAndReset(sw);
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
