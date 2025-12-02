package cz.inovatika;

import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.model.HarvesterContext;
import cz.inovatika.model.Instance;

import java.util.function.Consumer;


/**
 * Defines the interface for data harvesters that are capable of iteratively
 * downloading records from an external API, typically using cursor-based
 * pagination for efficient traversal of large datasets.
 *
 * <p>The harvester focuses on data retrieval and delegates the actual item
 * processing (e.g., downloading associated MODS files or storage) to the
 * provided {@code Consumer}.
 */
public interface Harvester {

    /**
     * Executes an incremental harvest based on the provided instance configuration.
     *
     * <p>The method iterates over records from the external API, typically using a
     * Solr query with a {@code cursorMark} parameter for efficient pagination
     * and an incremental filter query (FQ) to retrieve records updated since
     * the last successful run.
     *
     * <p>For each retrieved item, a {@code HarvestContext} is created and passed
     * to the consumer for further processing.
     *
     * @param applicationConfig
     * @param harvestingInstanceConfig The harvester instance configuration, containing the URL,
     *                                 API version, and storage for the last update timestamp (lastUpdated).
     * @param consumer                 The consumer that receives the {@code HarvestContext} for
     *                                 each harvested item. The consumer is responsible for processing the item,
     *                                 such as calling sub-queries (like fetchMods) using the provided
     *                                 {@code HttpClient} within the context.
     */
    public void harvest(ApplicationConfig applicationConfig, Instance harvestingInstanceConfig, Consumer<HarvesterContext> consumer);
}
