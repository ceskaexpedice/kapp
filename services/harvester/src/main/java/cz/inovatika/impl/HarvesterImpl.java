package cz.inovatika.impl;

import cz.inovatika.Constants;
import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Harvester;
import cz.inovatika.UpdateParentState;
import cz.inovatika.XmlProcessor;
import cz.inovatika.authors.AuthorData;
import cz.inovatika.authors.AuthorDataUtils;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.dates.DateRange;
import cz.inovatika.dates.DateRangeParser;
import cz.inovatika.identifiers.IdentifierUtils;
import cz.inovatika.model.*;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.touchstore.TouchStore;
import cz.inovatika.dates.normalization.utils.TitleNormalizationUtils;
import cz.inovatika.utils.XMLUtils;
import cz.inovatika.workflow.States;
import cz.inovatika.workflow.StringWrapper;
import cz.inovatika.workflow.Workflow;
import cz.inovatika.workflow.WorkflowDoc;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONObject;
import org.noggit.JSONUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static cz.inovatika.utils.CursorUtils.*;

public class HarvesterImpl implements Harvester {

    public static final Logger LOGGER = Logger.getLogger(HarvesterImpl.class.getName());

    private static final int BATCH_SIZE = 200;
    private static final int AUTHOR_BATCH_SIZE = 250;

    public static long REQUEST_COOLDOWN_MILIS = 0;

    private static final boolean IGNORE_CHECKTIME = true;
    public static final int NUMBER_OF_THREAD = 4;
    public static final String HTTP_CLIENT_THREAD_KEY = "nkp-http-client-thread-";

    final String user = "importer";
    public SolrClient solrClient = null;
    private HttpClient  httpClient = null;
    private ExecutorService  httpClientExecutorService = null;



    public String makeFQ(List<String> filters) {
        StringBuilder fq = new StringBuilder();
        for (String filter : filters) {
            if (filter == null || filter.isEmpty()) continue;
            if (!fq.isEmpty()) {
                fq.append(" AND ");
            }
            fq.append(filter);
        }
        return fq.toString();
    }

    @Override
    public void harvest(ApplicationConfig applicationConfig, Instance config, Consumer<HarvesterContext> consumer) {
        long startTime = System.currentTimeMillis();
        TouchStore touchStore = applicationConfig.touchStoreConfig().factory().create(solrClient, applicationConfig.touchStoreConfig());
        String cursorMark = null;
        String queryCursorMark = null;
        config.loadLastUpdatedFromPath();
        int processing = 0;
        int errorcount = 0;
        do {
            APIVersion apiVersion = config.getApiVersion();

            String query = pidCursorQuery(
                    apiVersion.iterationQuery(),
                    cursorMark,
                    config.getROWS(),
                    makeFQ(Collections.singletonList(//Arrays.asList(
                            apiVersion.incrementFQ(config)/*,
                            physicalLocationToFQ(config.getPhysicalLocations())*/
                    )),
                    "search",
                    apiVersion.getIdentifier(config),
                    apiVersion.getIdentifier(config) + " asc",
                    apiVersion.fieldList()
            );
            LOGGER.log(Level.INFO, "query: {0}", query);
            Element element;
            try {
                element = executeQuery(this.httpClient, config.getUrl(), query);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "IOException occurred when reading Solr response: ", e);
                errorcount++;
                continue;
            }
            //LOGGER.log(Level.INFO, "query executed");
            List<HarvestingItem> harvestItems = XMLUtils.getElementsRecursive(element, (elm) -> elm.getNodeName().equals("doc")).stream().map(docElm
                    -> apiVersion.createHarvestItemFromResultElement(docElm, config)
            ).toList();

            int numberOfResults = 0;
            Element resultElm = XMLUtils.findElement(element, (elm) -> elm.getNodeName().equals("result"));
            if (resultElm != null) {
                numberOfResults = Integer.parseInt(resultElm.getAttribute("numFound"));
            }

            if (harvestItems.isEmpty()) {
                LOGGER.log(Level.INFO, "Ran out of items to harvest");
            } else {
                LOGGER.log(Level.INFO, "Items to harvest: {0}", harvestItems.size());
                String subquery = apiVersion.prepareSubquery(harvestItems);
                if (subquery != null && !subquery.isEmpty()) {
                    try{
                        Element subResult = executeQuery(this.httpClient, config.getUrl(), subquery);
                        apiVersion.processSubqueryResult(harvestItems, subResult);}
                    catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "IOException occurred when reading Solr subquery response: ", e);
                    }
                }
                for (HarvestingItem harvestingItem : harvestItems) {
                    consumer.accept(new HarvesterContext(applicationConfig, harvestingItem, touchStore, this.httpClient, this.solrClient, config, startTime,numberOfResults, processing++));
                }
            }
            cursorMark = findCursorMark(element);
            queryCursorMark = findQueryCursorMark(element);
            errorcount = 0;

        } while ((cursorMark != null && queryCursorMark != null) && !cursorMark.equals(queryCursorMark) && errorcount <5 );

        //updateHierarchical();

        config.saveLastUpdatedToPath();
        LOGGER.log(Level.INFO, String.format("Harvesting completed. It took %d", (System.currentTimeMillis() - startTime)));
    }
