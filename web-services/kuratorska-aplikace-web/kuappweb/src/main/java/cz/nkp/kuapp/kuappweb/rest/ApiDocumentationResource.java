package cz.nkp.kuapp.kuappweb.rest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.io.IOUtils;

@Path("")
public class ApiDocumentationResource {

    private static final String OPENAPI_RESOURCE = "/cz/nkp/kuapp/kuappweb/rest/openapi.yaml";
    private static final String INDEX_RESOURCE = "/cz/nkp/kuapp/kuappweb/rest/index.html";

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response index() throws Exception {
        return html();
    }

    @GET
    @Path("index.html")
    @Produces(MediaType.TEXT_HTML)
    public Response html() throws Exception {
        return Response.ok(readResource(INDEX_RESOURCE), MediaType.TEXT_HTML_TYPE.withCharset("UTF-8")).build();
    }

    @GET
    @Path("openapi.yaml")
    @Produces("application/yaml")
    public Response openApi(@Context UriInfo uriInfo) throws Exception {
        String openApi = readResource(OPENAPI_RESOURCE)
                .replace("${REST_BASE_URL}", uriInfo.getBaseUri().toString().replaceAll("/$", ""));
        return Response.ok(openApi, "application/yaml;charset=UTF-8").build();
    }

    private static String readResource(String path) throws Exception {
        try (InputStream stream = ApiDocumentationResource.class.getResourceAsStream(path)) {
            if (stream == null) {
                return "";
            }
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }
}
