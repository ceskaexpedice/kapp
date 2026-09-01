package cz.nkp.kuapp.kuappweb.rest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("v1/status")
public class StatusResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "kuappweb-rest");
        response.put("status", "ok");
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
