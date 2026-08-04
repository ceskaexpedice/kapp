package cz.nkp.kuapp.kuappweb.rest;

import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("/rest")
public class RestApplication extends ResourceConfig {

    public RestApplication() {
        packages("cz.nkp.kuapp.kuappweb.rest");
        register(JacksonFeature.class);
    }
}
