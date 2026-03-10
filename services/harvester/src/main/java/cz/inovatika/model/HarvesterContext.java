package cz.inovatika.model;

import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.touchstore.TouchStore;
import org.apache.solr.client.solrj.SolrClient;

import java.net.http.HttpClient;


/**
 * A record that serves as a context container for processing a single harvested item.
 *
 * <p>This record bundles all necessary dependencies and data required by the consumer
 * (processor) to perform subsequent operations on a {@link HarvestingItem}, such as
 * downloading full metadata or saving the item. Since records are immutable, this
 * provides a safe, read-only context for processing.
 *
 * @param item The primary {@link HarvestingItem} instance retrieved during the initial harvest query.
 * @param client The {@link HttpClient} instance shared across the entire harvest process,
 * used for making follow-up requests (e.g., fetching MODS metadata).
 * @param config The {@link Instance} configuration providing the global context,
 * including API version and base URLs.
 */
public record HarvesterContext(
        ApplicationConfig applicationConfig,
        HarvestingItem item,
        TouchStore touchStore,
        HttpClient client,
        SolrClient solrClient,
        Instance config,
        long harvestStartTimestamp,
        int numItems,
        int processedSoFar
){}
