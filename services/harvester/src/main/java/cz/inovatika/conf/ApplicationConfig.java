package cz.inovatika.conf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import cz.inovatika.model.Instance;
import cz.inovatika.model.InstantTypeAdapter;

import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ApplicationConfig {


    private TouchStoreConfig touchStoreConfig;
    private WorkflowConfig workflow;
    private List<Instance> instances;
    private SolrConfig solr;


    public ApplicationConfig(List<Instance> instances, WorkflowConfig workflow, SolrConfig solr, TouchStoreConfig touchStoreConfig) {
        this.instances = instances;
        this.workflow = workflow;
        this.solr = solr;
        this.touchStoreConfig = touchStoreConfig;
    }

    public List<Instance> getInstances() {
        return instances;
    }

    public WorkflowConfig getWorkflow() {
        return workflow;
    }

    public SolrConfig getSolr() {
        return solr;
    }

    public TouchStoreConfig getTouchStoreConfig() {
        return touchStoreConfig;
    }

    public static ApplicationConfig loadFromStream(InputStream is) throws ConfigException {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                    .create();
            return gson.fromJson(new InputStreamReader(is), ApplicationConfig.class);
        } catch (Exception e) {
            throw new ConfigException(e);
        }
    }
    public static ApplicationConfig loadFromFile(String filename) {
        try {
            return loadFromStream(new FileInputStream(new File(filename)));
        } catch (Exception e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationConfig that = (ApplicationConfig) o;
        return Objects.equals(instances, that.instances);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(instances);
    }

    @Override
    public String toString() {
        return "ApplicationConfig{" +
                "instances=" + instances +
                '}';
    }
}
