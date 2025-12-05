package cz.nkp.kuapp.kuappweb;

import cz.nkp.kuapp.kuappweb.index.AUTIndexer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;

/**
 *
 * @author alber
 */
@WebServlet(name = "UserServlet", urlPatterns = {"/user/*"})
public class UserServlet extends HttpServlet {

    public static final Logger LOGGER = Logger.getLogger(UserServlet.class.getName());

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setDateHeader("Expires", 0); // Proxies.
        try {
            String actionNameParam = request.getPathInfo().substring(1);
            if (actionNameParam != null) {
                Actions actionToDo = Actions.valueOf(actionNameParam.toUpperCase());
                JSONObject json = actionToDo.doPerform(request, response);
                if (json != null) {
                    response.getWriter().println(json.toString(2));
                }

            } else {
                response.getWriter().print("actionNameParam -> " + actionNameParam);
            }
        } catch (IOException e1) {
            LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e1.toString());
            response.getWriter().print(e1.toString());
        } catch (SecurityException e1) {
            LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (Exception e1) {
            LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e1.toString());
            response.getWriter().print(e1.toString());
        }
    }
    
    private static String getFormDataAsString(Map<String, String> formData) {
    StringBuilder formBodyBuilder = new StringBuilder();
    for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
        if (formBodyBuilder.length() > 0) {
            formBodyBuilder.append("&");
        }
        formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
        formBodyBuilder.append("=");
        formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
    }
    return formBodyBuilder.toString();
}

    enum Actions {

        LOGIN {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = Options.getInstance().getJSONObject("keycloak");

                    System.out.println(js);
                    InputStream is = new ByteArrayInputStream(js.toString().getBytes());
                    AuthzClient authzClient = AuthzClient.create(is);
                    // create an authorization request
                    AuthorizationRequest request = new AuthorizationRequest();

                    AuthorizationResponse response = authzClient.authorization("kapp", "kapp").authorize(request);
                    String rpt = response.getToken();

                    System.out.println(rpt);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        TOKEN {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject user = new JSONObject();
                try {
                    JSONObject js = Options.getInstance().getJSONObject("keycloak");

                 ///  curl -s -X POST "https://eduid.inovatika.dev/realms/kapp/protocol/openid-connect/token" ^
                    //  -H "Content-Type: application/x-www-form-urlencoded" ^
                    //  -d "grant_type=authorization_code" ^
                    //  -d "client_id=kapp" ^
                    //  -d "code=737e5266-0187-41d2-9576-6e67b8787fd1.0fbf26b4-1351-4028-99eb-aac90bb0414a.3fc1dc7f-58c3-4db0-9506-7a8b50d6dd5d" ^
                    //  -d "scope=openid"
                    
                    String url = js.getString("auth-server-url") + "realms/" + js.getString("realm") + "/protocol/openid-connect/token";
                    
                    Map<String, String> formData = new HashMap<>();
                    formData.put("grant_type", "authorization_code");
                    formData.put("client_id", "kapp");
                    formData.put("scope", "openid");
                    formData.put("code", req.getParameter("code"));
                    formData.put("redirect_uri", req.getParameter("redirect_uri"));

                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                            .setHeader("Content-Type", "application/x-www-form-urlencoded")
                            .uri(new URI(url))
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String r = response.body();
                    
                    JSONObject json = new JSONObject(r);
                    
                    String[] chunks = json.getString("access_token").split("\\.");
                    Base64.Decoder decoder = Base64.getUrlDecoder();
                    
                    String header = new String(decoder.decode(chunks[0]));
                    String payload = new String(decoder.decode(chunks[1]));
                    user = new JSONObject(payload);
                    req.getSession().setAttribute("user", user);
                    
                    // get user by token
                    // curl -X GET -H "Authorization: Bearer $ACCESS_TOKEN" $KCHOST/auth/admin/realms/$REALM/users | jq
//                    url = js.getString("auth-server-url") + "/admin/realms/" + js.getString("realm") + "/users";
//                    HttpRequest grequest = HttpRequest.newBuilder()
//                            .GET()
//                            .setHeader("Authorization", "Bearer " + json.getString("access_token"))
//                            .uri(new URI(url))
//                            .build();
//                    HttpResponse<String> uresponse = client.send(grequest, HttpResponse.BodyHandlers.ofString());
//                    String u = uresponse.body();
                    
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    user.put("error", ex.toString());
                }
                return user;
            }
        },
        RT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    AUTIndexer fh = new AUTIndexer();
                    json = fh.resumptionToken(req.getParameter("rt"));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        };

        abstract JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception;
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
