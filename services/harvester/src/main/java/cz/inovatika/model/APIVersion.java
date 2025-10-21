package cz.inovatika.model;

import cz.inovatika.utils.XMLUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public enum APIVersion {

    v5 {
        @Override
        public String getIdentifier(Instance cloud) {
            return "PID";
        }

        @Override
        public String iterationQuery() {
            String query = "fedora.model:(monograph+OR+monographunit+OR+periodical+OR+periodicalvolume)";//&fl=PID+pid_path+root_pid+fedora.model";
            return query;
        }

        @Override
        public String[] fieldList() {
            return new String[]{"PID", "pid_path", "root_pid", "fedora.model","dc.title"};
        }

        public HarvestingItem createHarvestItemFromResultElement(Element docResultElement, Instance instance) {
            final HarvestingItem item = new HarvestingItem();
            item.setInstance(instance);

            Element modelElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("fedora.model"));
            });
            if (modelElm != null) {
                item.setModel(modelElm.getTextContent());
            }

            Element pidElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("PID"));
            });
            if (pidElm != null) {
                item.setPid(pidElm.getTextContent());
            }

            Element rootPidElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("root_pid"));
            });
            if (rootPidElm != null) {
                item.setRootPid(rootPidElm.getTextContent());
            }


            Element titlesElements = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("dc.title"));
            });

            if (titlesElements != null) {
                item.setTitles(titlesElements.getTextContent());
            }

            return item;
        }

        @Override
        public String incrementFQ(Instance instance) {
            Instant lastUpdated = instance.getLastUpdated();
            if (lastUpdated != null) {
                String solrDateString = SOLR_DATE_FORMATTER.format(lastUpdated);
                return String.format("modified_date:[%s TO NOW]",solrDateString);
            }
            return null;
        }

        @Override
        public InputStream getMods(HttpClient client, String apiUrl, HarvestingItem item) throws IOException, InterruptedException {
            String modsUrl = String.format(apiUrl + (apiUrl.endsWith("/") ? "" : "/") + "item/%s/streams/BIBLIO_MODS", item.getPid());
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(modsUrl))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response != null && response.statusCode() == 200) {
                return response.body();
            } else {
                LOGGER.severe("HTTP error: " + (response != null ? response.statusCode() : "null"));
                return null;
            }
        }
    },

    v7 {
        @Override
        public String getIdentifier(Instance instance) {
            return instance.isSolrCloud() ? "compositeId" : "pid";
        }

        @Override
        public String iterationQuery() {
            String query = "model:(monograph+OR+monographunit+OR+periodical+OR+periodicalvolume)";//&fl=pid+own_pid_path+root.pid+model";
            return query;
        }

        @Override
        public String[] fieldList() {
            return new String[]{"pid", "own_pid_path", "root.pid", "model", "titles.search"};
        }

        @Override
        public HarvestingItem createHarvestItemFromResultElement(Element docResultElement, Instance instance) {
            final HarvestingItem item = new HarvestingItem();
            item.setInstance(instance);

            Element modelElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("model"));
            });
            if (modelElm != null) {
                item.setModel(modelElm.getTextContent());
            }

            Element pidElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("pid"));
            });
            if (pidElm != null) {
                item.setPid(pidElm.getTextContent());
            }

            Element rootPidElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("root.pid"));
            });
            if (rootPidElm != null) {
                item.setRootPid(rootPidElm.getTextContent());
            }


            Element titlesElements = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("arr") && name.equals("titles.search"));
            });

            if (titlesElements != null) {
                String titles = XMLUtils.getElements(titlesElements).stream().map(Element::getTextContent).collect(Collectors.joining(", "));
                item.setTitles(titles);
            }

            return item;
        }

        @Override
        public String incrementFQ(Instance instance) {
            Instant lastUpdated = instance.getLastUpdated();
            if (lastUpdated != null) {
                String solrDateString = SOLR_DATE_FORMATTER.format(lastUpdated);
                return String.format("indexed:[%s TO NOW]",solrDateString);
            }
            return null;
        }

        @Override
        public InputStream getMods(HttpClient client, String apiUrl, HarvestingItem item) throws IOException, InterruptedException {
            String modsUrl = String.format(apiUrl + (apiUrl.endsWith("/") ? "" : "/") + "items/%s/metadata/mods", item.getPid());
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(modsUrl))
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response != null && response.statusCode() == 200) {
                return response.body();
            } else {
                LOGGER.severe("HTTP error: " + (response != null ? response.statusCode() : "null"));
                return null;
            }
        }
    };

    public abstract String getIdentifier(Instance instance);

    public abstract String iterationQuery();

    public abstract String[] fieldList();

    public abstract String incrementFQ(Instance instance);

    public abstract InputStream getMods(HttpClient client, String apiUrl, HarvestingItem item) throws IOException, InterruptedException;

    public abstract HarvestingItem createHarvestItemFromResultElement(Element docResultElement, Instance instance);

    private static final DateTimeFormatter SOLR_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    public static final Logger LOGGER = Logger.getLogger(APIVersion.class.getName());
}
