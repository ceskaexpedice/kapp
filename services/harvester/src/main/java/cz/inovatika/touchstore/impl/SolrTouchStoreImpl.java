package cz.inovatika.touchstore.impl;

import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.touchstore.TouchStore;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;

import static cz.inovatika.Constants.Collection.TITLES_COLLECTION;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SolrTouchStoreImpl implements TouchStore {

    public static final Logger LOGGER = Logger.getLogger(SolrTouchStoreImpl.class.getName());
    private static final int CACHE_SIZE = 10;

    private final Map<String, SolrDocument> cache =
            new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SolrDocument> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    private final SolrClient solrClient;

    public SolrTouchStoreImpl(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    private SolrDocument getDocument(String pid) throws IOException, SolrServerException {
        if (pid == null) {
            return null;
        }
        SolrDocument cachedDoc = cache.get(pid);
        if (cachedDoc != null) {
            return cachedDoc;
        }
        SolrDocument doc = this.solrClient.getById(TITLES_COLLECTION, pid);
        if (doc != null) {
            cache.put(pid, doc);
        }
        return doc;
    }


    @Override
    public void touch(String pid, String md5, String source, String rootPid, List<String> physicalLocations) {
        // no action
    }

    @Override
    public void storeMods(String pid, String biblioMods){
        // no action - already in index
    }

    @Override
    public boolean exists(String pid) {
        try {
            SolrDocument doc = getDocument(pid);
            return doc != null;
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return false;
        }
    }

    @Override
    public boolean isModsChanged(String pid, String md5){
        try {
            Object soldM5Sum = getDocument(pid).getFirstValue(
                    AppDataFields.BIBLIO_MODS_MD_5_FIELD
            );
            if (soldM5Sum != null) {
                return !md5.equals(soldM5Sum.toString());
            } else return true;
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
            return false;
        }
    }

    @Override
    public List<String> getPhysicalLocations(String pid) {
        try {
            SolrDocument doc = getDocument(pid);
            if (doc != null) {
                Collection<Object> fieldValues = doc.getFieldValues("physical_locations.facet");
                if (fieldValues != null) {
                    return fieldValues.stream().map(Object::toString).collect(Collectors.toList());
                } else {
                    return List.of();
                }
            } else return List.of();
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }
        return List.of();
    }

    @Override
    public String getRootPid(String pid) throws IOException {
        try {
            Object soldM5Sum = solrClient.getById(TITLES_COLLECTION, pid).getFirstValue("root.pid");
            return soldM5Sum != null ?  soldM5Sum.toString() : null;
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMd5(String pid) throws IOException {
        try {
            Object soldM5Sum = solrClient.getById(TITLES_COLLECTION, pid).getFirstValue(
                AppDataFields.BIBLIO_MODS_MD_5_FIELD
            );
            return soldM5Sum != null ?  soldM5Sum.toString() : null;
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        }
    }
}
