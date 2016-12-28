package org.librepilot.cloud.handler;

import org.apache.commons.lang.StringEscapeUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.librepilot.cloud.H;
import org.librepilot.cloud.Main;
import org.librepilot.cloud.UAVSettingsConverter;
import org.librepilot.cloud.VisualLog;
import org.librepilot.cloud.uavsettings.UAVSettings;
import org.librepilot.cloud.uavsettings.UAVSettingsField;
import org.librepilot.cloud.uavsettings.UAVSettingsObject;
import org.librepilot.cloud.uavtalk.UAVTalkXMLObject;
import org.xml.sax.SAXException;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by marc on 22.08.2016.
 */
public class ConvertHandler extends AbstractHandler {

    private static final Logger LOG = Log.getLogger(ConvertHandler.class);

    public ConvertHandler() {
        LOG.setDebugEnabled(true);
        LOG.debug("http://localhost:8080/convert startet!");
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
            response.sendRedirect("/convert/?error=" + java.net.URLEncoder.encode(e.getClass().getSimpleName() + ": " + e.getMessage(), "utf-8") );
            baseRequest.setHandled(true);
            return;
        }
    }

    private void handlePost(String s, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        PrintWriter out = response.getWriter();

        MultipartConfigElement multipartConfigElement = new MultipartConfigElement((String) null);
        request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);

        final boolean getFromSession = request.getParameter("getfromsession") != null && request.getParameter("getfromsession").equals("true");
        boolean download = request.getParameter("download") != null && request.getParameter("download").equals("true");
        boolean debug = request.getParameter("debug") != null && request.getParameter("debug").equals("true");

        if(debug && download) {
            debug = false;
        }

        String xml = "";
        String filename1 = "";
        String filename2 = "";

        if(!getFromSession) {

            Part objFrom = request.getPart("objfrom");
            Part objTo = request.getPart("objto");
            Part settingsFile = request.getPart("settings");


            String fromSourceName = objFrom.getSubmittedFileName();
            String toSourceName = objTo.getSubmittedFileName();

            InputStream fromStream;
            InputStream toStream;
            fromStream = objFrom.getInputStream();
            toStream = objTo.getInputStream();

            if (settingsFile.getSize() == 0) {
                throw new InvalidParameterException("No Settings");
            }

            String fromhash = request.getParameter("fromhash");
            String tohash = request.getParameter("tohash");



            UAVSettingsConverter sc = new UAVSettingsConverter();

            if (objFrom.getSize() == 0) {
                ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                if (fromhash == null || fromhash.equals("") || fromhash.equals("226f8c4c")) {
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
                        System.out.println(path);
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

                if (fromhash.equals("226f8c4c")) {  //load a static 15.09 to reduce traffic on bitbucket
                    fromStream = Main.class.getClassLoader().getResourceAsStream("uavo/15.09-85efdd63.zip");
                    fromSourceName = "Buildin uavo/15.09-85efdd63.zip";
                } else {
                    fromStream = new ByteArrayInputStream(outstr.toByteArray());
                    fromSourceName = MessageFormat.format("https://api.bitbucket.org/1.0/repositories/librepilot/librepilot/src/{0}/shared/uavobjectdefinition", fromhash);
                }
            }

            if (objTo.getSize() == 0) {
                ByteArrayOutputStream outstr = new ByteArrayOutputStream();
                if (tohash == null || tohash.equals("") || tohash.equals("a8f09d2")) {
                    tohash = "a8f09d2";  //16.09 RC1
                } else {
                    URL url = new URL(MessageFormat.format("https://api.bitbucket.org/1.0/repositories/librepilot/librepilot/src/{0}/shared/uavobjectdefinition", tohash));
                    LOG.debug(url.toExternalForm());
                    JSONTokener tokener = new JSONTokener(url.openStream());
                    JSONObject root = new JSONObject(tokener);
                    JSONArray files = ((JSONArray) root.get("files"));
                    ZipOutputStream zip = new ZipOutputStream(outstr);
                    zip.setLevel(Deflater.NO_COMPRESSION);

                    for (Object o : files) {
                        String path = (((JSONObject) o).get("path").toString());
                        System.out.println(path);
                        URL furl = new URL(MessageFormat.format("https://api.bitbucket.org/1.0/repositories/librepilot/librepilot/raw/{0}/{1}", tohash, path));
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
                }

                if (tohash.equals("a8f09d2")) {  //load a static 16.09 to reduce traffic on bitbucket
                    toStream = Main.class.getClassLoader().getResourceAsStream("uavo/16.09-RC1-4962b01c.zip");
                    toSourceName = "Buildin uavo/16.09-RC1-4962b01c.zip";
                } else {
                    toStream = new ByteArrayInputStream(outstr.toByteArray());
                    toSourceName = MessageFormat.format("https://api.bitbucket.org/1.0/repositories/librepilot/librepilot/src/{0}/shared/uavobjectdefinition", tohash);
                }
            }

            sc.convert(fromStream, toStream, settingsFile.getInputStream());

            TreeMap<String, UAVTalkXMLObject> mapFrom = sc.getMapFrom();
            TreeMap<String, UAVTalkXMLObject> mapTo = sc.getMapTo();
            UAVSettings settings = sc.getSettings();

            if (!sc.isInitialized()) {
                throw new IllegalStateException("Generic Problem: Some needed Object is null");
            }

            xml += "<!DOCTYPE UAVObjects>\r\n";
            xml += "<uavobjects>\r\n";
            xml += "    <version>\r\n";
            xml += MessageFormat.format("        <hardware revision=\"{0}\" serial=\"{1}\" type=\"{2}\"/>\r\n", settings.hw_rev, settings.hw_serial, settings.hw_type);
            xml += "        <firmware hash=\"" + tohash + "\" tag=\"\" date=\"\" uavo=\"" + sc.getToHash() + "\"/>\r\n";
            xml += "        <gcs hash=\"" + tohash + "\" tag=\"\" date=\"\" uavo=\"" + sc.getToHash() + "\"/>\r\n";
            xml += "        <!-- Converted by " + request.getRequestURL() + " -->\r\n";
            xml += "        <!-- Source: " + fromSourceName + " -->\r\n";
            xml += "        <!-- Target: " + toSourceName + " -->\r\n";
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
                xml += MessageFormat.format("        <object name=\"{0}\" id=\"0x{1}\">\r\n", toName, toId);
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
                    if (xmlFieldTo != null && xmlFieldTo.getType() == UAVTalkXMLObject.FIELDTYPE_ENUM) {
                        for (String value : f.values.split(",")) {
                            for (String option : xmlFieldTo.getOptions()) {
                                if (!value.equals(option) && value.toLowerCase().equals(option.toLowerCase())) {
                                    values += option + ",";
                                    corrected = true;
                                } else if (value.toLowerCase().equals(option.toLowerCase())) {
                                    values += value + ",";
                                }
                            }
                        }
                        if (values.length() > 0) {
                            values = values.substring(0, values.length() - 1);
                        }
                    }

                    String corCol = "";
                    if (corrected) {
                        corCol = " style=\"color:red\"";
                    }


                    if (!toFieldName.equals("")) {
                        if (corrected) {
                            xml += MessageFormat.format("            <!-- Corrected Values for {0} from {1} to {2} -->\r\n", f.name, f.values, values);
                        }

                        if (xmlFieldFrom != null && xmlFieldTo != null &&
                                xmlFieldFrom.getDefaults() != null && xmlFieldTo.getDefaults() != null &&
                                !Arrays.equals(xmlFieldFrom.getDefaults().toArray(), xmlFieldTo.getDefaults().toArray())) {
                            xml += MessageFormat.format("            <!-- Default Values for {0} changed from {1} to {2} -->\r\n",
                                    f.name, String.join(",", xmlFieldFrom.getDefaults()), String.join(",", xmlFieldTo.getDefaults()));
                        }
                        xml += MessageFormat.format("            <field name=\"{0}\" values=\"{1}\"/>\r\n", toFieldName, values);


                    } else {
                        xml += MessageFormat.format("            <!-- Removed Field {0} with Values {1} -->\r\n", fromFieldName, f.values);
                    }

                    println(MessageFormat.format("<tr><td>{0}</td><td></td><td>{1}</td><td>{3}</td><td>{2}</td><td" + corCol + ">{3}</td></tr>", fromFieldName, f.name, toFieldName, values), out, debug);


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
                    "</uavobjects>\r\n";

            println("</table>", out, debug);

            filename1 = settingsFile.getSubmittedFileName();
            filename2= tohash;
            request.getSession().setAttribute("filename1", filename1);
            request.getSession().setAttribute("filename2", filename2);

        } else {
            xml = (String)request.getSession().getAttribute("xml");
            filename1 = (String)request.getSession().getAttribute("filename1");
            filename2 = (String)request.getSession().getAttribute("filename2");
        }

        if (download) {
            response.setContentType("application/xml");
            String filename = MessageFormat.format("{0}_{1}.uav", filename1, filename2);
            response.setHeader("Content-Disposition", MessageFormat.format("attachment; filename={0}", filename));
        } else {
            response.setContentType("text/html");
        }

        if(download) {
            out.println(xml);
        } else {
            request.getSession().setAttribute("xml", xml);
            xml = StringEscapeUtils.escapeHtml(xml);
            xml = xml.replaceAll("\r\n", "<br />").replaceAll("&lt;!--", "<i style=\"color:red\">&lt;!--").replaceAll("--&gt;", "--&gt;</i>").replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");

            out.println("<form method='POST' action='"+request.getRequestURL()+"'>" +
                    "<input type='submit' name='click' value='Download' />" +
                    "<input type='hidden' name='download' value='true' />" +
                    "<input type='hidden' name='getfromsession' value='true' />" +
                    "</form>");

            out.println(xml);
        }
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
        out.println(" <!DOCTYPE html>\n" +
                "  <html>\n" +
                "    <head>\n" +
                "      <!--Import Google Icon Font-->\n" +
                "      <link href=\"http://fonts.googleapis.com/icon?family=Material+Icons\" rel=\"stylesheet\">\n" +
                "      <!--Import materialize.css-->\n" +
                "       <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/materialize/0.97.7/css/materialize.min.css\">\n" +
                "\n" +
                "      <!--Let browser know website is optimized for mobile-->\n" +
                "      <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n" +
                "    </head><body>");

        out.println("<h1><img src=\"https://forum.librepilot.org/librepilot_logo_forum.png\" /> <br />Settings conversion service</h1>");
        out.println("<h2>ALPHA / WORK IN PROGRESS</h2>");
        String error = request.getParameter("error");
        if(error != null && !error.equals("")) {
            out.println("<p class=\"flow-text\"><b style=\"color:red\">"+error+"</b></p>");
        }
        out.println("<form method='post' action='/convert/'  enctype='multipart/form-data'>");
        out.println("<p class=\"flow-text\" style=\"background-color:#eeeeff;margin:15px;\"><label class=\"flow-text\" for=\"objfrom\">Zipfile with current objects (<a href='https://bintray.com/marcproe/LP2Go-UAVO/download_file?file_path=zip%2F15.09-85efdd63.zip'>for example: 15.09</a>):</label> <input type='file' name='objfrom'' size='60' /><br />" +
                "<label class=\"flow-text\" for=\"fromhash\">or enter a commit hash to fetch that commit from bitbucket (takes +~20 seconds): </label><input type=\"text\" id= \"fromhash\" name=\"fromhash\" />"+
                "or leave emtpy to load 15.09 from a server-side archive<br /></p>");
        out.println("<p class=\"flow-text\" style=\"background-color:#eeeeff;margin:15px;\"><label class=\"flow-text\" for=\"objto\">Zipfile with target objects (<a href='https://bintray.com/package/files/marcproe/LP2Go-UAVO/uavo'>for example: next</a>):</label> <input type='file' name='objto' size='60' /><br />"+
                "<label class=\"flow-text\" for=\"tohash\">or enter a commit hash to fetch that commit from bitbucket (takes +~20 seconds): </label><input type=\"text\" id= \"tohash\" name=\"tohash\" />" +
                "or leave emtpy to load 16.09 RC1 from a server-side archive<br /></p>");
        out.println("<p style=\"background-color:#eeeeff;margin:15px;\" class=\"flow-text\">.uav settings file: <input type='file' name='settings' size='60' /></p>");
        out.println("<!--<p class=\"flow-text\"><input type=\"checkbox\" value=\"true\" name=\"download\" id=\"download\" /><label class=\"flow-text\" for=\"download\">Don't show, download as file </label></p>-->");
        out.println("<!--<p class=\"flow-text\"><input type=\"checkbox\" value=\"true\" name=\"debug\" id=\"debug\" /><label class=\"flow-text\" for=\"debug\">Show Debug </label></p>-->");
        out.println("<p class=\"flow-text\" style=\"margin:15px;\"><input type='submit' value='Upload' /></p>");
        out.println("</form>");

        out.println("<script type=\"text/javascript\" src=\"https://code.jquery.com/jquery-2.1.1.min.js\"></script>\n" +
                "      <script src=\"https://cdnjs.cloudflare.com/ajax/libs/materialize/0.97.7/js/materialize.min.js\"></script>\n" +
                "    </body>\n" +
                "  </html>");
    }
}
