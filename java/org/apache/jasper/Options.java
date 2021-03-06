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
package org.apache.jasper;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldCache;

import javax.servlet.jsp.tagext.TagLibraryInfo;
import java.io.File;
import java.util.Map;

/**
 * A class to hold all init parameters specific to the JSP engine.
 *
 * @author Anil K. Vijendran
 * @author Hans Bergsten
 * @author Pierre Delisle
 */
public interface Options {

    /**
     * Returns true if Jasper issues a compilation error instead of a runtime
     * Instantiation error if the class attribute specified in useBean action
     * is invalid.
     */
    boolean getErrorOnUseBeanInvalidClassAttribute();

    /**
     * Are we keeping generated code around?
     */
    boolean getKeepGenerated();

    /**
     * Returns true if tag handler pooling is enabled, false otherwise.
     */
    boolean isPoolingEnabled();

    /**
     * Are we supporting HTML mapped servlets?
     */
    boolean getMappedFile();

    /**
     * Should we include debug information in compiled class?
     */
    boolean getClassDebugInfo();

    /**
     * Background compile thread check interval in seconds
     */
    int getCheckInterval();

    /**
     * Is Jasper being used in development mode?
     */
    boolean getDevelopment();

    /**
     * Should we include a source fragment in exception messages, which could be displayed
     * to the developer ?
     */
    boolean getDisplaySourceFragment();

    /**
     * Is the generation of SMAP info for JSR45 debugging suppressed?
     */
    boolean isSmapSuppressed();

    /**
     * Indicates whether SMAP info for JSR45 debugging should be dumped to a
     * file.
     * Ignored if suppressSmap() is true.
     */
    boolean isSmapDumped();

    /**
     * Should white spaces between directives or actions be trimmed?
     */
    boolean getTrimSpaces();

    /**
     * Gets the class-id value that is sent to Internet Explorer when using
     * &lt;jsp:plugin&gt; tags.
     *
     * @return Class-id value
     */
    String getIeClassId();

    /**
     * What is my scratch dir?
     */
    File getScratchDir();

    /**
     * What classpath should I use while compiling the servlets
     * generated from JSP files?
     */
    String getClassPath();

    /**
     * Compiler to use.
     *
     * <p>
     * If <code>null</code> (the default), the java compiler from Eclipse JDT
     * project, bundled with Tomcat, will be used. Otherwise, the
     * <code>javac</code> task from Apache Ant will be used to call an external
     * java compiler and the value of this option will be passed to it. See
     * Apache Ant documentation for the possible values.
     */
    String getCompiler();

    /**
     * The compiler target VM, e.g. 1.8.
     */
    String getCompilerTargetVM();

    /**
     * The compiler source VM, e.g. 1.8.
     */
    String getCompilerSourceVM();

    /**
     * Jasper Java compiler class to use.
     */
    String getCompilerClassName();

    /**
     * The cache that maps URIs, resource paths and parsed TLD files for the
     * various tag libraries 'exposed' by the web application.
     * A tag library is 'exposed' either explicitly in
     * web.xml or implicitly via the uri tag in the TLD
     * of a taglib deployed in a jar file (WEB-INF/lib).
     *
     * @return the instance of the TldLocationsCache
     * for the web-application.
     */
    TldCache getTldCache();

    /**
     * Java platform encoding to generate the JSP
     * page servlet.
     */
    String getJavaEncoding();

    /**
     * The boolean flag to tell Ant whether to fork JSP page compilations.
     *
     * <p>
     * Is used only when Jasper uses an external java compiler (wrapped through
     * a <code>javac</code> Apache Ant task).
     */
    boolean getFork();

    /**
     * Obtain JSP configuration information specified in web.xml.
     */
    JspConfig getJspConfig();

    /**
     * Is generation of X-Powered-By response header enabled/disabled?
     */
    boolean isXpoweredBy();

    /**
     * Obtain a Tag Plugin Manager
     */
    TagPluginManager getTagPluginManager();

    /**
     * Indicates whether text strings are to be generated as char arrays.
     *
     * @return <code>true</code> if text strings are to be generated as char
     *         arrays, <code>false</code> otherwise
     */
    boolean genStringAsCharArray();

    /**
     * Modification test interval.
     */
    int getModificationTestInterval();


    /**
     * Re-compile on failure.
     */
    boolean getRecompileOnFail();

    /**
     * Is caching enabled (used for precompilation).
     */
    boolean isCaching();

    /**
     * The web-application wide cache for the TagLibraryInfo tag library
     * descriptors, used if {@link #isCaching()} returns <code>true</code>.
     *
     * <p>
     * Using this cache avoids the cost of repeating the parsing of a tag
     * library descriptor XML file (performed by TagLibraryInfoImpl.parseTLD).
     * </p>
     *
     * @return the Map(String uri, TagLibraryInfo tld) instance.
     */
    Map<String, TagLibraryInfo> getCache();

    /**
     * The maximum number of loaded jsps per web-application. If there are more
     * jsps loaded, they will be unloaded. If unset or less than 0, no jsps
     * are unloaded.
     */
    int getMaxLoadedJsps();

    /**
     * The idle time in seconds after which a JSP is unloaded.
     * If unset or less or equal than 0, no jsps are unloaded.
     */
    int getJspIdleTimeout();
}
