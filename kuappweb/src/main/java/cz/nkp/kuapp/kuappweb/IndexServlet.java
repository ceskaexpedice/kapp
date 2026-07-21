package cz.nkp.kuapp.kuappweb;

import cz.nkp.kuapp.kuappweb.index.AUTIndexer;
import cz.nkp.kuapp.kuappweb.index.IndexSearcher;
import cz.nkp.kuapp.kuappweb.index.SolrIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
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
            String actionNameParam = request.getPathInfo().substring(1);
            if (actionNameParam != null) {
                Actions actionToDo = Actions.valueOf(actionNameParam.toUpperCase());
                if (requiresAuthentication(actionToDo) && UserServlet.getUser(request) == null) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                String requiredRole = requiredRole(actionToDo);
                if (requiredRole != null && !UserServlet.hasRole(request, requiredRole)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
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

    static boolean requiresAuthentication(Actions action) {
        return switch (action) {
            case USER, HOME, TITLES, TITLES_AUTHOR, TITLES_CONTRACT,
                    TITLES_CONTRACT_FIND, GET_DOC, GET_RELATED, AUTHORS,
                    LABELS, GET_BATCH_DOCS, GET_USED_BATCHES, CONTRACTS,
                    GET_CONTRACT, CHECK_CONTRACT_EXISTS, CONFIG, CHECK_AUTHOR,
                    GET_AUTHOR_BY_ID, GET_AUT_BY_ID, GET_LINKS -> false;
            default -> true;
        };
    }

    static String requiredRole(Actions action) {
        return switch (action) {
            case SAVE_DK, SAVE_LIBRARY_RIGHTS -> "kappAdmin";
            default -> null;
        };
    }

    private static boolean requireTitleAccess(HttpServletRequest request, HttpServletResponse response, String pid) {
        return requireTitleAccess(request, response, List.of(pid));
    }

    private static boolean requireTitleAccess(HttpServletRequest request, HttpServletResponse response, JSONArray pids) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < pids.length(); i++) {
            values.add(pids.optString(i, null));
        }
        return requireTitleAccess(request, response, values);
    }

    private static boolean requireTitleAccess(HttpServletRequest request, HttpServletResponse response, List<String> pids) {
        if (TitleAuthorization.canEditTitles(request, pids)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
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
        SAVE_LIBRARY_RIGHTS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONArray js = new JSONArray(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    json = SolrIndexer.indexLibraryRights(js);
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
                    if (!requireTitleAccess(req, resp, js.getString("pid"))) {
                        return null;
                    }
                    json = SolrIndexer.indexDocLabels(js, UserServlet.getUserName(req));
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
                    JSONArray docs = IndexSearcher.getLabelDocs(js.getString("label"))
                            .getJSONObject("response").getJSONArray("docs");
                    JSONArray pids = new JSONArray();
                    for (int i = 0; i < docs.length(); i++) {
                        pids.put(docs.getJSONObject(i).getString("pid"));
                    }
                    if (!requireTitleAccess(req, resp, pids)) {
                        return null;
                    }
                    json = SolrIndexer.createBatchFromLabel(js, UserServlet.getUserName(req));
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
                    JSONArray pids = js.getJSONArray("pids");
                    if (!requireTitleAccess(req, resp, pids)) {
                        return null;
                    }
                    // Koukneme na prvni
                    JSONObject t = IndexSearcher.getTitleByPid(pids.getString(0)).getJSONObject("response");
                    if (t.getInt("numFound") > 0) {
                        json = SolrIndexer.indexDocBatches(js, UserServlet.getUserName(req));
                    } else {
                        json.put("error", "pid_not_found");
                    }
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
                    if (!requireTitleAccess(req, resp, js.getJSONArray("pids"))) {
                        return null;
                    }
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
                    if (!TitleAuthorization.hasLibraryRole(req)) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        return null;
                    }
                    json = SolrIndexer.saveAuthor(js, false, UserServlet.getUserName(req));
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
                    if (!TitleAuthorization.hasLibraryRole(req)) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        return null;
                    }
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
                    if (!requireTitleAccess(req, resp, js.getString("pid"))) {
                        return null;
                    }
                    json = SolrIndexer.saveAuthorsInTitle(js, UserServlet.getUserName(req)); 
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_LAST_YEAR_DEATH {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    if (!requireTitleAccess(req, resp, js.getString("pid"))) {
                        return null;
                    }
                    json = SolrIndexer.saveLastYearDeath(js, UserServlet.getUserName(req));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_TYP_DILA {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    if (!requireTitleAccess(req, resp, js.getString("pid"))) {
                        return null;
                    }
                    json = SolrIndexer.saveTypDila(js, UserServlet.getUserName(req));
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
                    if (!requireTitleAccess(req, resp, js.getString("pid"))) {
                        return null;
                    }
                    json = SolrIndexer.saveTitleNote(js, UserServlet.getUserName(req));
                } catch (JSONException ex) {
                    json.put("error", ex.toString());
                }
                return json;
            }
        },
        SAVE_DATE_ISSUED {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse resp) throws Exception {
                JSONObject json = new JSONObject();
                try {
                    JSONObject js = new JSONObject(IOUtils.toString(req.getInputStream(), "UTF-8"));
                    if (!requireTitleAccess(req, resp, js.getString("pid"))) {
                        return null;
                    }
                    json = SolrIndexer.saveDateIssued(js, UserServlet.getUserName(req));
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

                List<String> pids = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream()))) {
                    String pid;
                    while ((pid = reader.readLine()) != null) {
                        if (!pid.isBlank()) {
                            pids.add(pid.trim());
                        }
                    }
                }
                if (!requireTitleAccess(req, resp, pids)) {
                    return null;
                }
                for (String pid : pids) {
                    ret = SolrIndexer.addDocToBatch(pid, batch, UserServlet.getUserName(req));
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
                if (UserServlet.getUser(req) == null) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return null;
                }

                JSONObject ret = new JSONObject();
                try {
                    //for (Part part : request.getParts()) {
                    Part part = req.getPart("file");
                    String filename = sanitizeUploadedFilename(part.getSubmittedFileName());
                    byte[] pdfBytes = part.getInputStream().readAllBytes();

                    Path contractsDirectory = Paths.get(InitServlet.CONFIG_DIR, "contracts").toAbsolutePath().normalize();
                    Path targetFile = contractsDirectory.resolve(filename).normalize();
                    if (!targetFile.startsWith(contractsDirectory)) {
                        throw new SecurityException("Invalid contract filename");
                    }

                    try (OutputStream outStream = Files.newOutputStream(targetFile)) {
                        outStream.write(pdfBytes);
                    }
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
                    List<String> pids = new ArrayList<>();
                    pids.add(js.getString("pid"));
                    String model = js.optString("model", "");
                    if (model.equals("periodicalvolume") || model.equals("monographunit")) {
                        JSONObject titleResponse = IndexSearcher.getTitleByPid(js.getString("pid"))
                                .getJSONObject("response");
                        if (titleResponse.getInt("numFound") > 0) {
                            String rootPid = titleResponse.getJSONArray("docs").getJSONObject(0)
                                    .optString("root_pid", null);
                            if (rootPid != null) {
                                pids.add(rootPid);
                            }
                        }
                    }
                    if (!requireTitleAccess(req, resp, pids)) {
                        return null;
                    }
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
                    JSONArray docs = IndexSearcher.getBatchDocs(js.getString("batch"), true)
                            .getJSONObject("response").getJSONArray("docs");
                    JSONArray pids = new JSONArray();
                    for (int i = 0; i < docs.length(); i++) {
                        pids.put(docs.getJSONObject(i).getString("pid"));
                    }
                    if (!requireTitleAccess(req, resp, pids)) {
                        return null;
                    }
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

    static String sanitizeUploadedFilename(String submittedFilename) {
        if (submittedFilename == null || submittedFilename.isBlank()) {
            throw new IllegalArgumentException("Contract filename is missing");
        }

        String normalized = submittedFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_");

        if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
            throw new IllegalArgumentException("Contract filename is invalid");
        }
        return filename;
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