/*
    private void updateHierarchical() throws SolrServerException, IOException {
        List<String> hierarchicalPids = new ArrayList<>();
        SolrQuery q = new SolrQuery()
                .setQuery("is_hierarchical:true")
                .setFields("pid")
                .setRows(10000);
        for (SolrDocument doc : this.solrClient.query("titles", q).getResults()) {
            Object pid = doc.getFieldValue("pid");
            hierarchicalPids.add(pid.toString());
        }
    }*/




    public static Element executeQuery(HttpClient httpClient, String url, String query) throws IOException {
        try {
            StringReader t = new StringReader(executeSolrQuery(httpClient, url, query + "&wt=xml"));
            Document doc = XMLUtils.parseDocument(t);
            return doc.getDocumentElement();
        } catch (SAXException e) {
            LOGGER.log(Level.SEVERE, "Error parsing Solr response: ", e);
            LOGGER.log(Level.INFO, "Query:{0}", url + "/" + query);
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new IOException(e);
        } catch (ParserConfigurationException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error executing Solr query", e);
            LOGGER.log(Level.INFO, "Error executing query: {0}", url + "/" + query);
            throw new RuntimeException(e);
        }
    }

    private static String executeSolrQuery(HttpClient client, String url, String query)
            throws IOException, InterruptedException {

        int attempt = 0;
        HttpResponse<InputStream> response;
        while (attempt++ < 10) {
            response = client.send(HttpRequest.newBuilder()
                            .uri(URI.create(url + "/" + query))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response != null && response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    return IOUtils.toString(is, StandardCharsets.UTF_8);
                }
            } else {
                if (response != null) {
                    try (InputStream is = response.body()) {
                        if (is != null) is.transferTo(OutputStream.nullOutputStream());
                    }
                    LOGGER.log(Level.WARNING, "Response received with status:{0}", response.statusCode());
                } else {
                    LOGGER.log(Level.WARNING, "Response not received");
                }

                LOGGER.log(Level.INFO, "Going to try again. Attempt {0}", attempt);
                Thread.sleep(1000);
            }
        }
        throw new RuntimeException("HTTP error: Could not connect to Solr server");
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    static final int INTERVALMINUTES = 60;
    static final LocalTime start = LocalTime.now();
    static final LocalTime end = start.plusMinutes(INTERVALMINUTES);
    static final long SLEEPSECONDS = 3600;
    static final boolean EXITONOUTOFINTERVAL = false;

    public static void main(String[] args) {
//        if (args.length > 0) {
//            String timeRange = args[0];
//            SLEEPSECONDS = args.length > 1 ? Long.parseLong(args[1]) : 60;
//
//            String[] parts = timeRange.split("-");
//            if (parts.length != 2) {
//                System.err.println("Invalid time range. Use format HH:mm-HH:mm");
//                System.exit(1);
//            }
//
//            start = LocalTime.parse(parts[0], TIME_FMT);
//            end = LocalTime.parse(parts[1], TIME_FMT);
//            exitOnOutOfInterval = false;
//        }
//        try {
//            if (!IGNORE_CHECKTIME) checkTime();
//        } catch (IOException | InterruptedException e) {
//            LOGGER.log(Level.SEVERE, e.getMessage(), e);
//        }
        HarvesterImpl harvester = new HarvesterImpl();
        try {
            InputStream resourceAsStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
            ApplicationConfig applicationConfig = ApplicationConfig.loadFromStream(resourceAsStream);
            harvester.start(applicationConfig);
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Failed to load configuration", e);
            throw new RuntimeException(e);
        }catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Harvesting problem", e);
            throw new RuntimeException(e);
        } finally {
            harvester.closeClients();
        }
    }

    private static void checkTime()
            throws InterruptedException {
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
            if (inRange) return;
            if (EXITONOUTOFINTERVAL) {
                LOGGER.info("Not in interval, exiting. (EXITONOUTOFINTERVAL==true)");
                System.exit(0);
            } else {
                LOGGER.log(Level.INFO, "Outside allowed time ({0}). Sleeping...", now.format(TIME_FMT));
                Thread.sleep(SLEEPSECONDS * 1000);
            }

        }

    }

    @Override
    public void start(ApplicationConfig applicationConfig) {
        if (solrClient == null) {
            this.solrClient = buildSolrClient(applicationConfig);
        }
        if (httpClient == null) {
            this.httpClientExecutorService =  Executors.newFixedThreadPool(
                    NUMBER_OF_THREAD,
                    r -> {
                        Thread t = new Thread(r);
                        t.setName(HTTP_CLIENT_THREAD_KEY + t.threadId());
                        return t;
                    }
            );

            this.httpClient  = buildHTTPClient(this.httpClientExecutorService);
        }

        List<Instance> instances = applicationConfig.instances();
        for (Instance i : instances) {
            i.setTimestampPath(String.join(File.separator, System.getProperty("user.home"), ".kapp", "timestamps", i.getAcronym(), "timestamp"));
            try {
                LOGGER.log(Level.INFO, "Starting: {0}", i.getAcronym());
                REQUEST_COOLDOWN_MILIS = 0;
                harvestAndBatch(applicationConfig, i);
            } catch (final IOException | SolrServerException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void harvestAndBatch(ApplicationConfig applicationConfig, Instance config) throws SolrServerException, IOException {
        LOGGER.log(Level.INFO, "Harvesting started, BatchSize: {0}", BATCH_SIZE);
        List<SolrInputDocument> createBuffer = new ArrayList<>(BATCH_SIZE);
        List<SolrInputDocument> updateBuffer = new ArrayList<>(BATCH_SIZE);

        ArrayList<SolrInputDocument> authorBuffer = new ArrayList<>(AUTHOR_BATCH_SIZE + 20);
        harvest(applicationConfig, config, ctx -> processHarvest(config, ctx, createBuffer, updateBuffer, authorBuffer));

        if (!createBuffer.isEmpty()) { // BATCH_SIZE
            LOGGER.log(Level.INFO, "Sending batch");
            sendBatch(Constants.Collection.TITLES_COLLECTION, createBuffer);
            createBuffer.clear();
        }

        if (!updateBuffer.isEmpty()) { // BATCH_SIZE
            LOGGER.log(Level.INFO, "Sending batch");
            sendBatch(Constants.Collection.TITLES_COLLECTION, updateBuffer);
            updateBuffer.clear();
        }
        if (!authorBuffer.isEmpty()) { // BATCH_SIZE
            LOGGER.log(Level.INFO, "Sending author_batch");
            sendBatch(Constants.Collection.AUTHORS_COLLECTION, authorBuffer);
            authorBuffer.clear();
        }

        commitCollection(Constants.Collection.TITLES_COLLECTION);
        commitCollection(Constants.Collection.AUTHORS_COLLECTION);

        LOGGER.log(Level.INFO, "Updating state");
        UpdateParentState updateParentState = new UpdateParentStateImpl(this.solrClient);
        updateParentState.updateParentState();
        commitCollection(Constants.Collection.TITLES_COLLECTION);

    }

    private void sendBatch(String collection, final List<SolrInputDocument> docs) {
        if (!docs.isEmpty()) {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.add(docs);
            Exception lastException = null;
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    UpdateResponse response = updateRequest.process(this.solrClient, collection);
                    if (response.getStatus() != 0)
                        LOGGER.log(Level.WARNING, "Solr update of {"+collection+"} returned non-zero status: {0}, QTime: {1}",
                                new Object[]{response.getStatus(), response.getQTime()});
                    LOGGER.log(Level.INFO, "Indexed batch of {0} documents into: "+collection+".", docs.size());

                    break;
                } catch (IOException | SolrServerException e) {
                    lastException = e;
                    LOGGER.log(Level.WARNING, String.format("Failed to send batch to %s. Retrying: %d/10 times.", collection, attempt + 1), e);
                } catch(Exception e) {
                    LOGGER.log(Level.SEVERE, e.getMessage(),e);
                    //for (SolrInputDocument doc : docs) { LOGGER.log(Level.SEVERE, "DOC: {0}", doc.get("pid")); }
                    throw e;
                }
                if (attempt == 9) {
                    LOGGER.log(Level.SEVERE, "Failed to send batch. Retrying also failed.");
                }
            }
        } else {
            LOGGER.log(Level.INFO, "No documents to be indexed.");
        }
    }

    private void commitCollection(String collection) throws SolrServerException, IOException {
        LOGGER.log(Level.INFO, "Committing collection: {0}", collection);
        this.solrClient.commit(collection);
    }
/*
    private static Map<String, Object> createSetOperation(Object value) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("set", value);
        return updateMap;
    }*/

    private void processHarvest(final Instance config,
                                final HarvesterContext ctx,
                                List<SolrInputDocument> createBuffer,
                                List<SolrInputDocument> updateBuffer,
                                List<SolrInputDocument> authorsBuffer) {
        {

            if (ctx.processedSoFar() % 1000 == 0) {
                long took = System.currentTimeMillis() - ctx.harvestStartTimestamp();
                LOGGER.log(Level.INFO, String.format("Total number %d, processed SoFar: %d and took %d ms", ctx.numItems(), ctx.processedSoFar(), took));
            }

            String pid = ctx.item().getPid();
            if (ctx.touchStore().exists(pid)) {
                SolrInputDocumentWrapper wrapper = getSolrInputDocumentWrapper(ctx,SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);

                wrapper.addField(AppComputedFields.DIGITAL_LIBRARY, ctx.config().getAcronym(), SolrInputDocumentWrapper.TypeOfOperation.ATOMIC_ADD_DISTINCT);

                List<String> cPhysicalLocations = config.getPhysicalLocations();
                try {
                    List<String> touchStorePhysicalLocations = ctx.touchStore().getPhysicalLocations(pid);
                    if (touchStorePhysicalLocations.isEmpty()) {
                        String rootPid = ctx.touchStore().getRootPid(pid);
                        if (rootPid != null) {
                            // loading physical locations from root.pid
                            touchStorePhysicalLocations = ctx.touchStore().getPhysicalLocations(rootPid);
                        }
                    }

                    boolean acceptLibrary = touchStorePhysicalLocations == null || touchStorePhysicalLocations.isEmpty();
                    if (touchStorePhysicalLocations != null) {
                        for (String prevSigla : touchStorePhysicalLocations) {
                            if (cPhysicalLocations.contains(prevSigla)) {
                                acceptLibrary = true;
                                break;
                            }
                        }
                    }

                    if (acceptLibrary) {
                        BiblioModsEntity entity = getBiblioModsEntity(config, ctx);
                        if (entity == null) return;
                        if (ctx.touchStore().isModsChanged(pid, entity.md5Sum())) {
                            try {
                                Document parsed = XMLUtils.parseDocument(new StringReader(entity.biblioMods()), true);
                                WorkflowDoc workflowDoc = buildWorkflowDoc(config,pid,ctx,wrapper, entity, parsed,authorsBuffer);

                                // state differs
                                JSONObject oldDoc = getExistingDocument((String) wrapper.document().getFieldValue(AppDataFields.PID_FIELD));
                                if (oldDoc != null && oldDoc.has("state")) {
                                    String oldState = oldDoc.getString("state");
                                    StringWrapper reason = new StringWrapper("unspecified");
                                    States newState = Workflow.calculateAutomaticTransition(ctx, workflowDoc,reason);
                                    String message = String.format("Imported from '%s'"+" + "+reason, config.getUrl());
                                    if (oldState != null && !oldState.equals(newState.name())) {
                                        Workflow.executeTransition( workflowDoc, user, message, wrapper,newState,ctx.applicationConfig().workflow());
                                    }
                                }
                            } catch (ParserConfigurationException | SAXException e) {
                                LOGGER.severe(e.getMessage());
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    addToBufferAndSend(updateBuffer, authorsBuffer, wrapper);
                } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
                    LOGGER.severe(e.getMessage());
                    throw new RuntimeException(e);
                }

            } else {

                //Stopwatch sw = Stopwatch.createStarted();
                SolrInputDocumentWrapper doc = getSolrInputDocumentWrapper(ctx,SolrInputDocumentWrapper.TypeOfChange.SIMPLE);
                doc.addField(AppComputedFields.DIGITAL_LIBRARY, ctx.config().getAcronym());

                try {
                    BiblioModsEntity entity = getBiblioModsEntity(config, ctx);
                    if (entity == null) return;
                    Document parsed = XMLUtils.parseDocument(new StringReader(entity.biblioMods()), true);
                    if (parsed == null) {
                        LOGGER.log(Level.SEVERE, "Failed to process MODS for PID: {0}", ctx.item().getPid());
                        return;
                    }
                    WorkflowDoc workflowDoc = buildWorkflowDoc(config,pid,ctx,doc, entity, parsed,authorsBuffer);
                    StringWrapper reason = new StringWrapper("unspecified");
                    States newState = Workflow.calculateAutomaticTransition(ctx, workflowDoc,reason);
                    Workflow.executeTransition(
                            workflowDoc,
                            user,
                            String.format("Imported from '%s'" + " + "+reason, config.getUrl()),
                            doc,
                            newState,
                            ctx.applicationConfig().workflow()
                    );

                    addToBufferAndSend(createBuffer, authorsBuffer, doc);

                } catch (final IOException | InterruptedException | NoSuchAlgorithmException |
                               ParserConfigurationException | SAXException e) {
                    LOGGER.severe(e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private static BiblioModsEntity getBiblioModsEntity(Instance config, HarvesterContext ctx) throws IOException, InterruptedException, NoSuchAlgorithmException {
        InputStream inputStream = config.getApiVersion().fetchMods(ctx.client(), ctx.config(), ctx.item());
        if (inputStream == null) {
            LOGGER.log(Level.SEVERE, "Failed to get MODS for PID: {0}", ctx.item().getPid());
            return null;
        }
        return BiblioModsEntity.create(inputStream);
    }

    private static SolrInputDocumentWrapper getSolrInputDocumentWrapper(HarvesterContext ctx,SolrInputDocumentWrapper.TypeOfChange typeOfChange) {
        SolrInputDocumentWrapper doc = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                typeOfChange
                );

        doc.setField(AppDataFields.PID_FIELD, ctx.item().getPid());
        if (ctx.item().getLicenses() != null && ctx.item().getLicenses().length > 0) {
            List<String> licenses = Arrays.asList(ctx.item().getLicenses());
            doc.addField(AppDataFields.LICENSES_FIELD, licenses);
            doc.addField(String.format("%s%s", AppDataFields.LICENSES_DL_FIELD, ctx.config().getAcronym()), licenses);
        }
        doc.setField(AppComputedFields.IS_HIERARCHICAL_FIELD, ctx.item().isHierarchical());
        
        return doc;
    }

    private WorkflowDoc buildWorkflowDoc(Instance config,String pid,HarvesterContext ctx,SolrInputDocumentWrapper wrapper, BiblioModsEntity entity, Document parsed,List<SolrInputDocument> authorsBuffer) throws IOException {
        HarvestingItem hitem = ctx.item();
        wrapper.setField(AppDataFields.MODEL_FIELD, hitem.getModel());
        if (hitem.getRootTitle() != null) {
            wrapper.setField(AppDataFields.ROOT_TITLE_FIELD, hitem.getRootTitle());
        }
        wrapper.setField(AppDataFields.ROOT_PID_FIELD, hitem.getRootPid());
        wrapper.setField(AppDataFields.TITLE_FIELD, hitem.getTitles());
        wrapper.setField(AppDataFields.BIBLIO_MODS_FIELD, entity.biblioMods());
        wrapper.setField(AppDataFields.BIBLIO_MODS_MD_5_FIELD, entity.md5Sum());

        parseDateRange(wrapper, parsed);
        List<String> physicalLocation = XMLUtils.getElementsRecursive(parsed.getDocumentElement(), elm -> elm.getLocalName().equals("physicalLocation")).stream().map(Element::getTextContent).toList();
        if (!physicalLocation.isEmpty()) {
            wrapper.addField(AppDataFields.PHYSICAL_LOCATIONS_FACET_FIELD, physicalLocation);
        }
        // store to touch store
        storeToTouchStore(config, ctx, pid, entity, physicalLocation);

        IdentifierUtils.findIdentifiers(parsed).forEach((k, v) -> wrapper.setField(String.format("id_%s", k), v));
        // Authors
        processAuthors(wrapper, authorsBuffer, parsed);
        Integer dateRangeEnd = (Integer) wrapper.getFieldValue(AppComputedFields.DATE_ISSUED_YEAR);
        Integer last_year_death = (Integer) wrapper.getFieldValue(AppComputedFields.LAST_YEAR_DEATH);

        return new WorkflowDoc(
                dateRangeEnd,
                last_year_death,
                States.IMPORTED,
                user,
                ctx.item().getModel(),
                ctx.item().isHierarchical()//, States.IMPORTED.getLicensesAssignation()
        );
    }

    private void storeToTouchStore(Instance config, HarvesterContext ctx, String pid, BiblioModsEntity entity, List<String> physicalLocation) throws IOException {
        ctx.touchStore().touch(pid, entity.md5Sum(), config.getAcronym(), ctx.item().getRootPid(), physicalLocation);
        ctx.touchStore().storeMods(pid, entity.biblioMods().trim());
    }

    private void processAuthors(SolrInputDocumentWrapper doc, List<SolrInputDocument> authorsBuffer, Document parsed) {
        List<AuthorData> authorData = AuthorDataUtils.parseAuthorData(parsed);
        if (!authorData.isEmpty()) {
            doc.addField(AppDataFields.AUTHORS_FIELD, authorData.stream().map(AuthorData::getFull).toList());
        }

        List<String> authorsKrameriusList = new ArrayList<>();
        List<String> nameIdentifiers = new ArrayList<>();
        List<String> authorIds = new ArrayList<>();

        authorData.forEach(author -> {
            authorsKrameriusList.add(author.toJsonString());

            if (author.getNameIdentifier() != null && !author.getNameIdentifier().isEmpty()) {
                nameIdentifiers.add(author.getNameIdentifier());
                authorIds.add(author.getNameIdentifier());
            } else {
                String name = author.getFull() + (author.getDate() != null ? author.getDate() : "") ;
                authorIds.add(XmlProcessor.normalize(name));
            }
        });
        if (!authorsKrameriusList.isEmpty()) {
            doc.addField(AppDataFields.AUTHORS_KRAMERIUS_FIELD, authorsKrameriusList);
        }
        if (!nameIdentifiers.isEmpty()) {
            doc.addField(AppDataFields.NAME_IDENTIFIER_FIELD, nameIdentifiers);
        }
        if (!authorIds.isEmpty()) {
            doc.addField(AppDataFields.AUTHORS_IDS, authorIds);
        }

        List<SolrInputDocument> authorityDocuments = authorData.stream().map(AuthorData::toAuthoritySolrDocument).toList();
        authorsBuffer.addAll(authorityDocuments);
    }

    private  void addToBufferAndSend(List<SolrInputDocument> buffer, List<SolrInputDocument> authorsBuffer, SolrInputDocumentWrapper wrapperDoc) {
        buffer.add(wrapperDoc.getDocument());
        if (buffer.size() >= BATCH_SIZE) { // BATCH_SIZE
            sendBatch(Constants.Collection.TITLES_COLLECTION, buffer);
            buffer.clear();
            try {
                if (!IGNORE_CHECKTIME) checkTime();
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
        }
        if (authorsBuffer.size() >= BATCH_SIZE) { // BATCH_SIZE
            sendBatch(Constants.Collection.AUTHORS_COLLECTION, authorsBuffer);
            authorsBuffer.clear();
        }
    }

    static void parseDateRange(SolrInputDocumentWrapper wrapper, Document parsed) {
        Element dateIssued = XMLUtils.findElement(parsed.getDocumentElement(), elm -> elm.getLocalName().equals("dateIssued"));
        if (dateIssued != null) {

            String dateText = dateIssued.getTextContent();
            wrapper.setField(AppDataFields.DATE_ISSUED_STR, dateText);

            try {
                List<String> labels = new ArrayList<>();
                String normalizedDateText = XmlProcessor.normalizeImpreciseIssuedDateHint(dateText, labels);
                if (labels.isEmpty()) {
                    normalizedDateText = TitleNormalizationUtils.normalize(dateText);
                } else {
                    labels.forEach(label -> wrapper.addField(AppComputedFields.LABELS_FIELD, label));
                }

                DateRange parsedDateRange = new DateRangeParser().parse(normalizedDateText);
                if (parsedDateRange != null) {
                    parsedDateRange.mapToSolrDocument(wrapper);
                }
                else{
                    LOGGER.warning("Failed to parse date range for dateIssued");
                    LOGGER.warning(dateText);
                    LOGGER.warning(wrapper.getIdKey());
                }
            } catch (NumberFormatException e) {
                LOGGER.warning(String.format( "Failed to parse date range for dateIssued. Document %s", wrapper.getIdKey()));
                LOGGER.warning(() -> "Raw value"+ dateText);
                LOGGER.warning(() -> "Normalized string :"+TitleNormalizationUtils.normalize(dateText));
            }
        }
    }

/*
    private static void storeState(Instance config, SolrInputDocument idoc, JSONObject oldDoc, States nState) {

        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        JSONObject h = new JSONObject()
                .put("date", d)
                .put("reason", "import from " + config.getUrl())
                .put("user", "importer")
                .put("state", nState.toString());

        idoc.setField(AppComputedFields.STATE_FIELD, nState.toString());
        idoc.setField(AppComputedFields.STATE_DATE_FIELD, d);
        idoc.setField(AppComputedFields.STATE_USER_FIELD, "importer");
        idoc.setField(AppComputedFields.STATE_REASON_FIELD, "import from " + config.getUrl());

        if (oldDoc != null) {

            boolean historyExists = oldDoc.has(AppComputedFields.STATE_HISTORY_FIELD);
            if (historyExists) {
                Map<String, Object> partialUpdate = new HashMap<>();
                partialUpdate.put("add", h.toString());
                idoc.addField(AppComputedFields.STATE_HISTORY_FIELD, partialUpdate);
            } else {
                idoc.addField(AppComputedFields.STATE_HISTORY_FIELD, h.toString());
            }
        } else {
            idoc.addField(AppComputedFields.STATE_HISTORY_FIELD, h.toString());
        }

    }
*/
    private static Http2SolrClient buildSolrClient(ApplicationConfig applicationConfig) {
        return new Http2SolrClient.Builder(applicationConfig.solr().getUrl())
                .withConnectionTimeout(200, TimeUnit.SECONDS)
                .withIdleTimeout(5, TimeUnit.MINUTES)
                .withRequestTimeout(5, TimeUnit.MINUTES).useHttp1_1(true)
                .build();
    }


    private static HttpClient buildHTTPClient(ExecutorService executor) {
        return HttpClient.newBuilder()
                .executor(executor)
                //.version(HttpClient.Version.HTTP_1_1)
                //.connectTimeout(Duration.ofMinutes(1))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private JSONObject getExistingDocument(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery(String.format("%s:\"%s\"", AppDataFields.PID_FIELD, pid))
                .setFields(AppComputedFields.STATE_HISTORY_FIELD)
                .setRows(1);
        try {
            SolrDocumentList docs = this.solrClient.query(Constants.Collection.TITLES_COLLECTION, q).getResults();
            return docs.getNumFound() > 0 ? new JSONObject(JSONUtil.toJSON(docs.getFirst())) : null;
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return null;
        }
    }

//    private static void elapseAndReset(Stopwatch sw) {
//        sw.stop();
//        LOGGER.log(Level.INFO, "{0} ms", sw.elapsed(TimeUnit.MILLISECONDS));
//        sw.reset().start();
//    }


    @Override
    public void closeClients() {
        if (solrClient != null)         {
            try {
                solrClient.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to close solr client", e);
            }
        }
        if (httpClient != null){
            try {
                httpClient.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to close http client", e);
            }
        }
        if (this.httpClientExecutorService != null) {
            this.httpClientExecutorService.shutdown();
        }
    }
}
