package cz.inovatika.model;

import cz.inovatika.Constants;
import cz.inovatika.Constants.TitlesCollectionFields.ModelFields;
import cz.inovatika.utils.XMLUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Defines the supported API versions and encapsulates the version-specific logic
 * required for harvesting, particularly for constructing queries and parsing results
 * from different backend system versions (e.g., V5 vs. V7 of an institutional repository).
 *
 * <p>Each enum constant (V5, V7) implements the abstract methods to provide
 * version-specific identifiers, query components, result parsing, and
 * metadata fetching logic.
 */
public enum APIVersion {

    /**
     * Implementation for API Version 5.
     * Uses older field names like 'PID', 'fedora.model', 'modified_date', and a specific MODS URL structure.
     */
    v5 {
        @Override
        public String getIdentifier(Instance cloud) {
            return "PID";
        }

        @Override
        public String iterationQuery() {

            return "fedora.model:(monograph+OR+monographunit)";//+OR+periodical+OR+periodicalvolume)";//&fl=PID+pid_path+root_pid+fedora.model";
        }

        @Override
        public String[] fieldList() {
            return new String[]{"PID", "pid_path", "root_pid", "root_title", "fedora.model", "dc.title", "dnnt-labels", "dostupnost"};
        }

        @Override
        public String prepareSubquery(List<HarvestingItem> items) {
            List<HarvestingItem> monograph = getMonographs(items);
            if (!monograph.isEmpty()) {
                String or = monograph.stream().map(HarvestingItem::getPid).map(it -> '"' + it + '"').collect(Collectors.joining(" OR "));
                return String.format("search?q=root_pid:(%s)+AND+fedora.model:monographunit&fl=PID+fedora.model+root_pid&rows=%d", URLEncoder.encode(or, StandardCharsets.UTF_8), monograph.size());
            }
            return null;
        }


        @Override
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

            List<String> licenses = new ArrayList<>();
            Element dnntLabels = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("arr") && name.equals("dnnt-labels"));
            });
            if (dnntLabels != null) {
                List<String> elementsRecursive = XMLUtils.getElementsRecursive(dnntLabels, elm -> {
                    return elm.getNodeName().equals("str");
                }).stream().map(Element::getTextContent).toList();
                licenses.addAll(elementsRecursive);
            }

            Element dostupnost = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("dostupnost"));
            });
            if (dostupnost != null) {
                licenses.add(dostupnost.getTextContent().trim().equals("private") ? "onsite" : "public");
            }
            item.setLicenses(licenses.toArray(String[]::new));
            return item;
        }

        @Override
        public void processSubqueryResult(List<HarvestingItem> items, Element resultElm) {
            processSubqueryResult(items, resultElm, "PID", "root_pid");
        }


        @Override
        public String incrementFQ(Instance instance) {
            Instant lastUpdated = instance.getLastUpdated();
            if (lastUpdated != null) {
                String solrDateString = SOLR_DATE_FORMATTER.format(lastUpdated);
                return String.format("modified_date:[%s TO *]", solrDateString);
            }
            return null;
        }

        @Override
        public InputStream fetchMods(HttpClient client, Instance instance, HarvestingItem item) throws IOException, InterruptedException {
            String modsUrl = String.format(instance.getUrl() + (instance.getUrl().endsWith("/") ? "" : "/") + "item/%s/streams/BIBLIO_MODS", item.getPid());
            return sendRequest(client,modsUrl);
        }
    },

    /**
     * Implementation for API Version 7.
     * Uses newer field names like 'pid', 'model', 'indexed', and a revised MODS URL structure.
     */
    v7 {
        @Override
        public String getIdentifier(Instance instance) {
            return instance.isSolrCloud() ? "compositeId" : "pid";
        }

        @Override
        public String iterationQuery() {
            return "model:(monograph+OR+monographunit+OR+periodical+OR+periodicalvolume)";//&fl=pid+own_pid_path+root.pid+model";
        }

        @Override
        public String[] fieldList() {
            return new String[]{"pid", "own_pid_path", "root.pid","root.title" , "model", "titles.search", "licenses_of_ancestors", "licenses"};
        }


        @Override
        public String prepareSubquery(List<HarvestingItem> items) {
            List<HarvestingItem> monograph = getMonographs(items);
            if (!monograph.isEmpty()) {
                String or = monograph.stream().map(HarvestingItem::getPid).map(it -> '"' + it + '"').collect(Collectors.joining(" OR "));
                return String.format("search?q=root.pid:(%s)+AND+model:monographunit&fl=pid+model+root.pid&rows=%d", URLEncoder.encode(or, StandardCharsets.UTF_8), monograph.size());
            }
            return null;
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
                String mname = modelElm.getTextContent();
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


            Element rootTitleElm = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("str") && name.equals("root.title"));
            });

            if (rootTitleElm != null) {
                String titles = rootTitleElm.getTextContent();
                item.setRootTitle(titles);
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

            List<String> licenses = new ArrayList<>();
            Element dLicenses = XMLUtils.findElement(docResultElement, (elm) -> {
                String nodeName = elm.getNodeName();
                String name = elm.getAttribute("name");
                return (nodeName.equals("arr") && name.equals("licenses"));
            });
            if (dLicenses != null) {
                List<String> elementsRecursive = XMLUtils.getElementsRecursive(dLicenses, elm -> {
                    return elm.getNodeName().equals("str");
                }).stream().map(Element::getTextContent).toList();
                licenses.addAll(elementsRecursive);
            }

            Element aLicenses = XMLUtils.findElement(docResultElement, (elm) -> {
                String name = elm.getAttribute("name");
                return (elm.getNodeName().equals("arr") && name.equals("licenses_of_ancestors"));
            });
            if (aLicenses != null) {
                List<String> elementsRecursive = XMLUtils.getElementsRecursive(aLicenses, elm -> {
                    return elm.getNodeName().equals("str");
                }).stream().map(Element::getTextContent).toList();
                for (String l : elementsRecursive) {
                    if (!licenses.contains(l)) {
                        licenses.add(l);
                    }
                }
            }
            item.setLicenses(licenses.toArray(String[]::new));
            return item;
        }

        @Override
        public void processSubqueryResult(List<HarvestingItem> items, Element resultElm) {
            processSubqueryResult(items, resultElm, "pid", "root.pid");
        }

        @Override
        public String incrementFQ(Instance instance) {
            Instant lastUpdated = instance.getLastUpdated();
            if (lastUpdated != null) {
                String solrDateString = SOLR_DATE_FORMATTER.format(lastUpdated);
                return String.format("indexed:[%s TO *]", solrDateString);
            }
            return null;
        }

        @Override
        public InputStream fetchMods(HttpClient client, Instance instance, HarvestingItem item) throws IOException, InterruptedException {
            String url = instance.getUrl();
            String modsUrl = String.format(url + (url.endsWith("/") ? "" : "/") + "items/%s/metadata/mods", item.getPid());
            return sendRequest(client,modsUrl);
        }
    };
    public InputStream sendRequest(HttpClient client, String modsUrl) throws InterruptedException, IOException {
        int attempts = 0;
        while(attempts++ < 10) {
            try {

                HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(modsUrl))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofInputStream());

                if (response != null && response.statusCode() == 200) {
                    return response.body();

                } else {
                    LOGGER.log(Level.SEVERE, "HTTP error: {0}", response != null ? response.statusCode() : "null");
                    return null;
                }
            } catch (InterruptedException | ConnectException exception) {
                LOGGER.warning("Unable to send request, sleeping and trying again for: "+attempts+" time(s)"+ exception.getMessage());
                Thread.sleep(5000);
            }
        }
        throw new RuntimeException("Unable to send request"+attempts+" times");
    }

    static void processSubqueryResult(List<HarvestingItem> items, Element resultElm, String pidKey, String rootPidKey) {

        Map<String, HarvestingItem> map = new HashMap<>();
        items.forEach(item -> {
            map.put(item.getPid(), item);
        });

        List<Pair<String, String>> monographUnitPids = XMLUtils.getElementsRecursive(resultElm, (elm) -> {
            return elm.getNodeName().equals("doc");
        }).stream().map(docElm -> {
            Element pidElm = XMLUtils.findElement(docElm, elm -> elm.getAttribute("name").equals(pidKey));
            Element rootPid = XMLUtils.findElement(docElm, elm -> elm.getAttribute("name").equals(rootPidKey));
            if (pidElm != null && rootPid != null) {
                return Pair.of(rootPid.getTextContent(), pidElm.getTextContent());
            }
            return null;
        }).toList();

        // monographs
        monographUnitPids.forEach(k -> {
            try {
                if (k == null) {
                    //LOGGER.warning("monographUnitPid \"k\" is null");
                    //TODO figure out why this is null so often
                    return;
                }
                String rootPid = k.getKey();
                if (map.containsKey(rootPid)) {
                    map.get(rootPid).setHierarchical(true);
                }
            } catch (Exception e) {
                LOGGER.severe(e.getMessage());
                throw new RuntimeException(e);
            }
        });

        // periodicals
        items.stream().filter(harvestingItem -> harvestingItem.getModel().equals(ModelFields.PERIODICAL)).forEach(harvestingItem -> {
            harvestingItem.setHierarchical(true);
        });
    }

    private static List<HarvestingItem> getMonographs(List<HarvestingItem> items) {
        return items.stream().filter(item -> {
            return item.getModel().equals(ModelFields.MONOGRAPH);
        }).toList();
    }

    // --- Abstract Methods ---

    /**
     * Gets the name of the unique identifier field used in the Solr index for this API version.
     * This field is used for sorting and cursor-based pagination.
     *
     * @param instance The harvester instance configuration.
     * @return The identifier field name (e.g., "PID" or "pid", "compositeId").
     */
    public abstract String getIdentifier(Instance instance);

    /**
     * Gets the base query ({@code q} parameter) that defines the set of records to be harvested.
     *
     * @return The query string defining the base record types.
     */
    public abstract String iterationQuery();

    /**
     * Gets the list of fields ({@code fl} parameter) required to be returned in the Solr response
     * for creating the {@link HarvestingItem}.
     *
     * @return An array of field names.
     */
    public abstract String[] fieldList();

    /**
     * Creates the incremental filter query ({@code fq} parameter) based on the last update timestamp.
     * This filter ensures only records modified since the last harvest are retrieved.
     *
     * @param instance The harvester instance configuration containing the {@code lastUpdated} timestamp.
     * @return The filter query string (e.g., "indexed:[date TO NOW]"), or {@code null} if no timestamp is available.
     */
    public abstract String incrementFQ(Instance instance);

    /**
     * Fetches the full metadata (MODS stream) for a specific item using a version-appropriate URL.
     *
     * @param client   The {@link HttpClient} to use for the HTTP request.
     * @param instance The instance configuration for context (e.g., base URL).
     * @param item     The {@link HarvestingItem} containing the PID of the record to fetch.
     * @return An {@link InputStream} containing the MODS XML data, or {@code null} if the request failed (HTTP error).
     * @throws IOException          If an I/O error occurs during the request.
     * @throws InterruptedException If the operation is interrupted.
     */
    public abstract InputStream fetchMods(HttpClient client, Instance instance, HarvestingItem item) throws IOException, InterruptedException;

    /**
     * Parses a single Solr document element from the XML response into a {@link HarvestingItem} object.
     * The method must handle version-specific field names and XML structure.
     *
     * @param docResultElement The {@code <doc>} XML element from the Solr response.
     * @param instance         The harvester instance configuration for context.
     * @return A populated {@link HarvestingItem}.
     */
    public abstract HarvestingItem createHarvestItemFromResultElement(Element docResultElement, Instance instance);


    public abstract String prepareSubquery(List<HarvestingItem> items);

    public abstract void processSubqueryResult(List<HarvestingItem> items, Element resultElm);

    // --- Private/Static Helpers ---

    /**
     * DateTimeFormatter configured for Solr date format: ISO 8601 with millisecond precision and UTC zone ('Z').
     */
    private static final DateTimeFormatter SOLR_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    /**
     * Logger instance for logging errors and information specific to API version operations.
     */
    public static final Logger LOGGER = Logger.getLogger(APIVersion.class.getName());
}
