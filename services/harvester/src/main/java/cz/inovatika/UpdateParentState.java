package cz.inovatika;

import org.apache.solr.client.solrj.SolrServerException;

import java.io.IOException;

public interface UpdateParentState {

    void updateParentState() throws SolrServerException, IOException;
}
