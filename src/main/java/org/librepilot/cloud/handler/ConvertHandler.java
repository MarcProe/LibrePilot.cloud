package org.librepilot.cloud.handler;

import org.apache.commons.lang.StringEscapeUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
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
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        if (debug) {
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
            response.sendRedirect("/convert/?error=" + java.net.URLEncoder.encode(e.getClass().getSimpleName() + ": " + e.getMessage(), "utf-8"));
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

        if (debug && download) {
            debug = false;
        }

        String xml = "";
        String filename1 = "";
        String filename2 = "";

        if (!getFromSession) {

            Part objFrom = request.getPart("objfrom");
            Part objTo = request.getPart("objto");
            Part settingsFile = request.getPart("settings");


            String fromSourceName = "";//objFrom.getSubmittedFileName();
            String toSourceName = "";//objTo.getSubmittedFileName();

            InputStream fromStream;
            InputStream toStream;
            fromStream = null;//objFrom.getInputStream();
            toStream = null;//objTo.getInputStream();

            if (settingsFile.getSize() == 0) {
                throw new InvalidParameterException("No Settings");
            }

            String fromhash = request.getParameter("fromhash");
            String tohash = request.getParameter("tohash");


            UAVSettingsConverter sc = new UAVSettingsConverter();

            if (objFrom == null) {
                ByteArrayOutputStream outstr = new ByteArrayOutputStream();

                fromStream = Main.class.getClassLoader().getResourceAsStream("uavo/"+fromhash);
                fromSourceName = "Buildin " + fromhash;

            }

            if (objTo == null) {
                ByteArrayOutputStream outstr = new ByteArrayOutputStream();

                toStream = Main.class.getClassLoader().getResourceAsStream("uavo/"+tohash);
                toSourceName = "Buildin " + tohash;

            }

            System.out.println(fromhash + " " + tohash);

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
//                boolean objok = o.id.equals("0x" + xmlObjFrom.getId());
                String toId = xmlObjTo.getId();
                String fromId;
                String fromName;
                if (xmlObjFrom != null) {
                    fromId = xmlObjFrom.getId();
                    fromName = xmlObjFrom.getName();
                } else {
                    System.out.println("BAD. BAAAAD!!!!" + o.name);
                    continue;
                }
                String toName = xmlObjTo.getName();
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
                    } else {
                        values = f.values;
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
            filename2 = tohash;
            request.getSession().setAttribute("filename1", filename1);
            request.getSession().setAttribute("filename2", filename2);

        } else {
            xml = (String) request.getSession().getAttribute("xml");
            filename1 = (String) request.getSession().getAttribute("filename1");
            filename2 = (String) request.getSession().getAttribute("filename2");
        }

        if (download) {
            response.setContentType("application/xml");
            String filename = MessageFormat.format("{0}_{1}.uav", filename1, filename2);
            response.setHeader("Content-Disposition", MessageFormat.format("attachment; filename={0}", filename));
        } else {
            response.setContentType("text/html");
        }

        if (download) {
            out.println(xml);
        } else {
            request.getSession().setAttribute("xml", xml);
            xml = StringEscapeUtils.escapeHtml(xml);
            xml = xml.replaceAll("\r\n", "<br />").replaceAll("&lt;!--", "<i style=\"color:red\">&lt;!--").replaceAll("--&gt;", "--&gt;</i>").replaceAll("    ", "&nbsp;&nbsp;&nbsp;&nbsp;");

            out.println("<form method='POST' action='" + request.getRequestURL() + "'>" +
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
        if (error != null && !error.equals("")) {
            out.println("<p class=\"flow-text\"><b style=\"color:red\">" + error + "</b></p>");
        }
        out.println("<form method='post' action='/convert/' enctype='multipart/form-data'>");
        out.println("<p class=\"flow-text\" style=\"background-color:#eeeeff;margin:15px;\">");
        out.println("<div class=\"input-field col s12 flow-text\">\n" +
                "    <select id=\"fromhash\" name=\"fromhash\">\n");
        try {
            List<String> l = getResourceFiles("uavo/");
            for (String str : l) {
                out.println("<option value=\"" + str + "\">" + str + "</option>\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        out.println("    </select>\n" +
                "    <label class=\"flow-text\">Choose which version to convert from:</label>\n" +
                "  </div><br /></p>");
        out.println("<p class=\"flow-text\" style=\"background-color:#eeeeff;margin:15px;\">" +
         "<div class=\"input-field col s12 flow-text\">\n" +
                "    <select id= \"tohash\" name=\"tohash\">\n");
        try {
            List<String> l = getResourceFiles("uavo/");
            for (String str : l) {
                out.println("<option value=\"" + str + "\">" + str + "</option>\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        out.println("    </select>\n" +
                "    <label>Choose which version to convert to:</label>\n" +
                "  </div><br /></p>");

        out.println("<p style=\"background-color:#eeeeff;margin:15px;\" class=\"flow-text\">.uav settings file: " +
                "<input type='file' name='settings' size='60' /></p>" +
                "");
        out.println("<p class=\"flow-text\"><input type=\"checkbox\" value=\"true\" name=\"debug\" id=\"debug\" />" +
                "<label class=\"flow-text\" for=\"debug\">Show Debug </label></p>");
        out.println("<p class=\"flow-text\" style=\"margin:15px;\">" +
                "" +
                "" +
                "<input type='submit' value='Upload' />" +
                "" +
                "" +
                "</p>");
        out.println("</form>");

        out.println("<script type=\"text/javascript\" src=\"https://code.jquery.com/jquery-2.1.1.min.js\"></script>\n" +
                "      <script src=\"https://cdnjs.cloudflare.com/ajax/libs/materialize/0.97.7/js/materialize.min.js\"></script>\n" +
                "<script type=\"text/javascript\">$(document).ready(function() {\n" +
                "    $('select').material_select();\n" +
                "  });</script>" +
                "    </body>\n" +
                "  </html>");
    }

    private List<String> getResourceFiles(String path) throws IOException {
        List<String> filenames = new ArrayList<>();

        try (
                InputStream in = getResourceAsStream(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;

            while ((resource = br.readLine()) != null) {
                filenames.add(resource);
            }
        }

        return filenames;
    }

    private InputStream getResourceAsStream(String resource) {
        final InputStream in
                = getContextClassLoader().getResourceAsStream(resource);

        return in == null ? getClass().getResourceAsStream(resource) : in;
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

}
