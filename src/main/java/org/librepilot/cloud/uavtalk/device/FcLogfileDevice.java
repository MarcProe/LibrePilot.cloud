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

import org.librepilot.cloud.uavtalk.UAVTalkObjectTree;

public class FcLogfileDevice extends FcDevice implements FcDevice.EventListener{


    private final FcWaiterThread mWaiterThread;
    public int mState;

    public FcLogfileDevice(String filename, String defFileName, boolean skipall) throws IllegalStateException {
        super();

        mObjectTree = new UAVTalkObjectTree();
        loadXmlObjects(defFileName);

        mWaiterThread = new FcLogfileWaiterThread(this, filename);
        if(skipall) {
            setSkip(999999);
        }
    }

    public void setSkip(int skip) {
        if (mWaiterThread != null) {
            ((FcLogfileWaiterThread) mWaiterThread).mSkipForward = skip;
        }
    }

    @Override
    public boolean isConnected() {
        return mWaiterThread != null;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public void start() {
        mWaiterThread.start();
    }

    @Override
    public void stop() {
        synchronized (mWaiterThread) {
            mWaiterThread.stopThread();
        }
    }

    @Override
    public boolean sendAck(String objectId, int instance) {
        return true;
    }

    @Override
    public boolean sendSettingsObject(String objectName, int instance) {
        return true;
    }

    @Override
    public boolean sendSettingsObject(String objectName, int instance, String fieldName, int element, byte[] newFieldData, boolean block) {
        return true;
    }

    @Override
    public boolean requestObject(String objectName) {
        return true;
    }

    @Override
    public boolean requestObject(String objectName, int instance) {
        return true;
    }

    @Override
    protected boolean writeByteArray(byte[] bytes) {
        return true;
    }

    @Override
    public void setGuiEventListener(EventListener gel) {
        mWaiterThread.setGuiEventListener(gel);
    }

    public boolean isPaused() {
        return ((FcLogfileWaiterThread) mWaiterThread).isPaused();
    }

    public void setPaused(boolean p) {
        ((FcLogfileWaiterThread) mWaiterThread).setPaused(p);
    }

    @Override
    public void reportState(int i) {
        mState = i;
    }

    @Override
    public void reportDataSource(String dataSource) {

    }

    @Override
    public void reportDataSize(float dataSize) {

    }

    @Override
    public void reportObjectCount(int objectCount) {

    }

    @Override
    public void reportRuntime(long ms) {

    }

    @Override
    public void incObjectsReceived(int objRec) {

    }

    @Override
    public void incObjectsSent(int objSent) {

    }

    @Override
    public void incObjectsBad(int ObjBad) {

    }
}
