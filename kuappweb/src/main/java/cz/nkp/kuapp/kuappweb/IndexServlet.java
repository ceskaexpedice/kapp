package cz.nkp.kuapp.kuappweb;

import cz.nkp.kuapp.kuappweb.index.AUTIndexer;
import cz.nkp.kuapp.kuappweb.index.IndexSearcher;
import cz.nkp.kuapp.kuappweb.index.SolrIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
@WebServlet(name = "IndexServlet", urlPatterns = {"/index/*"})
@MultipartConfig(location = "/tmp", fileSizeThreshold = 1024 * 1024 * 50,
        maxFileSize = 1024 * 1024 * 50, // 10MB
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
//            if (UserServlet.getUser(request) == null) {
//            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//            return;
//            }
            String actionNameParam = request.getPathInfo().substring(1);
            if (actionNameParam != null) {
                Actions actionToDo = Actions.valueOf(actionNameParam.toUpperCase());
                JSONObject json = actionToDo.doPerform(request, response);
                if (json != null) {
                    if (request.getParameter("export") != null) {
                        response.setContentType("plain/text;charset=UTF-8");
                        response.getWriter().println(json.getString("csv"));
                    } else {
                        response.getWriter().println(json.toString(2));
                    }
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
        USER {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = UserServlet.getUser(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        HOME {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.home(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        AUT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    AUTIndexer fh = new AUTIndexer();
                    json = fh.update();
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        AUT_FULL {
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
        TITLES_AUTHOR {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.searchTitlesByAuthor(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        TITLES_CONTRACT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.searchTitlesByContract(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        TITLES_CONTRACT_FIND {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.findTitlesByContract(req);
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
                    json = IndexSearcher.getTitleByPid(req.getParameter("pid"));
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
        SAVE_DK {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONArray js = new JSONArray(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexDK(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_LICENSES {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONArray js = new JSONArray(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexLicenses(js);
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
        SAVE_TITLE_COLUMNS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONArray js = new JSONArray(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexTitleColumns(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        CREATE_BATCH_FROM_LABEL {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.createBatchFromLabel(js);
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
        SAVE_DOC_BATCH {
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
        SAVE_DOC_CONTRACT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexDocContracts(js, UserServlet.getUserName(req));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_AUTHOR {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.saveAuthor(js, false);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_AUTHOR_LABELS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexAuthorLabels(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_AUTHORS_IN_TITLE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.saveAuthorsInTitle(js); 
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_DECISION_DATE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.saveDecisionDate(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_TITLE_NOTE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.saveTitleNote(js);
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
        GET_USED_BATCHES {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getUsedBatches();
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
                // String contents = IOUtils.toString(part.getInputStream(), StandardCharsets.UTF_8.name());

                BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream()));
                while (reader.ready()) {
                    ret = SolrIndexer.addDocToBatch(reader.readLine(), batch);
                }
                return ret;
            }
        },
        CONTRACTS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.searchContracts(req);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        GET_CONTRACT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getContract(req.getParameter("id"));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        CHECK_CONTRACT_EXISTS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject ret = new JSONObject();
                try {
//                String filename = req.getParameter("filename");
//                File targetFile = new File(InitServlet.CONFIG_DIR + File.separator + "contracts" + File.separator + filename);
//                ret.put("exists", targetFile.exists());

                    JSONObject json = IndexSearcher.getContract(req.getParameter("filename"));
                    ret.put("exists", json.getJSONObject("response").getInt("numFound") > 0);

                } catch (Exception ex) {
                    ret.put("error", ex);
                }
                return ret;
            }
        },
        UPLOAD_CONTRACT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject ret = new JSONObject();
                try {
                    //for (Part part : request.getParts()) {
                    Part part = req.getPart("file");
                    String filename = part.getSubmittedFileName();
                    byte[] pdfBytes = part.getInputStream().readAllBytes();

                    // File targetFile = new File(InitServlet.CONFIG_DIR + File.separator + "contracts" + File.separator + filename);
                    OutputStream outStream = new FileOutputStream(InitServlet.CONFIG_DIR + File.separator + "contracts" + File.separator + filename);
                    outStream.write(pdfBytes);

                    if (Boolean.parseBoolean(req.getParameter("analyze"))) {
                        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
                        JSONObject gemini = GeminiAnalyzer.analyze(base64Pdf);

                        // candidates[0].content.parts[0].text
                        ret = new JSONObject(gemini.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text"));
                    } else {
                        ret.put("filename", filename);
                    }
                } catch (Exception ex) {
                    ret.put("error", ex);
                }
                return ret;
            }
        },
        ANALYZE_CONTRACT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject ret = new JSONObject();
                try {
                    String filename = req.getParameter("filename");
                    File targetFile = new File(InitServlet.CONFIG_DIR + File.separator + "contracts" + File.separator + filename);
                    byte[] pdfBytes = FileUtils.readFileToByteArray(targetFile);

                    String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
                    JSONObject gemini = GeminiAnalyzer.analyze(base64Pdf);

                    // candidates[0].content.parts[0].text
                    ret = new JSONObject(gemini.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text"));

                } catch (Exception ex) {
                    ret.put("error", ex);
                }
                return ret;
            }
        },
        SAVE_CONTRACT {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.saveContract(js);
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        CHANGE_STATE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.changeState(js, UserServlet.getUserName(req));
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
                    json = SolrIndexer.changeStateInBatch(js, UserServlet.getUserName(req));
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
                    json.put("user", UserServlet.getUser(req));
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
        GET_AUT_BY_ID {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    json = IndexSearcher.getAutById(req.getParameter("id"));
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
