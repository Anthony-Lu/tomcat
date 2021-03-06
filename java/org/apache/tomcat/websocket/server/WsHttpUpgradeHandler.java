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

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;

import javax.servlet.http.HttpSession;
import javax.servlet.http.WebConnection;
import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet 3.1 HTTP upgrade handler for WebSocket connections.
 */
public class WsHttpUpgradeHandler implements InternalHttpUpgradeHandler {

    private static final Log log = LogFactory.getLog(WsHttpUpgradeHandler.class);
    private static final StringManager sm = StringManager.getManager(WsHttpUpgradeHandler.class);

    private final ClassLoader applicationClassLoader;

    private SocketWrapperBase<?> socketWrapper;

    private Endpoint ep;
    private EndpointConfig endpointConfig;
    private WsServerContainer webSocketContainer;
    private WsHandshakeRequest handshakeRequest;
    private List<Extension> negotiatedExtensions;
    private String subProtocol;
    private Transformation transformation;
    private Map<String,String> pathParameters;
    private boolean secure;
    private WebConnection connection;

    private WsRemoteEndpointImplServer wsRemoteEndpointServer;
    private WsFrameServer wsFrame;
    private WsSession wsSession;


    public WsHttpUpgradeHandler() {
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
    }


    @Override
    public void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    public void preInit(Endpoint ep, EndpointConfig endpointConfig,
            WsServerContainer wsc, WsHandshakeRequest handshakeRequest,
            List<Extension> negotiatedExtensionsPhase2, String subProtocol,
            Transformation transformation, Map<String,String> pathParameters,
            boolean secure) {
        this.ep = ep;
        this.endpointConfig = endpointConfig;
        this.webSocketContainer = wsc;
        this.handshakeRequest = handshakeRequest;
        this.negotiatedExtensions = negotiatedExtensionsPhase2;
        this.subProtocol = subProtocol;
        this.transformation = transformation;
        this.pathParameters = pathParameters;
        this.secure = secure;
    }


    @Override
    public void init(WebConnection connection) {
        if (ep == null) {
            throw new IllegalStateException(
                    sm.getString("wsHttpUpgradeHandler.noPreInit"));
        }

        String httpSessionId = null;
        Object session = handshakeRequest.getHttpSession();
        if (session != null ) {
            httpSessionId = ((HttpSession) session).getId();
        }

        // Need to call onOpen using the web application's class loader
        // Create the frame using the application's class loader so it can pick
        // up application specific config from the ServerContainerImpl
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            wsRemoteEndpointServer = new WsRemoteEndpointImplServer(socketWrapper, webSocketContainer);
            wsSession = new WsSession(ep, wsRemoteEndpointServer,
                    webSocketContainer, handshakeRequest.getRequestURI(),
                    handshakeRequest.getParameterMap(),
                    handshakeRequest.getQueryString(),
                    handshakeRequest.getUserPrincipal(), httpSessionId,
                    negotiatedExtensions, subProtocol, pathParameters, secure,
                    endpointConfig);
            wsFrame = new WsFrameServer(socketWrapper, wsSession, transformation);
            // WsFrame adds the necessary final transformations. Copy the
            // completed transformation chain to the remote end point.
            wsRemoteEndpointServer.setTransformation(wsFrame.getTransformation());
            ep.onOpen(wsSession, endpointConfig);
            webSocketContainer.registerSession(ep, wsSession);
        } catch (DeploymentException e) {
            throw new IllegalArgumentException(e);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    @Override
    public SocketState upgradeDispatch(SocketStatus status) {
        switch (status) {
            case OPEN_READ:
                try {
                    wsFrame.onDataAvailable();
                } catch (WsIOException ws) {
                    close(ws.getCloseReason());
                } catch (EOFException eof) {
                    CloseReason cr = new CloseReason(
                            CloseCodes.CLOSED_ABNORMALLY, eof.getMessage());
                    close(cr);
                } catch (IOException ioe) {
                    onError(ioe);
                    CloseReason cr = new CloseReason(
                            CloseCodes.CLOSED_ABNORMALLY, ioe.getMessage());
                    close(cr);
                }
                break;
            case OPEN_WRITE:
                wsRemoteEndpointServer.onWritePossible(false);
                break;
            case STOP:
                // TODO i18n
                CloseReason cr = new CloseReason(CloseCodes.GOING_AWAY, "");
                try {
                    wsSession.close(cr);
                } catch (IOException ioe) {
                    onError(ioe);
                    cr = new CloseReason(
                            CloseCodes.CLOSED_ABNORMALLY, ioe.getMessage());
                    close(cr);
                }
                break;
            case ASYNC_READ_ERROR:
            case ASYNC_WRITE_ERROR:
            case CLOSE_NOW:
            case DISCONNECT:
            case ERROR:
            case TIMEOUT:
                return SocketState.CLOSED;

        }
        return SocketState.UPGRADED;
    }


    @Override
    public void pause() {
        // NO-OP
    }


    @Override
    public void destroy() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.error(sm.getString("wsHttpUpgradeHandler.destroyFailed"), e);
            }
        }
    }


    private void onError(Throwable throwable) {
        // Need to call onError using the web application's class loader
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            ep.onError(wsSession, throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    private void close(CloseReason cr) {
        /*
         * Any call to this method is a result of a problem reading from the
         * client. At this point that state of the connection is unknown.
         * Attempt to send a close frame to the client and then close the socket
         * immediately. There is no point in waiting for a close frame from the
         * client because there is no guarantee that we can recover from
         * whatever messed up state the client put the connection into.
         */
        wsSession.onClose(cr);
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        // NO-OP. WebSocket has no requirement to access the TLS information
        // associated with the underlying connection.
    }
}
