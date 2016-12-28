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

package org.librepilot.cloud.uavtalk.device;

import org.librepilot.cloud.H;
import org.librepilot.cloud.Main;
import org.librepilot.cloud.VisualLog;
import org.librepilot.cloud.uavtalk.UAVTalkDeviceHelper;
import org.librepilot.cloud.uavtalk.UAVTalkObject;
import org.librepilot.cloud.uavtalk.UAVTalkObjectTree;
import org.librepilot.cloud.uavtalk.UAVTalkXMLObject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class FcDevice {

    public static final int GEL_STOPPED = 0;
    public static final byte UAVTALK_CONNECTED = 0x03;
    public static final byte UAVTALK_DISCONNECTED = 0x00;
    public static final byte UAVTALK_HANDSHAKE_ACKNOWLEDGED = 0x02;
    public static final byte UAVTALK_HANDSHAKE_REQUESTED = 0x01;
    public static final int GEL_PAUSED = 1;
    public static final int GEL_RUNNING = -1;
    private static final int MAX_HANDSHAKE_FAILURE_CYCLES = 3;
    public final Set<String> nackedObjects;
    volatile UAVTalkObjectTree mObjectTree;
    private int mFailedHandshakes = 0;
    private boolean mIsLogging = false;
    private long mLogBytesLoggedOPL = 0;
    private long mLogBytesLoggedUAV = 0;
    private String mLogFileName = "OP-YYYY-MM-DD_HH-MM-SS";
    private long mLogObjectsLogged = 0;
    private FileOutputStream mLogOutputStream;
    private long mLogStartTimeStamp;
    private int mUavTalkConnectionState = 0x00;
    FcDevice() throws IllegalStateException {
        nackedObjects = new HashSet<>();
    }

    public void setGuiEventListener(EventListener gel) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    public long getLogBytesLoggedOPL() {
        return mLogBytesLoggedOPL;
    }

    public long getLogBytesLoggedUAV() {
        return mLogBytesLoggedUAV;
    }

    public String getLogFileName() {
        return mLogFileName;
    }

    public long getLogObjectsLogged() {
        return this.mLogObjectsLogged;
    }

    public long getLogStartTimeStamp() {
        return this.mLogStartTimeStamp;
    }

    public UAVTalkObjectTree getObjectTree() {
        return mObjectTree;
    }

    public abstract boolean isConnected();

    public abstract boolean isConnecting();

    public boolean isLogging() {
        return this.mIsLogging;
    }

    public abstract void start();

    public abstract void stop();

    public boolean sendSettingsObject(String objectName, int instance, String fieldName,
                                      String element, byte[] newFieldData) {
        return sendSettingsObject(
                objectName,
                instance,
                fieldName,
                mObjectTree.getElementIndex(objectName, fieldName, element),
                newFieldData,
                false
        );
    }

    public boolean sendSettingsObject(String objectName, int instance, String fieldName,
                                      int element, byte[] newFieldData) {
        return sendSettingsObject(
                objectName,
                instance,
                fieldName,
                element,
                newFieldData,
                false
        );
    }

    protected abstract boolean writeByteArray(byte[] bytes);

    public abstract boolean sendAck(String objectId, int instance);

    public abstract boolean sendSettingsObject(String objectName, int instance);

    public abstract boolean sendSettingsObject(String objectName, int instance, String fieldName,
                                               int element, byte[] newFieldData,
                                               final boolean block);

    public abstract boolean requestObject(String objectName);

    public boolean requestMetaObject(String objectName) {
        try {
            UAVTalkXMLObject xmlObj = mObjectTree.getXmlObjects().get(objectName);
            if (xmlObj == null) {
                return false;
            }

            if (nackedObjects.contains(xmlObj.getId())) {
                VisualLog.d("NACKED META", xmlObj.getId());
                return false;   //if it was already nacked, don't try to get it again
                //If the original object was nacked, there is no metadata as well
            }

            //metadataid is id +1... yes, this is hacky.
            String metaId = H.intToHex((int) (Long.decode("0x" + xmlObj.getId()) + 1));
            //FIXME:Too hacky....

            byte[] send = UAVTalkObject.getReqMsg((byte) 0x21, metaId, 0);

            writeByteArray(send);
            return true;
        } catch (Exception e) {
            VisualLog.d("FcDevice", "Could not request MetaData for " + objectName);
            return false;
        }
    }


    public abstract boolean requestObject(String objectName, int instance);

    public void savePersistent(String saveObjectName) {
        mObjectTree.getObjectFromName("ObjectPersistence").setWriteBlocked(true);

        byte[] op = {0x02};
        UAVTalkDeviceHelper
                .updateSettingsObject(mObjectTree, "ObjectPersistence", 0, "Operation", 0, op);

        byte[] sel = {0x00};
        UAVTalkDeviceHelper
                .updateSettingsObject(mObjectTree, "ObjectPersistence", 0, "Selection", 0, sel);
        String sid = mObjectTree.getXmlObjects().get(saveObjectName).getId();

        byte[] oid = H.reverse4bytes(H.hexStringToByteArray(sid));

        UAVTalkDeviceHelper
                .updateSettingsObject(mObjectTree, "ObjectPersistence", 0, "ObjectID", 0, oid);

        //for the last things we set, we can just use the sendsettingsobject. It will call updateSettingsObjectDeprecated for the last field.
        byte[] ins = {0x00};
        UAVTalkDeviceHelper
                .updateSettingsObject(mObjectTree, "ObjectPersistence", 0, "InstanceID", 0, ins);

        sendSettingsObject("ObjectPersistence", 0);

        mObjectTree.getObjectFromName("ObjectPersistence").setWriteBlocked(false);
    }

    public boolean sendMetaObject(byte[] data) {

        if (data != null) {

            writeByteArray(data);

            return true;
        } else {
            return false;
        }
    }

    public void handleHandshake(byte flightTelemtryStatusField) {

        //if(SettingsHelper.mSerialModeUsed == MainActivity.SERIAL_LOG_FILE) {
        //    return;
        //}

        if (mFailedHandshakes > MAX_HANDSHAKE_FAILURE_CYCLES) {
            mUavTalkConnectionState = UAVTALK_DISCONNECTED;
            mFailedHandshakes = 0;
            //VisualLog.d("Handshake", "Setting DISCONNECTED " + mUavTalkConnectionState + " " + flightTelemtryStatusField);
        }

        if (mUavTalkConnectionState == UAVTALK_DISCONNECTED) {
            //Send Handshake initiator packet (HANDSHAKE_REQUEST)
            byte[] msg = new byte[1];
            msg[0] = UAVTALK_HANDSHAKE_REQUESTED;
            sendSettingsObject("GCSTelemetryStats", 0, "Status", 0, msg);
            mUavTalkConnectionState = UAVTALK_HANDSHAKE_REQUESTED;
            //VisualLog.d("Handshake", "Setting REQUESTED " + mUavTalkConnectionState + " " + flightTelemtryStatusField);
        } else if (flightTelemtryStatusField == UAVTALK_HANDSHAKE_ACKNOWLEDGED &&
                mUavTalkConnectionState == UAVTALK_HANDSHAKE_REQUESTED) {
            byte[] msg = new byte[1];
            msg[0] = UAVTALK_CONNECTED;
            sendSettingsObject("GCSTelemetryStats", 0, "Status", 0, msg);
            mUavTalkConnectionState = UAVTALK_CONNECTED;
            mFailedHandshakes++;
            //VisualLog.d("Handshake", "Setting CONNECTED " + mUavTalkConnectionState + " " + flightTelemtryStatusField);
        } else if (flightTelemtryStatusField == UAVTALK_CONNECTED &&
                mUavTalkConnectionState == UAVTALK_CONNECTED) {
            //We are connected, that is good.
            mFailedHandshakes = 0;
            //VisualLog.d("Handshake", "We're connected. How nice. " + mUavTalkConnectionState + " " + flightTelemtryStatusField);
        } else if (flightTelemtryStatusField == UAVTALK_CONNECTED) {
            //the fc thinks we are connected.
            mUavTalkConnectionState = UAVTALK_CONNECTED;
            mFailedHandshakes = 0;
            //VisualLog.d("Handshake", "The FC thinks we are connected." + mUavTalkConnectionState + " " + flightTelemtryStatusField);
        } else {
            mFailedHandshakes++;
            //VisualLog.d("Handshake", "Failed " + mUavTalkConnectionState + " " + flightTelemtryStatusField);
        }
        byte[] myb = new byte[1];
        myb[0] = (byte) mUavTalkConnectionState;
        UAVTalkDeviceHelper
                .updateSettingsObject(mObjectTree, "GCSTelemetryStats", 0, "Status", 0, myb);
        requestObject("FlightTelemetryStats");
    }

    public interface EventListener {
        void reportState(int i);

        void reportDataSource(String dataSource);

        void reportDataSize(float dataSize);

        void reportObjectCount(int objectCount);

        void reportRuntime(long ms);

        void incObjectsReceived(int objRec);

        void incObjectsSent(int objSent);

        void incObjectsBad(int ObjBad);
    }

    public boolean loadXmlObjects(String file) {
        System.out.println("starting load");
        VisualLog.d("TEST", "TEST");
        if (this.mObjectTree.getXmlObjects() == null) {
            this.mObjectTree.setXmlObjects(new TreeMap<String, UAVTalkXMLObject>());

            //String file = "uavo/15.09-85efdd63.zip";
            ZipInputStream zis = null;
            MessageDigest crypt;
            MessageDigest cumucrypt;
            try {
                InputStream is = null;
                try {
                    is = Main.class.getClassLoader().getResourceAsStream(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
                            this.mObjectTree.getXmlObjects().put(obj.getName(), obj);
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

            return true;
        }
        return false;
    }
}
