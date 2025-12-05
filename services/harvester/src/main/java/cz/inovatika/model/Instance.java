package cz.inovatika.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Represents a Kramerius digital library instance (K5 or K7).
 * It contains essential configuration information necessary for data harvesting.
 * <p>
 * This class is typically used within a data harvesting process.
 * </p>
 *
 * @see cz.inovatika.Harvester The class that uses this instance for harvesting operations.
 * @see APIVersion The enumeration of supported Kramerius API versions.
 */
public class Instance {

    /**
     * Logger for the {@code Instance} class.
     */
    public static Logger LOGGER = Logger.getLogger(Instance.class.getName());

    private String acronym;
    private String url;
    private APIVersion apiVersion = APIVersion.v7;
    private boolean solrCloud = false;

    private Instant lastUpdated;
    private String timestampPath;

    private List<String> physicalLocations = new ArrayList<>();

    public Instance() {}

    /**
     * Sets the acronym of the instance.
     * @param acronym The new acronym for the instance (e.g., "mzk").
     */
    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    /**
     * Sets the URL address of the API.
     * @param url The new API URL address.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets the API version of the instance.
     * @param apiVersion The new API version.
     */
    public void setApiVersion(APIVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * Sets the Solr Cloud usage flag.
     * @param solrCloud {@code true} if Solr Cloud is in use; otherwise {@code false}.
     */
    public void setSolrCloud(boolean solrCloud) {
        this.solrCloud = solrCloud;
    }

    /**
     * Returns the acronym of the instance.
     * @return The instance acronym.
     */
    public String getAcronym() {
        return acronym;
    }

    /**
     * Returns the URL address of the API.
     * @return The API URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the API version.
     * @return The API version.
     */
    public APIVersion getApiVersion() {
        return apiVersion;
    }

    /**
     * Checks if Solr Cloud is used by the instance.
     * @return {@code true} if Solr Cloud is used.
     */
    public boolean isSolrCloud() {
        return solrCloud;
    }

    /**
     * Returns the timestamp of the last update.
     * @return The last updated {@code Instant}.
     */
    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Updates the timestamp of the last data update or harvest.
     * @param lastUpdated The new update timestamp.
     */
    public void updateLastUpdatedTimestampManually(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getTimestampPath() {
        return timestampPath;
    }

    public void setTimestampPath(String timestampPath) {
        this.timestampPath = timestampPath;
    }

    public List<String> getPhysicalLocations() {
        return physicalLocations;
    }

    public void setPhysicalLocations(List<String> physicalLocations) {
        this.physicalLocations = physicalLocations;
    }

    public boolean saveLastUpdatedToPath() {
        this.lastUpdated = Instant.now();
        if (timestampPath == null) {
            LOGGER.warning("Cannot save timestamp: timestampPath is null for instance " + acronym);
            return false;
        }
        if (lastUpdated == null) {
            LOGGER.warning("Cannot save timestamp: lastUpdated is null for instance " + acronym);
            return false;
        }
        Path fullPath = Paths.get(timestampPath);
        try {
            Files.createDirectories(fullPath.getParent());
            String content = lastUpdated.toString() + System.lineSeparator();

            Files.write(
                    fullPath,
                    content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            LOGGER.info("Successfully saved new timestamp for " + acronym + " to: " + fullPath);
            return true;
        } catch (Exception e) {
            LOGGER.severe("Failed to save timestamp for instance " + acronym + " to path: " + fullPath + ". Error: " + e.getMessage());
            return false;
        }
    }


    public void loadLastUpdatedFromPath() {
        if (timestampPath == null) return;

        try {
            String fullPath = Paths.get(timestampPath).toString();
            String timestampString = Files.lines(Paths.get(fullPath))
                    .findFirst()
                    .orElse(null);

            if (timestampString != null && !timestampString.trim().isEmpty()) {
                this.lastUpdated = Instant.parse(timestampString.trim());
            } else {
                LOGGER.warning("Timestamp file is empty or missing: " + fullPath);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to load timestamp for instance " + acronym + " from path: " + timestampPath + ". Error: " + e.getMessage());
            this.lastUpdated = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Instance that = (Instance) o;
        return Objects.equals(acronym, that.acronym) && Objects.equals(apiVersion, that.apiVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acronym, apiVersion);
    }

    @Override
    public String toString() {
        return "Instance{" +
                "acronym='" + acronym + '\'' +
                ", url='" + url + '\'' +
                ", apiVersion=" + apiVersion +
                ", solrCloud=" + solrCloud +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
