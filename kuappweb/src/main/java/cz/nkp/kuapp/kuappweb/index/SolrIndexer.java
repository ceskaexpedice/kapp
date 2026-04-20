package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alber
 */
public class SolrIndexer {

    public static final Logger LOGGER = Logger.getLogger(SolrIndexer.class.getName());

    private static void addAtomicField(SolrInputDocument doc, String modifier, Object value, String field) {
        Map<String, Object> partialUpdate = new HashMap<>();
        partialUpdate.put(modifier, value);
        doc.setField(field, partialUpdate);
    }

    public static JSONObject saveConfig(JSONArray js, String id) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("id", id);
            doc.setField("value", js.toString());
            solr.add("configuration", doc);
            solr.commit("configuration");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject indexDK(JSONArray js) {

        JSONObject ret = saveConfig(js, "digitalni_knihovna");
        return ret;
    }

    public static JSONObject indexLicenses(JSONArray js) {

        JSONObject ret = saveConfig(js, "licenses");
        return ret;
    }

    public static JSONObject indexLabels(JSONArray js) {

        JSONObject ret = saveConfig(js, "labels");
        return ret;
    }

    public static JSONObject indexTitleColumns(JSONArray js) {

        JSONObject ret = saveConfig(js, "titleColumns");
        return ret;
    }

