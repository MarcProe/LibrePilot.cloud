/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.librepilot.cloud.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.librepilot.cloud.serialize.UAVTalkFieldSerializer;
import org.librepilot.cloud.serialize.UAVTalkInstanceSerializer;
import org.librepilot.cloud.serialize.UAVTalkObjectSerializer;
import org.librepilot.cloud.uavtalk.UAVTalkMissingObjectException;
import org.librepilot.cloud.uavtalk.UAVTalkObject;
import org.librepilot.cloud.uavtalk.UAVTalkXMLObject;
import org.librepilot.cloud.uavtalk.device.FcDevice;
import org.librepilot.cloud.uavtalk.device.FcLogfileDevice;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class ReplayHandler extends AbstractHandler {
    private FcDevice mFcDevice;
    private boolean mShowSettings = true;
    private boolean mShowState = true;

    public ReplayHandler(boolean showSettings, boolean showState) {
        this.mFcDevice = null;
        mShowSettings = showSettings;
        mShowState = showState;
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        response.setCharacterEncoding("utf-8");
        response.setStatus(HttpServletResponse.SC_OK);

        if (request.getMethod().equals("POST")) {
            handlePost(target, baseRequest, request, response);
        } else if (request.getMethod().equals("GET")) {
            handleGet(target, baseRequest, request, response);
        }
        baseRequest.setHandled(true);
    }

    public void handleGet(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        PrintWriter out = response.getWriter();

        mFcDevice = new FcLogfileDevice("OP-2015-10-25_16-53-30.opl", "uavo/15.09-85efdd63.zip", true);
        mFcDevice.setGuiEventListener((FcDevice.EventListener) mFcDevice);
        mFcDevice.start();

        do {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
        } while(((FcLogfileDevice)mFcDevice).mState != FcDevice.GEL_STOPPED);

        List<String> targetObjects = Arrays.asList(target.split("/"));

        SortedMap<String, UAVTalkXMLObject> objects = new TreeMap<>();

        for (UAVTalkXMLObject xmlObj : mFcDevice.getObjectTree().getXmlObjects().values()) {
            if (targetObjects.size() == 0 || targetObjects.contains(xmlObj.getName())) {
                if (mShowSettings && xmlObj.isSettings() || mShowState && !xmlObj.isSettings()) {
                    mFcDevice.requestObject(xmlObj.getName());
                    objects.put(xmlObj.getName(), xmlObj);
                }
            }
        }

        SortedMap<String, UAVTalkObjectSerializer> uavObjects = new TreeMap<>();

        for (UAVTalkXMLObject xmlObj : objects.values()) {
            UAVTalkObjectSerializer os = new UAVTalkObjectSerializer();
            if (targetObjects.size() == 0 || targetObjects.contains(xmlObj.getName())) {
                UAVTalkObject obj = mFcDevice.getObjectTree().getObjectFromName(xmlObj.getName());
                for (int i = 0; i < obj.getInstances().values().size(); i++) {
                    UAVTalkInstanceSerializer is = new UAVTalkInstanceSerializer();
                    for (UAVTalkXMLObject.UAVTalkXMLObjectField xmlField : xmlObj.getFields().values()) {
                        UAVTalkFieldSerializer fs = new UAVTalkFieldSerializer();
                        boolean hasElements = true;
                        Object res = null;
                        for (String element : xmlField.getElements()) {
                            try {
                                res = mFcDevice.getObjectTree().getData(xmlObj.getName(), i, xmlField.getName(), element);
                                if (element == null || xmlField.getElements().size() == 1 && (element.equals("") || element.equals("0"))) {
                                    hasElements = false;
                                    fs.setElements(null);
                                    fs.setValue(res);
                                } else {
                                    fs.getElements().put(element, res);
                                    fs.setValue(null);
                                }
                            } catch (UAVTalkMissingObjectException e) {
                                e.printStackTrace();
                            }
                        }
                        if (hasElements) {
                            is.getFields().put(xmlField.getName(), fs);
                        } else {
                            is.getFields().put(xmlField.getName(), res);
                        }
                    }
                    os.getInstances().put(i, is);
                }
            }
            uavObjects.put(xmlObj.getName(), os);
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String callback = request.getParameter("callback");
        if (callback != null) {
            response.setContentType("application/javascript");
            out.println(callback + "(");
        } else {
            response.setContentType("application/json");
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        out.println(mapper.writeValueAsString(uavObjects));

        if (callback != null) {
            out.print(")");
        }

        out.println("");
    }

    public void handlePost(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        PrintWriter out = response.getWriter();
        BufferedReader br = request.getReader();
        String json = "";
        while (br.ready()) {
            json += br.readLine();
        }
        System.out.println(json);
        json = json.replaceFirst("json=", "");
        System.out.println(json);
        json = URLDecoder.decode(json, "utf-8");
        System.out.println(json);
        System.out.println();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(json);
        out.println(actualObj.asText());
        out.println(mapper.toString());
    }
}