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
package org.apache.catalina.startup;

import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.ha.context.ReplicatedContext;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TestTomcat extends TomcatBaseTest {

    /**
     * Simple servlet to test in-line registration.
     */
    public static class HelloWorld extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse res)
                throws IOException {
            res.getWriter().write("Hello world");
        }
    }

    /**
     * Simple servlet to test the default session manager.
     */
    public static class HelloWorldSession extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse res)
                throws IOException {
            HttpSession s = req.getSession(true);
            s.getId();
            res.getWriter().write("Hello world");
        }
    }

    /**
     * Simple servlet to test JNDI
     */
    public static class HelloWorldJndi extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private static final String JNDI_ENV_NAME = "test";

        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse res)
                throws IOException {

            String name = null;

            try {
                Context initCtx = new InitialContext();
                Context envCtx = (Context) initCtx.lookup("java:comp/env");
                name = (String) envCtx.lookup(JNDI_ENV_NAME);
            } catch (NamingException e) {
                throw new IOException(e);
            }

            res.getWriter().write("Hello, " + name);
        }
    }

    /**
     * Servlet that tries to obtain a URL for WEB-INF/web.xml
     */
    public static class GetResource extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
            URL url = req.getServletContext().getResource("/WEB-INF/web.xml");

            res.getWriter().write("The URL obtained for /WEB-INF/web.xml was ");
            if (url == null) {
                res.getWriter().write("null");
            } else {
                res.getWriter().write(url.toString() + "\n");
                res.getWriter().write("The first 20 characters of that resource are:\n");

                // Read some content from the resource
                URLConnection conn = url.openConnection();

                char[] cbuf = new char[20];
                int read = 0;
                try (InputStream is = conn.getInputStream();
                        Reader reader = new InputStreamReader(is)) {
                    while (read < 20) {
                        int len = reader.read(cbuf, read, cbuf.length - read);
                        res.getWriter().write(cbuf, read, len);
                        read = read + len;
                    }
                }
            }
        }
    }

    /**
     * Simple servlet to test initialization of servlet instances.
     */
    private static class InitCount extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public void init() throws ServletException {
            super.init();
            callCount.incrementAndGet();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }

        public int getCallCount() {
            return callCount.intValue();
        }
    }


    /*
     * Start tomcat with a single context and one
     * servlet - all programmatic, no server.xml or
     * web.xml used.
     *
     * @throws Exception
     */
    @Test
    public void testProgrammatic() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "myServlet", new HelloWorld());
        ctx.addServletMapping("/", "myServlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("Hello world", res.toString());
    }

    @Test
    public void testSingleWebapp() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

    @Test
    public void testJsps() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(WsContextListener.class.getName());

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/examples/jsp/jsp2/el/basic-arithmetic.jsp");
        assertTrue(res.toString().indexOf("<td>${(1==2) ? 3 : 4}</td>") > 0);
    }

    @Test
    public void testSession() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "myServlet", new HelloWorldSession());
        ctx.addServletMapping("/", "myServlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("Hello world", res.toString());
    }

    @Test
    public void testLaunchTime() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        long t0 = System.currentTimeMillis();
        tomcat.addContext(null, "", ".");
        tomcat.start();
        log.info("Tomcat started in [" + (System.currentTimeMillis() - t0)
                + "] ms");
     }


    /*
     * Test for enabling JNDI.
     */
    @Test
    public void testEnableNaming() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext("", null);

        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextEnvironment environment = new ContextEnvironment();
        environment.setType("java.lang.String");
        environment.setName(HelloWorldJndi.JNDI_ENV_NAME);
        environment.setValue("Tomcat User");
        ctx.getNamingResources().addEnvironment(environment);

        Tomcat.addServlet(ctx, "jndiServlet", new HelloWorldJndi());
        ctx.addServletMapping("/", "jndiServlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("Hello, Tomcat User", res.toString());
    }

    /*
     * Test for enabling JNDI and using global resources.
     */
    @Test
    public void testEnableNamingGlobal() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext("", null);

        // Enable JNDI - it is disabled by default
        tomcat.enableNaming();

        ContextEnvironment environment = new ContextEnvironment();
        environment.setType("java.lang.String");
        environment.setName("globalTest");
        environment.setValue("Tomcat User");
        tomcat.getServer().getGlobalNamingResources().addEnvironment(environment);

        ContextResourceLink link = new ContextResourceLink();
        link.setGlobal("globalTest");
        link.setName(HelloWorldJndi.JNDI_ENV_NAME);
        ctx.getNamingResources().addResourceLink(link);

        Tomcat.addServlet(ctx, "jndiServlet", new HelloWorldJndi());
        ctx.addServletMapping("/", "jndiServlet");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("Hello, Tomcat User", res.toString());
    }


    /*
     * Test for https://bz.apache.org/bugzilla/show_bug.cgi?id=47866
     */
    @Test
    public void testGetResource() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        String contextPath = "/examples";

        File appDir = new File(getBuildDirectory(), "webapps" + contextPath);
        // app dir is relative to server home
        org.apache.catalina.Context ctx =
            tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        ctx.addApplicationListener(WsContextListener.class.getName());

        Tomcat.addServlet(ctx, "testGetResource", new GetResource());
        ctx.addServletMapping("/testGetResource", "testGetResource");

        tomcat.start();

        ByteChunk res = new ByteChunk();

        int rc =getUrl("http://localhost:" + getPort() + contextPath +
                "/testGetResource", res, null);
        assertEquals(HttpServletResponse.SC_OK, rc);
        assertTrue(res.toString().contains("<?xml version=\"1.0\" "));
    }

    @Test
    public void testBug50826() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        String contextPath = "/examples";

        File appDir = new File(getBuildDirectory(), "webapps" + contextPath);
        // app dir is relative to server home
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        Exception e = null;
        try {
            tomcat.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
            e = ex;
        }
        assertNull(e);
    }

    @Test
    public void testBug53301() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext("", null);

        InitCount initCount = new InitCount();
        Tomcat.addServlet(ctx, "initCount", initCount);
        ctx.addServletMapping("/", "initCount");

        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/");
        assertEquals("OK", res.toString());

        assertEquals(1, initCount.getCallCount());
    }

    @Test
    public void testGetWebappConfigFileFromDirectory() {
        Tomcat tomcat = new Tomcat();
        assertNotNull(tomcat.getWebappConfigFile("test/deployment/dirContext", ""));
    }

    @Test
    public void testGetWebappConfigFileFromDirectoryNegative() {
        Tomcat tomcat = new Tomcat();
        assertNull(tomcat.getWebappConfigFile("test/deployment/dirNoContext", ""));
    }

    @Test
    public void testGetWebappConfigFileFromJar() {
        Tomcat tomcat = new Tomcat();
        assertNotNull(tomcat.getWebappConfigFile("test/deployment/context.war", ""));
    }

    @Test
    public void testGetWebappConfigFileFromJarNegative() {
        Tomcat tomcat = new Tomcat();
        assertNull(tomcat.getWebappConfigFile("test/deployment/noContext.war", ""));
    }

    @Test
    public void testBug51526() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appFile = new File("test/deployment/context.war");
        StandardContext context = (StandardContext) tomcat.addWebapp(null, "/test",
                appFile.getAbsolutePath());

        tomcat.start();

        assertEquals("WAR_CONTEXT", context.getSessionCookieName());
    }

    @Test
    public void testGetDefaultContextPerAddWebapp() {
        Tomcat tomcat = getTomcatInstance();

        File appFile = new File("test/deployment/context.war");
        org.apache.catalina.Context context = tomcat.addWebapp(null,
                "/test", appFile.getAbsolutePath());

        assertEquals(StandardContext.class.getName(), context.getClass()
                .getName());
    }

    @Test
    public void testGetBrokenContextPerAddWepapp() {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass("InvalidContextClassName");
        }

        try {
            File appFile = new File("test/deployment/context.war");
            tomcat.addWebapp(null, "/test", appFile.getAbsolutePath());
            fail();
        } catch (IllegalArgumentException e) {
            // OK
        }
    }

    @Test
    public void testGetCustomContextPerAddWebappWithNullHost() {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class
                    .getName());
        }

        File appFile = new File("test/deployment/context.war");
        org.apache.catalina.Context context = tomcat.addWebapp(null, "/test",
                appFile.getAbsolutePath());

        assertEquals(ReplicatedContext.class.getName(), context.getClass()
                .getName());
    }

    @Test
    public void testGetCustomContextPerAddWebappWithHost() {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class
                    .getName());
        }

        File appFile = new File("test/deployment/context.war");
        org.apache.catalina.Context context = tomcat.addWebapp(host, "/test",
                appFile.getAbsolutePath());

        assertEquals(ReplicatedContext.class.getName(), context.getClass()
                .getName());
    }

        @Test
    public void testGetDefaultContextPerAddContext() {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext(null, "", null);
        assertEquals(StandardContext.class.getName(), ctx.getClass().getName());
    }

    @Test
    public void testGetBrokenContextPerAddContext() {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass("InvalidContextClassName");
        }

        // No file system docBase required
        try {
            tomcat.addContext(null, "", null);
            fail();
        } catch (IllegalArgumentException e) {
            // OK
        }
    }

    @Test
    public void testGetCustomContextPerAddContextWithHost() {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class
                    .getName());
        }

        // No file system docBase required
        org.apache.catalina.Context ctx = tomcat.addContext(host, "", null);
        assertEquals(ReplicatedContext.class.getName(), ctx.getClass()
                .getName());
    }

}
