package cz.inovatika.touchstore;

import cz.inovatika.conf.TouchStoreConfig;
import cz.inovatika.touchstore.impl.FileSystemTouchStoreImpl;
import cz.inovatika.touchstore.impl.SolrTouchStoreImpl;
import org.apache.solr.client.solrj.SolrClient;

public enum TouchStoreFactory {

    file {
        @Override
        public TouchStore create(SolrClient solrClient, TouchStoreConfig config) {
            return new FileSystemTouchStoreImpl(config);
        }
    },
    solr {
        @Override
        public TouchStore create(SolrClient solrClient, TouchStoreConfig config) {
            return new SolrTouchStoreImpl(solrClient);
        }
    };

    public abstract TouchStore create(SolrClient solrClient, TouchStoreConfig config);

}
