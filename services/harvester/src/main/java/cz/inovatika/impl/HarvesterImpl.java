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
import org.apache.solr.common.SolrInputDocument;
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
import java.util.function.Consumer;
import java.util.logging.Logger;

import static cz.inovatika.utils.CursorUtils.*;

public class HarvesterImpl implements Harvester {

    public static final Logger LOGGER = Logger.getLogger(HarvesterImpl.class.getName());
    private static final String SOLR_URL = "http://localhost:8983/solr";
    private final int ROWS = 100;

    @Override
    public void harvest(Instance config, Consumer<HarvesterContext> consumer) {
        try {
            HttpClient client = HttpClient.newHttpClient();
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

        h.harvest(config, ctx -> {
            System.out.println(ctx.item());
            InputStream inputStream;
            try (SolrClient solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build()) {
                inputStream = config.getApiVersion().fetchMods(ctx.client(), ctx.config(), ctx.item());

                Document parsed = XMLUtils.parseDocument(inputStream, true);
                SolrInputDocument doc = new SolrInputDocument();
                doc.addField("pid", ctx.item().getPid());
                doc.addField("root.title", ctx.item().getTitles());
                AutorData[] autorData = XmlProcessor.processBiblioMods(parsed, doc);
                XmlProcessor.trySetLastAuthor(doc, autorData);
                XmlProcessor.extractAuthorData(false, solrClient, doc, 0, autorData);
                solrClient.add("titles", doc);
                solrClient.commit("titles");

            } catch (IOException | InterruptedException | ParserConfigurationException | SAXException | SolrServerException e) {
                throw new RuntimeException(e);
            } 
        });

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
        h.harvest(config, ctx -> {
            try {

                System.out.println(ctx.item());

                //String pid = ctx.item().getPid();
                InputStream inputStream = config.getApiVersion().fetchMods(ctx.client(), ctx.config(), ctx.item());
                Document parsed = XMLUtils.parseDocument(inputStream, true);
                SolrInputDocument doc = new SolrInputDocument();
                AutorData[] autorData = XmlProcessor.processBiblioMods(parsed, doc);
//                System.out.println(parsed.getDocumentElement().getNodeName());

//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            } catch (ParserConfigurationException e) {
//                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
