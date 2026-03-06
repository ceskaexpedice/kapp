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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UpdateParentStateImpl implements UpdateParentState {

    public static final Logger LOGGER = Logger.getLogger(UpdateParentStateImpl.class.getName());
    public static final int BATCH_SIZE = 50;

    public SolrClient solrClient = null;

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

            QueryResponse response = solrClient.query(Constants.Collection.TITLES_COLLECTION, params);
            processBatchResponse(response);
        }
    }


    private void processBatchResponse(QueryResponse response) {
        Map<Object, Object> responseMap = (Map<Object, Object>) response.getResponse().get("facets");
        if (responseMap == null || !responseMap.containsKey("parents")) {
            return;
        }

        Map<Object, Object> parentsFacet = (Map<Object, Object>) responseMap.get("parents");
        List<Map<Object, Object>> buckets = (List<Map<Object, Object>>) parentsFacet.get("buckets");

        List<SolrInputDocument> updates = new ArrayList<>();

        for (Map<Object, Object> bucket : buckets) {
            String rootPid = bucket.get("val").toString();

            Map<Object, Object> stavyFacet = (Map<Object, Object>) bucket.get("stavy");
            List<Map<Object, Object>> stavBuckets = (List<Map<Object, Object>>) stavyFacet.get("buckets");

            List<String> childStates = new ArrayList<>();
            for (Map<Object, Object> sb : stavBuckets) {
                childStates.add(sb.get("val").toString());
            }

            States parentState = childStates.isEmpty() || childStates.contains(States.TO_CONTROL.name()) ?  States.PARENT_OPEN : States.PARENT_CLOSED;
            SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                    Constants.TitlesCollectionFields.AppDataFields.PID_FIELD,
                    new SolrInputDocument(),
                    SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);


            wrapper.addField(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD, rootPid);
            wrapper.setField(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD, parentState.name());

            updates.add(wrapper.getDocument());
        }

        if (!updates.isEmpty()) {
            try {
                this.solrClient.add(Constants.Collection.TITLES_COLLECTION, updates);
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

        QueryResponse response = this.solrClient.query("titles", params);

        Map<Object, Object> responseMap = (Map<Object, Object>) response.getResponse().get("facets");

        if (responseMap != null && responseMap.containsKey("all_ids")) {
            Map<Object, Object> idFacet = (Map<Object, Object>) responseMap.get("all_ids");
            List<Map<Object, Object>> buckets = (List<Map<Object, Object>>) idFacet.get("buckets");

            if (buckets != null) {
                for (Map<Object, Object> bucket : buckets) {
                    Object val = bucket.get("val");
                    if (val != null) {
                        results.add(val.toString());
                    }
                }
            }
        }
        return results;
    }

//    private static Http2SolrClient buildSolrClient(String url) {
//        return new Http2SolrClient.Builder(url)
//                .withConnectionTimeout(200, TimeUnit.SECONDS)
//                .withIdleTimeout(5, TimeUnit.MINUTES)
//                .withRequestTimeout(5, TimeUnit.MINUTES).useHttp1_1(true)
//                .build();
//    }

}