    public static JSONObject indexDocLabels(JSONObject js, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            addAtomicField(doc, "set", js.getJSONArray("labels"), "labels");
            addAtomicField(doc, "add-distinct", user, "modified_by_user");
            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject indexAuthorLabels(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("id", js.getString("id"));

            addAtomicField(doc, "set", js.getJSONArray("labels"), "labels");
            solr.add("authors", doc);
            solr.commit("authors");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject indexBatches(JSONArray js) {

        JSONObject ret = saveConfig(js, "batches");
        return ret;
    }

    public static JSONObject indexDocBatches(JSONObject js, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JSONArray pids = js.getJSONArray("pids");
            for (int i = 0; i < pids.length(); i++) {
                SolrInputDocument doc = new SolrInputDocument();
                doc.setField("pid", pids.getString(i));
                addAtomicField(doc, "set", js.getString("batch"), "batch");
                addAtomicField(doc, "add-distinct", user, "modified_by_user");
                solr.add("titles", doc);
            }

            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject indexDocContracts(JSONObject js, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JSONArray pids = js.getJSONArray("pids");
            for (int i = 0; i < pids.length(); i++) {
                SolrInputDocument doc = new SolrInputDocument();
                doc.setField("pid", pids.getString(i));

                String modifier = js.optBoolean("selected") ? "set" : "remove";
                addAtomicField(doc, modifier, js.getString("contract"), "contracts");

                if (js.optBoolean("withState")) {
                    String state = "KONTROLOVANE_NEVEREJNE_LICENCE";
                    fillState(doc, state, js.getJSONArray("licenses"), "Smlouva " + js.getString("contract"), user);
                }

                solr.add("titles", doc);
            }
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject saveLastYearDeath(JSONObject js, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            addAtomicField(doc, "set", js.getInt("lastYearDeath"), "last_year_death");
            addAtomicField(doc, "add-distinct", user, "modified_by_user");

            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject saveTitleNote(JSONObject js, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            addAtomicField(doc, "set", js.getString("note"), "note");
            addAtomicField(doc, "add-distinct", user, "modified_by_user");

            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    // Save changes to author in authors core
    // Add / modify authors_kapp field in titles core
    public static JSONObject saveAuthor(JSONObject js, boolean onlyAuthors, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {

            //Save to authors
            /**
             * <field name="id" type="string" multiValued="false" indexed="true" required="true" stored="true"/>
             * <field name="family" type="string" multiValued="false" indexed="true" stored="true"/>
             * <field name="full" type="string" multiValued="false" indexed="true" stored="true"/>
             * <field name="full_text" type="text_general_lemmatized" multiValued="true" indexed="true" stored="false"/>
             * <field name="given" type="string" multiValued="false" indexed="true" stored="true"/>
             * <field name="labels" type="text_general"/>
             * <field name="nameIdentifier" type="string" multiValued="false" indexed="true" stored="true"/>
             * <field name="termsOfAddress" type="string" multiValued="false" indexed="true" stored="true"/>
             *
             * <field name="confirmed_end_year" type="boolean" uninvertible="true" indexed="true" stored="true"/>
             * <field name="confirmed_start_year" type="boolean" uninvertible="true" indexed="true" stored="true"/>
             *
             * <field name="date" type="string" multiValued="false" indexed="true" stored="true"/>
             * <field name="date_birth" type="string" multiValued="false" indexed="true" stored="true"/>
             * <field name="date_death" type="string" multiValued="false" indexed="true" stored="true"/>
             *
             * <field name="year_birth" type="pint" multiValued="false" indexed="true" stored="true"/>
             * <field name="year_death" type="pint" multiValued="false" indexed="true" stored="true"/>
             *
             *
             */
            SolrInputDocument adoc = new SolrInputDocument();
            adoc.setField("id", js.getString("id"));
            adoc.setField("old_id", js.optString("old_id"));
            adoc.setField("nameIdentifier", js.optString("nameIdentifier"));
            adoc.setField("full", js.optString("full"));
            adoc.setField("family", js.optString("family"));
            adoc.setField("given", js.optString("given"));
            adoc.setField("labels", js.optJSONArray("labels"));
            adoc.setField("termsOfAddress", js.optString("termsOfAddress"));
            adoc.setField("date", js.optString("date"));
            adoc.setField("date_birth", js.optString("date_birth"));
            adoc.setField("date_death", js.optString("date_death"));
            if (js.has("year_birth")) {
                adoc.setField("year_birth", js.optInt("year_birth"));
            }
            if (js.has("year_death")) {
                adoc.setField("year_death", js.optInt("year_death"));
            }
            adoc.setField("confirmed_start_year", js.optBoolean("confirmed_start_year"));
            adoc.setField("confirmed_end_year", js.optBoolean("confirmed_end_year"));
            
            adoc.setField("modified_by_user", user);
            
            // addAtomicField(doc, "add-distinct", user, "modified_by_user");
            
            solr.add("authors", adoc);

            if (!onlyAuthors) {
                // save all titles
                String old_id = js.optString("old_id", null);
                boolean idChanged = old_id != null && !js.optString("old_id").equals(js.getString("id"));
                saveAuthorsInAllTitles(solr, old_id == null ? js.getString("id") : old_id, idChanged, js, user);
                // Remove author if id changed
                if (idChanged) {
                    solr.deleteById("authors", js.optString("old_id"));
                }
            }

            solr.commit("authors");

            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    private static void saveAuthorsInAllTitles(SolrClient solr, String old_id, boolean idChanged, JSONObject js, String user) throws SolrServerException, IOException {
        String q = "*";
        JsonQueryRequest jrequest = new JsonQueryRequest()
                .setQuery(q)
                .withFilter("authors_ids:(\"" + old_id + "\")")
                .returnFields("pid,authors_ids,nameIdentifier,authors_kapp:[json],authors_kramerius:[json]")
                //.returnFields("pid,authors_ids,nameIdentifier,authors_kramerius:[json]")
                .setLimit(1000);
        jrequest.setResponseParser(new InputStreamResponseParser("json"));

        NamedList<Object> resp = solr.request(jrequest, "titles");
        InputStream is = (InputStream) resp.get("stream");
        String r = IOUtils.toString(is, "UTF-8");
        JSONArray docs = (new JSONObject(r).getJSONObject("response")).getJSONArray("docs");
        List<SolrInputDocument> tdocs = new ArrayList();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            tdocs.add(saveAuthorInTitle(doc.getString("pid"), js, doc.optJSONArray("authors_kapp", new JSONArray()), doc.optJSONArray("authors_kramerius", new JSONArray()), idChanged, user));
            //saveAuthorInTitle(solr, doc.getString("pid"), js, new JSONArray());
        }
        solr.add("titles", tdocs);
        solr.commit("titles");

    }

    private static SolrInputDocument saveAuthorInTitle(String pid, JSONObject new_author, JSONArray authors_kapp, JSONArray authors_kramerius, boolean idChanged, String user) throws SolrServerException, IOException {

        SolrInputDocument tdoc = new SolrInputDocument();
        tdoc.setField("pid", pid);
        String id = new_author.getString("id");
        String old_id = new_author.optString("old_id");
        JSONObject new_author_title = new JSONObject(new_author.toString());

        int idx = -1;
        // addAtomicField(tdoc, "set", null, "authors_kapp");

        // Find author in kramerius to keep role and isLast
        for (int i = 0; i < authors_kramerius.length(); i++) {
            JSONObject doc = authors_kramerius.getJSONObject(i);
            if (id.equals(doc.optString("id"))
                    || id.equals(doc.optString("nameIdentifier"))
                    || old_id.equals(doc.optString("id"))) {
                new_author_title.put("role", doc.optJSONArray("role"));
                new_author_title.put("isLast", doc.optBoolean("isLast"));
                break;
            }
        }

        for (int i = 0; i < authors_kapp.length(); i++) {
            JSONObject doc = authors_kapp.getJSONObject(i);
            if (id.equals(doc.getString("id"))) {
                idx = i;
                break;
            }
        }

        if (idx > -1) {
            authors_kapp.put(idx, new_author_title);
        } else {
            authors_kapp.put(new_author_title);
        }

        addAtomicField(tdoc, "set", authors_kapp.toString(), "authors_kapp");

        // Remove old_id, add new
        // addAtomicField(tdoc, "add-distinct", id, "authors_ids");
        if (idChanged) {
            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("remove", old_id);
            partialUpdate.put("add-distinct", id);
            tdoc.setField("authors_ids", partialUpdate);
        }

        if (new_author.has("nameIdentifier")) {
            addAtomicField(tdoc, "add-distinct", new_author.getString("nameIdentifier"), "nameIdentifier");
        }

        addAtomicField(tdoc, "add-distinct", new_author.optString("full"), "authors");

        JSONArray labels = new_author.optJSONArray("labels", new JSONArray());
        addAtomicField(tdoc, "add-distinct", labels.toList(), "labels");
        addAtomicField(tdoc, "add-distinct", user, "modified_by_user");

        return tdoc;

    }

    public static JSONObject saveAuthorsInTitle(JSONObject js, String user) throws SolrServerException, IOException {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            addAtomicField(doc, "set", js.getJSONArray("authors").toString(), "authors_kapp");
            addAtomicField(doc, "set", js.getJSONArray("ids"), "authors_ids");
            addAtomicField(doc, "set", js.getJSONArray("nameIdentifiers"), "nameIdentifier");
            addAtomicField(doc, "set", js.getJSONArray("names"), "authors");
            addAtomicField(doc, "add-distinct", "author_modified", "labels");
            
            addAtomicField(doc, "add-distinct", user, "modified_by_user");

            solr.add("titles", doc);
            solr.commit("titles");

            if (js.has("author") && !js.isNull("author")) {
                addAtomicField(doc, "add-distinct", "author_modified", "labels");
                saveAuthor(js.getJSONObject("author"), true, user);
            }
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;

    }

    public static JSONObject addDocToBatch(String pid, String batch, String user) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            ret = addDocToBatch(solr, pid, batch, user);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject addDocToBatch(SolrClient solr, String pid, String batch, String user) {

        JSONObject ret = new JSONObject();
        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", pid);

            addAtomicField(doc, "set", batch, "batch");
            addAtomicField(doc, "add-distinct", user, "modified_by_user");
            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject createBatchFromLabel(JSONObject js, String user) {

        JSONObject ret = new JSONObject();
        String label = js.getString("label");
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JSONArray docs = IndexSearcher.getLabelDocs(label).getJSONObject("response").getJSONArray("docs");
            for (int i = 0; i < docs.length(); i++) {
                addDocToBatch(solr, docs.getJSONObject(i).getString("pid"), "label_" + label, user);
            }
            ret.put("batch", "label_" + label);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject changeStateInBatch(JSONObject js, String user) {

        JSONObject ret = new JSONObject();

        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JSONArray docs = IndexSearcher.getBatchDocs(js.getString("batch"), true).getJSONObject("response").getJSONArray("docs");
            for (int i = 0; i < docs.length(); i++) {
                changeDocStateBatch(solr, js, docs.getJSONObject(i).getString("pid"), user);
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    private static void changeDocStateBatch(SolrClient solr, JSONObject js, String pid, String user) {

        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", pid);

            fillState(doc, js.getString("state"), js.getJSONArray("licenses"), js.optString("reason", ""), user);

            solr.add("titles", doc);
            solr.commit("titles");
            LOGGER.log(Level.INFO, "state for {0} changed", pid);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private static void setParentClosed(SolrClient solr, String pid, JSONArray licenses, String reason, String user) {

        //Kontrollovat parent a pokud zadni potomek nema TO_CONTROL nastavit state na PARENT_CLOSED
        JSONObject t = IndexSearcher.getTitleByPid(pid).getJSONObject("response");
        if (t.getInt("numFound") > 0) {
            JSONObject title = t.getJSONArray("docs").getJSONObject(0);
            if (!title.optBoolean("is_hierachical")) {
                try {
                    String root_pid = title.getString("root_pid");
                    JsonQueryRequest jrequest = new JsonQueryRequest()
                            .setQuery("*")
                            .withFilter("root_pid:\"" + root_pid + "\"")
                            .withFilter("state:TO_CONTROL")
                            .setLimit(0);
                    jrequest.setResponseParser(new InputStreamResponseParser("json"));
                    NamedList<Object> resp = solr.request(jrequest, "titles");
                    InputStream is = (InputStream) resp.get("stream");
                    String s = IOUtils.toString(is, "UTF-8");
                    JSONObject ret = (new JSONObject(s)).getJSONObject("response");
                    if (ret.getInt("numFound") > 0) {
                        // Jeste jsou nekontrolavane potomky
                    } else {
                        SolrInputDocument doc = new SolrInputDocument();
                        doc.setField("pid", root_pid);
                        fillState(doc, "PARENT_CLOSED", null, reason, user);
                        solr.add("titles", doc);
                        solr.commit("titles");
                    }

                } catch (SolrServerException | IOException ex) {
                    LOGGER.log(Level.SEVERE, "Error setting PARENT_CLOSED: {0}", ex);
                }
            }
        }

        // 
    }

    public static JSONObject changeState(JSONObject js, String user) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {

            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));
            fillState(doc, js.getString("state"), js.getJSONArray("licenses"), js.optString("reason", ""), user);
            solr.add("titles", doc);
            solr.commit("titles");
            // Pro model periodicalvolume a monographunit
            String model = js.optString("model", "");
            boolean checkParent = model.equals("periodicalvolume") || model.equals("monographunit");
            if (checkParent) {
                setParentClosed(solr, js.getString("pid"), js.getJSONArray("licenses"), js.optString("reason", ""), user);
            }
            LOGGER.log(Level.INFO, "state for {0} changed", js.getString("pid"));
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error changing state: {0}", ex);
            ret.put("error", ex);
        }
        return ret;
    }

    private static void fillState(SolrInputDocument doc, String state, JSONArray licenses, String reason, String user) {

        addAtomicField(doc, "set", state, "state");
        addAtomicField(doc, "set", licenses, "licenses");
        addAtomicField(doc, "set", user, "state_user");
        addAtomicField(doc, "set", reason, "state_reason");
        addAtomicField(doc, "add-distinct", user, "modified_by_user");

        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        addAtomicField(doc, "set", d, "state_date");

        JSONObject h = new JSONObject()
                .put("date", d)
                .put("reason", reason)
                .put("licenses", licenses)
                .put("user", user)
                .put("state", state);

        addAtomicField(doc, "add", h.toString(), "state_history");
    }

    public static JSONObject saveContract(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("name", js.optString("name"));
            doc.setField("title", js.optString("title"));
            doc.setField("filename", js.optString("filename"));
            doc.setField("cislo_smlouvy", js.optString("cislo_smlouvy"));
            doc.setField("nazev_poskytovatele", js.optString("nazev_poskytovatele"));
            doc.setField("ico", js.optString("ico"));
            doc.setField("datum_uzavreni", js.optString("datum_uzavreni"));
            doc.setField("datum_platnosti", js.optString("datum_platnosti"));
            doc.setField("kontaktni_osoby", js.optJSONArray("kontaktni_osoby"));
            doc.setField("souhrn", js.optString("souhrn"));
            doc.setField("digitalni_knihovny", js.optJSONArray("digitalni_knihovny"));
            doc.setField("hlavni_smlouva", js.optString("hlavni_smlouva"));
            doc.setField("hlavni", js.optBoolean("hlavni"));
            doc.setField("dodatky", js.optJSONArray("dodatky"));
            doc.setField("ccnb", js.optJSONArray("ccnb"));
            doc.setField("issn", js.optJSONArray("issn"));
            doc.setField("license", js.optJSONArray("license"));

            solr.add("contracts", doc);
            solr.commit("contracts");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
}
