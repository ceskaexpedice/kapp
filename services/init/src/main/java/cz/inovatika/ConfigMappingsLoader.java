package cz.inovatika;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class ConfigMappingsLoader {

    private final Map<String, Map<String, String>> exact = new HashMap<>();
    private final List<Pair> startsWith = new ArrayList<>();

    private static class Pair {
        String prefix;
        Map<String, String> headers;
        Pair(String p, Map<String, String> h) { prefix = p; headers = h; }
    }

    public ConfigMappingsLoader(Path configPath) throws IOException {
        String json = Files.readString(configPath);
        JSONObject root = new JSONObject(json);
        JSONArray mappings = root.optJSONArray("mappings");
        if (mappings == null) return;
        for (int i = 0; i < mappings.length(); i++) {
            JSONObject m = mappings.getJSONObject(i);
            JSONObject headers = m.optJSONObject("headers");
            if (headers == null) continue;
            Map<String, String> map = new HashMap<>();
            for (String key : headers.keySet()) {
                map.put(key, headers.getString(key));
            }
            if (m.has("match")) {
                exact.put(m.getString("match"), map);
            } else if (m.has("matchStartsWith")) {
                startsWith.add(new Pair(m.getString("matchStartsWith"), map));
            }
        }
    }

    public Map<String, String> getHeadersForFile(String fileName) {
        Map<String, String> h = exact.get(fileName);
        if (h != null) return h;
        for (Pair p : startsWith) {
            if (fileName.startsWith(p.prefix)){ return p.headers;}
            else System.out.println("File name: " + fileName + ", checking prefix: " + p.prefix);
        }
        return null;
    }
}