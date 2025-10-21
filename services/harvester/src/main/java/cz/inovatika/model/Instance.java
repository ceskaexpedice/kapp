package cz.inovatika.model;

import cz.inovatika.utils.XMLUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class Instance {

    public static Logger LOGGER = Logger.getLogger(Instance.class.getName());

    private String acronym;
    private String url;
    private APIVersion apiVersion = APIVersion.v7;

    private boolean solrCloud = false;
    private Instant lastUpdated;

    public Instance() {}

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setApiVersion(APIVersion apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setSolrCloud(boolean solrCloud) {
        this.solrCloud = solrCloud;
    }


    public String getAcronym() {
        return acronym;
    }

    public String getUrl() {
        return url;
    }

    public APIVersion getApiVersion() {
        return apiVersion;
    }

    public boolean isSolrCloud() {
        return solrCloud;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void updateLastUpdatedTimestamp(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
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
