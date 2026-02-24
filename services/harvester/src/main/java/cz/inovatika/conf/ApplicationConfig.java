package cz.inovatika.conf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cz.inovatika.model.Instance;
import cz.inovatika.model.InstantTypeAdapter;

import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public record ApplicationConfig(List<Instance> instances,
                                WorkflowConfig workflow,
                                SolrConfig solr,
                                TouchStoreConfig touchStoreConfig,
                                List<JobConfig> jobConfigs) {


    public static ApplicationConfig loadFromStream(InputStream is) throws ConfigException {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                    .create();
            return gson.fromJson(new InputStreamReader(is), ApplicationConfig.class);
        } catch (JsonIOException | JsonSyntaxException e) {
            throw new ConfigException(e);
        }
    }

    public static ApplicationConfig loadFromFile(String filename) throws ConfigException {
        try {
            return loadFromStream(new FileInputStream(filename));
        } catch (ConfigException | FileNotFoundException e) {
            throw new ConfigException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(instances, ((ApplicationConfig) o).instances);
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
