package org.librepilot.cloud;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.librepilot.cloud.uavsettings.UAVSettings;
import org.librepilot.cloud.uavtalk.UAVTalkXMLObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by marc on 23.08.2016.
 */
public class UAVSettingsConverter {
    private TreeMap<String, UAVTalkXMLObject> mapFrom;
    private TreeMap<String, UAVTalkXMLObject> mapTo;

    private StringBuilder toHash;
    private StringBuilder fromHash;

    private UAVSettings settings;


    public UAVSettingsConverter() {
        mapFrom = null;
        mapTo = null;
        settings = null;
    }

    public String getToHash() {
        if(toHash.length()>=8) {
            return toHash.toString().substring(0,8);
        }
        return "";
    }

    public String getFromHash() {
        if(fromHash.length()>=8) {
            return fromHash.toString().substring(0,8);
        }
        return "";
    }

    @JsonIgnore
    public TreeMap<String, UAVTalkXMLObject> getMapFrom() {
        return mapFrom;
    }
    @JsonIgnore
    public TreeMap<String, UAVTalkXMLObject> getMapTo() {
        return mapTo;
    }

    public UAVSettings getSettings() {
        return settings;
    }

    @JsonIgnore
    public boolean isInitialized() {
        return mapFrom != null && mapTo != null && settings.objects != null;
    }

    public boolean convert(InputStream fromStream, InputStream toStream, InputStream settingsStream) {
        try {
            fromHash = new StringBuilder("");
            toHash = new StringBuilder("");
            mapFrom = loadXmlObjects(fromStream, fromHash);
            mapTo = loadXmlObjects(toStream, toHash);
            settings = new UAVSettings(settingsStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isInitialized();
    }
    @JsonIgnore
    public String getXml() throws UnsupportedEncodingException, XMLStreamException {
        if(!isInitialized()) {
            return null;
        }
        OutputStream outputStream = new ByteArrayOutputStream();

        XMLStreamWriter out = XMLOutputFactory.newInstance().createXMLStreamWriter(new OutputStreamWriter(outputStream, "utf-8"));

        out.writeStartDocument();
        out.writeStartElement("uavobjects");

        out.writeStartElement("version");
        out.writeEndElement();

        out.writeStartElement("settings");
        out.writeStartElement("objects");
        out.writeAttribute("name", "testo");
        out.writeAttribute("id", "0x000000");
        out.writeStartElement("field");
        out.writeAttribute("name", "test");
        out.writeAttribute("values", "test,test");
        out.writeEndElement();
        out.writeEndElement();
        out.writeEndElement();

        out.writeEndElement();
        out.writeEndDocument();

        out.close();
        return outputStream.toString();

    }


    private TreeMap<String, UAVTalkXMLObject> loadXmlObjects(InputStream is, StringBuilder refHash) {
        System.out.println("starting load");

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

            refHash.append(H.bytesToHex(cumucrypt.digest()).toLowerCase());
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
}
