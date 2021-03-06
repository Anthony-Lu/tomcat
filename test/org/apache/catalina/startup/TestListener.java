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

import org.apache.catalina.Context;
import org.junit.Test;

import javax.servlet.*;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestListener extends TomcatBaseTest {

    /*
     * Check that a ServletContainerInitializer can install a
     * {@link ServletContextListener} and that it gets initialized.
     * @throws Exception
     */
    @Test
    public void testServletContainerInitializer() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context context = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        context.addServletContainerInitializer(new SCI(), null);
        tomcat.start();
        assertTrue(SCL.initialized);
    }

    /*
     * Check that a {@link ServletContextListener} cannot install a
     * {@link ServletContainerInitializer}.
     * @throws Exception
     */
    @Test
    public void testServletContextListener() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context context = tomcat.addContext("",
                System.getProperty("java.io.tmpdir"));

        // SCL2 pretends to be in web.xml, and tries to install a
        // ServletContextInitializer.
        context.addApplicationListener(SCL2.class.getName());
        tomcat.start();

        //check that the ServletContextInitializer wasn't initialized.
        assertFalse(SCL3.initialized);
    }

    public static class SCI implements ServletContainerInitializer {

        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx)
                throws ServletException {
            ctx.addListener(new SCL());
        }
    }

    public static class SCL implements ServletContextListener {

        static boolean initialized = false;

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            initialized = true;
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NOOP
        }
    }

    public static class SCL2 implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServletContext sc = sce.getServletContext();
            sc.addListener(SCL3.class.getName());
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NOOP
        }
    }

    public static class SCL3 implements ServletContextListener {

        static boolean initialized = false;

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            initialized = true;
        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            // NOOP
        }
    }
}
