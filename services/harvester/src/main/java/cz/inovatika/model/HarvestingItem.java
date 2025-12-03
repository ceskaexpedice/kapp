package cz.inovatika.model;


import org.eclipse.jetty.util.StringUtil;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single item or record retrieved during the harvesting process.
 *
 * <p>This class acts as a Data Transfer Object (DTO) holding essential metadata
 * and context information needed for subsequent processing steps (e.g.,
 * downloading full metadata or storage). It is typically populated from
 * the initial, lightweight search results (like a Solr document).
 */
public class HarvestingItem {

    /**
     * The unique persistent identifier of the item's root object in the source system.
     * This is useful for grouping related items (e.g., all pages belonging to one monograph).
     */
    private String rootPid;

    /**
     * The unique persistent identifier of the item itself. This is the primary key
     * used to locate and reference the record.
     */
    private String pid;

    /**
     * The data model or content type of the item (e.g., "monograph", "periodicalvolume", "monographunit").
     */
    private String model;

    /**
     * A string representation of the item's title(s), often retrieved as a concatenated
     * value from the source search results.
     */
    private String titles;

    /**
     * Licenses associated with the item
     */
    private String[] licenses;

    /**
     * A reference to the {@link Instance} configuration associated with this item.
     * This provides the necessary context (e.g., API version, URL) required for
     * subsequent data fetching operations.
     */
    private Instance instance;

    private boolean isRootTitle = false; // true only for monograph with monographunits and periodical

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getRootPid() {
        return this.rootPid;
    }

    public void setRootPid(String rootPid) {
        this.rootPid = rootPid;
    }

    public String getTitles() {
        return titles;
    }

    public void setTitles(String titles) {
        this.titles = titles;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    public String[] getLicenses() {
        return licenses;
    }

    public void setLicenses(String[] licenses) {
        this.licenses = licenses;
    }

    public boolean isRootTitle() {
        return isRootTitle;
    }

    public void setRootTitle(boolean rootTitle) {
        this.isRootTitle = rootTitle;
    }


    @Override
    public String toString() {
        return "HarvestingItem{" +
                "rootPid='" + rootPid + '\'' +
                ", pid='" + pid + '\'' +
                ", model='" + model + '\'' +
                ", titles='" + titles + '\'' +
                ", licenses=" + Arrays.toString(licenses) +
                ", instance=" + instance +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        HarvestingItem that = (HarvestingItem) o;
        return Objects.equals(rootPid, that.rootPid) && Objects.equals(pid, that.pid) && Objects.equals(model, that.model) && Objects.equals(titles, that.titles) && Objects.deepEquals(licenses, that.licenses) && Objects.equals(instance, that.instance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootPid, pid, model, titles, Arrays.hashCode(licenses), instance);
    }
}
