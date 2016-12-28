package org.librepilot.cloud.uavsettings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by marc on 22.08.2016.
 */
public class UAVSettings {
    public Map<String, UAVSettingsObject> objects;

    public Collection<UAVSettingsObject> getObject() {
        return objects.values();
    }

    public String hw_rev;
    public String hw_serial;
    public String hw_type;
    public String fw_hash;
    public String fw_tag;
    public String fw_date;
    public String fw_uavo;
    public String gcs_hash;
    public String gcs_tag;
    public String gcs_date;
    public String gcs_uavo;


    @JsonIgnore
    public Map<String, UAVSettingsObject> getObjects() {
        return objects;
    }

    public UAVSettings(InputStream xml) {

        Document doc;
        objects = new TreeMap<>();

        /*
         <hardware revision="3" serial="1b0036000f47333039383532" type="9"/>
        <firmware hash="c3f2fd0b" tag="15.09" date="20151021 19:14" uavo="85efdd63"/>
        <gcs hash="c3f2fd0b" tag="15.09" date="20151021 19:14" uavo="85efdd63"/>
         */

        try {
            doc = loadXMLFromString(xml);

            hw_rev = ((Element)doc.getElementsByTagName("hardware").item(0)).getAttribute("revision");
            hw_serial = ((Element)doc.getElementsByTagName("hardware").item(0)).getAttribute("serial");
            hw_type = ((Element)doc.getElementsByTagName("hardware").item(0)).getAttribute("type");

            fw_hash = ((Element)doc.getElementsByTagName("firmware").item(0)).getAttribute("hash");
            fw_tag = ((Element)doc.getElementsByTagName("firmware").item(0)).getAttribute("tag");
            fw_date = ((Element)doc.getElementsByTagName("firmware").item(0)).getAttribute("date");
            fw_uavo = ((Element)doc.getElementsByTagName("firmware").item(0)).getAttribute("uavo");

            gcs_hash = ((Element)doc.getElementsByTagName("gcs").item(0)).getAttribute("hash");
            gcs_tag = ((Element)doc.getElementsByTagName("gcs").item(0)).getAttribute("tag");
            gcs_date = ((Element)doc.getElementsByTagName("gcs").item(0)).getAttribute("date");
            gcs_uavo = ((Element)doc.getElementsByTagName("gcs").item(0)).getAttribute("uavo");

            NodeList objNodeList = doc.getElementsByTagName("object");
            for (int i = 0; i < objNodeList.getLength(); i++) {
                Node obj = objNodeList.item(i);
                Element eObj = (Element)obj;

                UAVSettingsObject sObj = new UAVSettingsObject();
                sObj.name = eObj.getAttribute("name");
                sObj.id = eObj.getAttribute("id");

                NodeList fieldNodeList = eObj.getElementsByTagName("field");
                for(int j = 0; j < fieldNodeList.getLength(); j++) {
                    Node field = fieldNodeList.item(j);
                    Element eField = (Element)field;
                    UAVSettingsField sField = new UAVSettingsField();
                    sField.name = eField.getAttribute("name");
                    sField.values = eField.getAttribute("values");
                    sObj.fields.put(sField.name, sField);
                }

                objects.put(sObj.name, sObj);
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }
    }

    private static Document loadXMLFromString(InputStream xml)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(xml);
        return builder.parse(is);
    }
}
