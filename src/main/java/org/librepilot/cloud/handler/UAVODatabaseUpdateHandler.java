package org.librepilot.cloud.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.librepilot.cloud.H;
import org.librepilot.cloud.VisualLog;
import org.librepilot.cloud.uavtalk.UAVTalkXMLObject;
import org.xml.sax.SAXException;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by marc on 22.08.2016.
 */
public class UAVODatabaseUpdateHandler extends AbstractHandler {
    //private static final Logger LOG = Log.getLogger(UAVODatabaseUpdateHandler.class);

    public UAVODatabaseUpdateHandler() {
        //LOG.setDebugEnabled(true);
        //LOG.debug("http://localhost:8080/uavodb startet!");
    }

    private void println(String s, PrintWriter out, boolean debug) {
        if(debug) {
            out.println(s);
        }
    }

    @Override
    public void handle(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            response.setCharacterEncoding("utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            if (request.getMethod().equals("GET")) {

                handleGet(s, baseRequest, request, response);

            } else if (request.getMethod().equals("POST")) {

                handlePost(s, baseRequest, request, response);

            }

            baseRequest.setHandled(true);

        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/uavodb/?error=" + java.net.URLEncoder.encode(e.getClass().getSimpleName() + ": " + e.getMessage(), "utf-8") );
            baseRequest.setHandled(true);
            return;
        }
    }

    private void handlePost(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        PrintWriter out = response.getWriter();

        MultipartConfigElement multipartConfigElement = new MultipartConfigElement((String) null);
        request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);

        /*

        boolean debug;
        final boolean download;

        Part objFrom = request.getPart("objfrom");
        Part objTo = request.getPart("objto");
        Part settingsFile = request.getPart("settings");

        if(settingsFile.getSize() == 0) {
            throw new InvalidParameterException("No Settings");
        }

        String fromhash = request.getParameter("fromhash");
        String tohash = request.getParameter("fromhash");

        debug = request.getParameter("debug") != null && request.getParameter("debug").equals("true");

        if(request.getParameter("download") != null && request.getParameter("download").equals("true")) {
            response.setContentType("application/xml");
            String filename = MessageFormat.format("{0}_{1}.uav", settingsFile.getSubmittedFileName(), objTo.getSubmittedFileName());
            response.setHeader("Content-Disposition", MessageFormat.format("attachment; filename={0}", filename));
            debug = false;
            download = true;
        } else {
            response.setContentType("text/html");
            download = false;
        }

        UAVSettingsConverter sc = new UAVSettingsConverter();

        if(objFrom.getSize() == 0) {
            ByteArrayOutputStream outstr = new ByteArrayOutputStream();
            if(fromhash == null || fromhash.equals("") || fromhash.equals("226f8c4c")) {
                fromhash = "226f8c4c";  //15.09
            } else {
                URL url = new URL(MessageFormat.format("https://api.bitbucket.org/1.0/repositories/librepilot/librepilot/src/{0}/shared/uavobjectdefinition", fromhash));
                LOG.debug(url.toExternalForm());
                JSONTokener tokener = new JSONTokener(url.openStream());
                JSONObject root = new JSONObject(tokener);
                JSONArray files = ((JSONArray) root.get("files"));
                ZipOutputStream zip = new ZipOutputStream(outstr);
                zip.setLevel(Deflater.NO_COMPRESSION);

                for (Object o : files) {
                    String path = (((JSONObject) o).get("path").toString());
                    URL furl = new URL(MessageFormat.format("https://api.bitbucket.org/1.0/repositories/librepilot/librepilot/raw/{0}/{1}", fromhash, path));
                    InputStream is = furl.openStream();

                    zip.putNextEntry(new ZipEntry(furl.getFile().substring(furl.getFile().lastIndexOf('/') + 1)));

                    int length;
                    byte[] b = new byte[256];

                    while ((length = is.read(b)) > 0) {
                        zip.write(b, 0, length);
                    }
                    zip.closeEntry();
                    is.close();

                }
                zip.close();

                ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(outstr.toByteArray()));
                //"uavo/15.09-85efdd63.zip"
            }
            final InputStream fromStream;
            if(fromhash.equals("226f8c4c")) {  //load a static 15.09 to reduce traffic on bitbucket
                fromStream = Main.class.getClassLoader().getResourceAsStream("uavo/15.09-85efdd63.zip");
            } else {
                fromStream = new ByteArrayInputStream(outstr.toByteArray());
            }
            sc.convert(fromStream, objTo.getInputStream(), settingsFile.getInputStream());

        } else {
            sc.convert(objFrom.getInputStream(), objTo.getInputStream(), settingsFile.getInputStream());
        }

        TreeMap<String, UAVTalkXMLObject> mapFrom = sc.getMapFrom();
        TreeMap<String, UAVTalkXMLObject> mapTo = sc.getMapTo();
        UAVSettings settings = sc.getSettings();

        if(!sc.isInitialized()) {
            throw new IllegalStateException("Generic Problem: Some needed Object is null");
        }

        String xml = "";
        xml += "<!DOCTYPE UAVObjects>\r\n";
        xml += "<uavobjects>\r\n";
        xml += "    <version>\r\n";
        xml += MessageFormat.format("        <hardware revision=\"{0}\" serial=\"{1}\" type=\"{2}\"/>\r\n", settings.hw_rev, settings.hw_serial, settings.hw_type);
        xml += "        <firmware hash=\""+tohash+"\" tag=\"\" date=\"\" uavo=\""+sc.getToHash()+"\"/>\r\n";
        xml += "        <gcs hash=\""+tohash+"\" tag=\"\" date=\"\" uavo=\""+sc.getToHash()+"\"/>\r\n";
        xml += "        <!-- Converted by " + request.getRequestURL() + " -->\r\n";
        xml += "        <!-- Source File: " + objFrom.getSubmittedFileName() + " -->\r\n";
        xml += "        <!-- Target File: " + objTo.getSubmittedFileName() + " -->\r\n";
        xml += "    </version>\r\n";
        xml += "    <settings>\r\n";


        println("<table>", out, debug);
        println(MessageFormat.format("<tr><th>{0}</th><th>{3}</th><th>{1}</th><th>{4}</th><th>{2}</th><th>{5}</th></tr>", "Name", "Name", "Name", "ID", "ID", "ID"), out, debug);
        for (UAVSettingsObject o : settings.objects.values()) {
            UAVTalkXMLObject xmlObjFrom = mapFrom.get(o.name);
            UAVTalkXMLObject xmlObjTo = mapTo.get(o.name);
            boolean objok = o.id.equals("0x" + xmlObjFrom.getId());
            String toId = xmlObjTo.getId();
            String fromId = xmlObjFrom.getId();
            String toName = xmlObjTo.getName();
            String fromName = xmlObjFrom.getName();
            String targetColor = "#880000";
            if (toId.equals(o.id)) {
                targetColor = "#008800";
            }
            xml += MessageFormat.format("<object name=\"{0}\" id=\"0x{1}\">\r\n", toName, toId);
            println(MessageFormat.format("<tr><th>{0}</th><th>0x{3}</th><th>{1}</th><th>{4}</th><th>{2}</th><th>0x{5}</th></tr>", fromName, o.name, toName, fromId, o.id, toId), out, debug);


            for (UAVTalkXMLObject.UAVTalkXMLObjectField f : xmlObjFrom.getFields().values()) {
                if (!o.fields.containsKey(f.getName())) {
                    xml += MessageFormat.format("            <!-- Missing Field {0} in settings file. Adding Field without Value to provoke an import error. -->\r\n", f.getName());
                    xml += MessageFormat.format("            <field name=\"{0}\" />\r\n", f.getName());
                    println(MessageFormat.format("<tr><td style='color:red'>{0}</td><td></td><td></td><td></td><td></td><td></td></tr>", f.getName()), out, debug);
                }
            }
            for (UAVSettingsField f : o.fields.values()) {
                UAVTalkXMLObject.UAVTalkXMLObjectField xmlFieldFrom = xmlObjFrom.getFields().get(f.name);
                UAVTalkXMLObject.UAVTalkXMLObjectField xmlFieldTo = xmlObjTo.getFields().get(f.name);

                String fromFieldName = "";
                String toFieldName = "";
                if (xmlFieldFrom != null) {
                    fromFieldName = xmlFieldFrom.getName();
                }
                if (xmlFieldTo != null) {
                    toFieldName = xmlFieldTo.getName();
                }

                boolean corrected = false;
                String values = "";
                if(xmlFieldTo != null && xmlFieldTo.getType() == UAVTalkXMLObject.FIELDTYPE_ENUM) {
                    for(String value : f.values.split(",")) {
                        for (String option : xmlFieldTo.getOptions()) {
                            if(!value.equals(option) && value.toLowerCase().equals(option.toLowerCase())) {
                                values += option + ",";
                                corrected = true;
                            } else if (value.toLowerCase().equals(option.toLowerCase())) {
                                values += value + ",";
                            }
                        }
                    }
                    if(values.length() > 0) {
                        values = values.substring(0, values.length()-1);
                    }
                }

                String corCol = "";
                if(corrected) {
                    corCol = " style=\"color:red\"";
                }


                if(!toFieldName.equals("")) {
                    if(corrected) {
                        xml += MessageFormat.format("            <!-- Corrected Values for {0} from {1} to {2} -->\r\n", f.name, f.values, values);
                    }

                    if(xmlFieldFrom!= null && xmlFieldTo != null &&
                            xmlFieldFrom.getDefaults() != null && xmlFieldTo.getDefaults() != null &&
                            !Arrays.equals(xmlFieldFrom.getDefaults().toArray(), xmlFieldTo.getDefaults().toArray())) {
                        xml += MessageFormat.format("            <!-- Default Values for {0} changed from {1} to {2} -->\r\n",
                                f.name, String.join(",", xmlFieldFrom.getDefaults()), String.join(",", xmlFieldTo.getDefaults()));
                    }
                    xml += MessageFormat.format("            <field name=\"{0}\" values=\"{1}\"/>\r\n", toFieldName, values);


                } else {
                    xml += MessageFormat.format("            <!-- Removed Field {0} with Values {1} -->\r\n", fromFieldName, f.values);
                }

                println(MessageFormat.format("<tr><td>{0}</td><td></td><td>{1}</td><td>{3}</td><td>{2}</td><td"+ corCol + ">{3}</td></tr>", fromFieldName, f.name, toFieldName, values), out, debug);


            }
            for (UAVTalkXMLObject.UAVTalkXMLObjectField f : xmlObjTo.getFields().values()) {
                if (!o.fields.containsKey(f.getName())) {
                    xml += MessageFormat.format("            <!-- New Field {0}-->\r\n", f.getName());
                    xml += MessageFormat.format("            <field name=\"{0}\" values=\"{1}\"/>\r\n", f.getName(), String.join(",", f.getDefaults()));
                    println(MessageFormat.format("<tr><td><td></td><td></td></td><td></td><td style='color:red'>{0}</td><td>{1}</td></tr>", f.getName(), String.join(",", f.getDefaults())), out, debug);
                }
            }
            xml += "        </object>\r\n";
        }
        xml += "    </settings>\r\n" +
                "      </uavobjects>\r\n";

        println("</table>", out, debug);

        if(download) {
            out.println(xml);
        } else {
            xml = StringEscapeUtils.escapeHtml(xml);
            out.println(xml.replaceAll("\r\n", "<br />").replaceAll("&lt;!--", "<i style=\"color:red\">&lt;!--").replaceAll("--&gt;", "--&gt;</i>").replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;"));
        }
*/
    }

    private TreeMap<String, UAVTalkXMLObject> loadXmlObjects(InputStream is) {
        //LOG.debug("starting load");

        TreeMap<String, UAVTalkXMLObject> xmlObjects = new TreeMap<>();

        ZipInputStream zis = null;
        MessageDigest crypt;
        MessageDigest cumucrypt;
        try {
            //openFileInput(UAVO_INTERNAL_PATH + getString(R.string.DASH) + file);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;

            //we need to sort the files to generate the correct hash
            SortedMap<String, String> files = new TreeMap<>();

            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().endsWith("xml")) {

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, count);
                    }

                    String xml = baos.toString();
                    files.put(ze.getName(), xml);

                    if (xml.length() > 0) {
                        UAVTalkXMLObject obj = new UAVTalkXMLObject(xml);
                        xmlObjects.put(obj.getName(), obj);
                    }
                }
            }

            crypt = MessageDigest.getInstance("SHA-1");     //single files hash
            cumucrypt = MessageDigest.getInstance("SHA-1"); //cumulative hash
            cumucrypt.reset();
            for (String xmle : files.values()) {            //cycle over the sorted files
                crypt.reset();
                crypt.update(xmle.getBytes());              //hash the file
                //update a hash over the file hash string representations (yes.)
                cumucrypt.update(H.bytesToHex(crypt.digest()).toLowerCase().getBytes()); //sic!
            }

            //mUavoLongHash = H.bytesToHex(cumucrypt.digest()).toLowerCase();
            VisualLog.d("SHA1", H.bytesToHex(cumucrypt.digest()).toLowerCase());

        } catch (IOException | SAXException
                | ParserConfigurationException | NoSuchAlgorithmException e) {
            VisualLog.e("UAVO", "UAVO Load Error", e);
        } finally {
            try {
                if (zis != null) {
                    zis.close();
                }
            } catch (IOException e) {
                VisualLog.e("LoadXML", "Exception on Close");
            }
        }
        return xmlObjects;

    }

    public void handleGet(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        PrintWriter out = response.getWriter();
        final File temp = Files.createTempDirectory("temp").toFile();
        try {

          /*  Git gitRepo = Git.cloneRepository().setURI("https://marcus_proest@bitbucket.org/librepilot/librepilot.git").setDirectory(temp).setNoCheckout(true).call();
            gitRepo.checkout().setName("next").addPath("shared/uavobjectdefinition/").call();


            Iterable<RevCommit> log = gitRepo.log().addPath("shared/uavobjectdefinition/").call();
            for(RevCommit r : log) {
                out.println(H.intToHex(r.hashCode()));
            }

            gitRepo.getRepository().close();*/

            //FileRepositoryBuilder builder = new FileRepositoryBuilder();
            //Repository repo = builder.setGitDir(new File("localrepositary"+"\\.git")).setMustExist(true).build();
            Git git = Git.cloneRepository().setURI("https://marcus_proest@bitbucket.org/librepilot/librepilot.git").setDirectory(temp).call();
            Iterable<RevCommit> log = git.log().call();
            for (Iterator<RevCommit> iterator = log.iterator(); iterator.hasNext();) {
                RevCommit rev = iterator.next();
                out.println(H.intToHex(rev.hashCode()));
            }



        } catch (GitAPIException e) {
            e.printStackTrace();
        }

//        out.println(log);


    }
}
