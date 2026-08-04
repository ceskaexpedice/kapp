package cz.inovatika.impl;

import cz.inovatika.Constants;
import cz.inovatika.UpdateParentState;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.workflow.States;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

import static cz.inovatika.Constants.Collection.TITLES_COLLECTION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UpdateParentStateImpl implements UpdateParentState {

    public static final Logger LOGGER = Logger.getLogger(UpdateParentStateImpl.class.getName());
    public static final int BATCH_SIZE = 50;

    public SolrClient solrClient;

    public UpdateParentStateImpl(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    @Override
    public void updateParentState() throws SolrServerException, IOException {
        List<String> allParentPids = fetchAllHierarchicalPids();

        for (int i = 0; i < allParentPids.size(); i += BATCH_SIZE) {
            int toIndex = Math.min(i + BATCH_SIZE, allParentPids.size());
            List<String> currentBatch = allParentPids.subList(i, toIndex);


            StringBuilder queryBuilder = new StringBuilder( String.format("%s:(", Constants.TitlesCollectionFields.AppDataFields.ROOT_PID_FIELD));
            for (int j = 0; j < currentBatch.size(); j++) {
                queryBuilder.append("\"").append(currentBatch.get(j)).append("\"");
                if (j < currentBatch.size() - 1) queryBuilder.append(" OR ");
            }
            queryBuilder.append(")");

            String jsonFacet = "{" +
                    "  parents: {" +
                    "    type: 'terms'," +
      String.format("    field: '%s',", Constants.TitlesCollectionFields.AppDataFields.ROOT_PID_FIELD) +
                    "    limit: " + BATCH_SIZE + "," +
                    "    facet: {" +
      String.format("      stavy: { type: 'terms', field: '%s' }", Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD) +
                    "    }" +
                    "  }" +
                    "}";

            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("q", queryBuilder.toString());
            params.set("rows", 0);
            params.set("json.facet", jsonFacet);

            QueryResponse response = solrClient.query(TITLES_COLLECTION, params);
            processBatchResponse(response);
        }
    }


    private void processBatchResponse(QueryResponse response) {
        Object parentsFacet = getFacetValue(response.getResponse().get("facets"), "parents");
        if (parentsFacet == null) {
            return;
        }

        List<SolrInputDocument> updates = new ArrayList<>();

        for (Object bucket : getBuckets(parentsFacet)) {
            Object rootPidValue = getFacetValue(bucket, "val");
            if (rootPidValue == null) {
                continue;
            }

            String rootPid = rootPidValue.toString();
            List<String> childStates = getBucketValues(getFacetValue(bucket, "stavy"));
            States parentState = childStates.isEmpty() || childStates.contains(States.TO_CONTROL.name()) ?  States.PARENT_OPEN : States.PARENT_CLOSED;
            SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                    Constants.TitlesCollectionFields.AppDataFields.PID_FIELD,
                    SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);


            wrapper.setField(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD, rootPid);
            wrapper.setField(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD, parentState.name());

            updates.add(wrapper.getDocument());
        }

        if (!updates.isEmpty()) {
            try {
                this.solrClient.add(TITLES_COLLECTION, updates);
            } catch (SolrServerException | IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
        }
    }

    private List<String> fetchAllHierarchicalPids() throws SolrServerException, IOException {
        List<String> results = new ArrayList<>();
        String jsonFacet = "{" +
                "  all_ids: {" +
                "    type: 'terms'," +
                "    field: 'pid'," +
                "    limit: -1" +
                "  }" +
                "}";

        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "is_hierarchical:true");
        params.set("rows", 0); // Dokumenty nas nezajimaji, jen ty unikaty
        params.set("json.facet", jsonFacet);

        QueryResponse response = this.solrClient.query(TITLES_COLLECTION, params);

        Object idFacet = getFacetValue(response.getResponse().get("facets"), "all_ids");
        for (Object bucket : getBuckets(idFacet)) {
            Object val = getFacetValue(bucket, "val");
            if (val != null) {
                results.add(val.toString());
            }
        }
        return results;
    }

    private static List<?> getBuckets(Object facet) {
        Object buckets = getFacetValue(facet, "buckets");
        return buckets instanceof List<?> bucketList ? bucketList : List.of();
    }

    private static List<String> getBucketValues(Object facet) {
        List<String> values = new ArrayList<>();
        for (Object bucket : getBuckets(facet)) {
            Object value = getFacetValue(bucket, "val");
            if (value != null) {
                values.add(value.toString());
            }
        }
        return values;
    }

    private static Object getFacetValue(Object facet, String key) {
        if (facet instanceof NamedList<?> namedList) {
            return namedList.get(key);
        }
        if (facet instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

}
