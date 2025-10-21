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

    private List<Instance> instances;

    public ApplicationConfig(List<Instance> instances) {
        this.instances = instances;
    }

    public List<Instance> getInstances() {
        return instances;
    }

    public static ApplicationConfig loadFromStream(InputStream is) throws ConfigException {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                    .create();
            return gson.fromJson(new InputStreamReader(is), ApplicationConfig.class);
        } catch (JsonSyntaxException e) {
            throw new ConfigException(e);
        } catch (JsonIOException e) {
            throw new ConfigException(e);
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
