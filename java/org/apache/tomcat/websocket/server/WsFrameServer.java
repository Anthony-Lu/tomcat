/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsFrameBase;
import org.apache.tomcat.websocket.WsSession;

import java.io.IOException;

public class WsFrameServer extends WsFrameBase {

    private final SocketWrapperBase<?> socketWrapper;
    private final Object connectionReadLock = new Object();


    public WsFrameServer(SocketWrapperBase<?> socketWrapper, WsSession wsSession,
            Transformation transformation) {
        super(wsSession, transformation);
        this.socketWrapper = socketWrapper;
    }


    /**
     * Called when there is data in the ServletInputStream to process.
     *
     * @throws IOException if an I/O error occurs while processing the available
     *                     data
     */
    public void onDataAvailable() throws IOException {
        synchronized (connectionReadLock) {
            while (isOpen() && socketWrapper.isReadyForRead()) {
                // Fill up the input buffer with as much data as we can
                int read = socketWrapper.read(
                        false, inputBuffer, writePos, inputBuffer.length - writePos);
                if (read <= 0) {
                    return;
                }
                writePos += read;
                processInputBuffer();
            }
        }
    }


    @Override
    protected boolean isMasked() {
        // Data is from the client so it should be masked
        return true;
    }


    @Override
    protected Transformation getTransformation() {
        // Overridden to make it visible to other classes in this package
        return super.getTransformation();
    }
}
