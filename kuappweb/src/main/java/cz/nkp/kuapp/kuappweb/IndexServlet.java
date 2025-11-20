package cz.nkp.kuapp.kuappweb;

import cz.nkp.kuapp.kuappweb.index.AUTIndexer;
import cz.nkp.kuapp.kuappweb.index.IndexSearcher;
import cz.nkp.kuapp.kuappweb.index.SolrIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
@WebServlet(name = "IndexServlet", urlPatterns = {"/index/*"})
@MultipartConfig(location = "/tmp", fileSizeThreshold = 1024 * 1024,
        maxFileSize = 1024 * 1024 * 10, // 10MB
        maxRequestSize = 1024 * 1024 * 50)   // 50MB)
public class IndexServlet extends HttpServlet {

    public static final Logger LOGGER = Logger.getLogger(IndexServlet.class.getName());

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

    private JSONObject doUpload(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        JSONObject ret = new JSONObject();
        //for (Part part : request.getParts()) {
        Part part = request.getPart("file");
        LOGGER.log(Level.INFO, "Part name -> {0}", part.getName());
        String subbmittedFilename = request.getParameter("name");
        String contents = IOUtils.toString(part.getInputStream(), StandardCharsets.UTF_8.name());

        return ret;
    }

    enum Actions {

        AUT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    AUTIndexer fh = new AUTIndexer();
                    json = fh.full();
                } catch (JSONException ex) {
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
        },
        TITLES {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.searchTitles(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        GET_DOC {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getDocByPid(req.getParameter("pid"));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        GET_RELATED {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getRelated(req.getParameter("pid"));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        AUTHORS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.searchAuthors(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        LABELS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getLabels();
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_LABELS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONArray js = new JSONArray(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexLabels(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_DOC_LABELS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexDocLabels(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_BATCHES {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONArray js = new JSONArray(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexBatches(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_DOC_BATCHES {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexDocBatches(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        GET_BATCH_DOCS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getBatchDocs(req.getParameter("batch"), false);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        UPLOAD_BATCH {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject ret = new JSONObject();
                //for (Part part : request.getParts()) {
                Part part = req.getPart("file");
                LOGGER.log(Level.INFO, "Part name -> {0}", part.getName());
                String batch = req.getParameter("name");
                //String contents = IOUtils.toString(part.getInputStream(), StandardCharsets.UTF_8.name());

                BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream()));
                while (reader.ready()) {
                    ret = SolrIndexer.addDocToBatch(reader.readLine(), batch);
                }
                return ret;
            }
        },
        CHANGE_STATE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.changeState(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        CHANGE_STATE_IN_BATCH {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.changeStateInBatch(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        CONFIG {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getConfig();
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        CHECK_AUTHOR {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.checkAuthor(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        GET_AUTHOR_BY_ID {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getAuthorById(req.getParameter("id"));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        GET_LINKS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getLinks(req.getParameter("id"));
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
