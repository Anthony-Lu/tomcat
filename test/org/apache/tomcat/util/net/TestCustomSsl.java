/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.jsse.TesterBug50640SslImpl;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.junit.Assume;
import org.junit.Test;

import javax.net.ssl.SSLException;
import java.io.File;
import java.net.SocketException;

import static org.junit.Assert.*;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
public class TestCustomSsl extends TomcatBaseTest {

    @Test
    public void testCustomSslImplementation() throws Exception {

        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        Assume.assumeFalse("This test is only for JSSE based SSL connectors",
                connector.getProtocolHandlerClassName().contains("Apr"));

        connector.setProperty("sslImplementationName",
                "org.apache.tomcat.util.net.jsse.TesterBug50640SslImpl");

        // This setting will break ssl configuration unless the custom
        // implementation is used.
        connector.setProperty(TesterBug50640SslImpl.PROPERTY_NAME,
                TesterBug50640SslImpl.PROPERTY_VALUE);

        connector.setProperty("sslProtocol", "tls");

        File keystoreFile =
            new File("test/org/apache/tomcat/util/net/localhost.jks");
        connector.setAttribute(
                "keystoreFile", keystoreFile.getAbsolutePath());

        connector.setSecure(true);
        connector.setProperty("SSLEnabled", "true");

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

    @Test
    public void testCustomTrustManager1() throws Exception {
        doTestCustomTrustManager(false);
    }

    @Test
    public void testCustomTrustManager2() throws Exception {
        doTestCustomTrustManager(true);
    }

    private void doTestCustomTrustManager(boolean serverTrustAll)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Assume.assumeTrue("SSL renegotiation has to be supported for this test",
                TesterSupport.isRenegotiationSupported(getTomcatInstance()));

        TesterSupport.configureClientCertContext(tomcat);

        // Override the defaults
        ProtocolHandler handler = tomcat.getConnector().getProtocolHandler();
        if (handler instanceof AbstractHttp11JsseProtocol) {
            ((AbstractHttp11JsseProtocol<?>) handler).setTruststoreFile(null);
        } else {
            // Unexpected
            fail("Unexpected handler type");
        }
        if (serverTrustAll) {
            tomcat.getConnector().setAttribute("trustManagerClassName",
                    "org.apache.tomcat.util.net.TesterSupport$TrustAllCerts");
        }

        // Start Tomcat
        tomcat.start();

        TesterSupport.configureClientSsl();

        // Unprotected resource
        ByteChunk res =
                getUrl("https://localhost:" + getPort() + "/unprotected");
        assertEquals("OK", res.toString());

        // Protected resource
        res.recycle();
        int rc = -1;
        try {
            rc = getUrl("https://localhost:" + getPort() + "/protected", res,
                null, null);
        } catch (SocketException se) {
            if (serverTrustAll) {
                fail(se.getMessage());
                se.printStackTrace();
            }
        } catch (SSLException he) {
            if (serverTrustAll) {
                fail(he.getMessage());
                he.printStackTrace();
            }
        }
        if (serverTrustAll) {
            assertEquals(200, rc);
            assertEquals("OK-" + TesterSupport.ROLE, res.toString());
        } else {
            assertTrue(rc != 200);
            assertEquals("", res.toString());
        }
    }
}
