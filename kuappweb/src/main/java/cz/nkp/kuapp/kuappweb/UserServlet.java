/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package cz.nkp.kuapp.kuappweb;

import cz.nkp.kuapp.kuappweb.index.AUTIndexer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
