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
package org.apache.catalina.webresources;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.junit.Test;

import java.io.File;

public class TestJarResourceSetInternal extends AbstractTestResourceSet {

    @Override
    public WebResourceRoot getWebResourceRoot() {
        File f = new File("test/webresources/dir1-internal.jar");
        TesterWebResourceRoot root = new TesterWebResourceRoot();
        WebResourceSet webResourceSet =
                new JarResourceSet(root, "/", f.getAbsolutePath(), "/dir1");
        root.setMainResources(webResourceSet);
        return root;
    }

    @Override
    protected boolean isWriteable() {
        return false;
    }

    @Override
    public String getBaseDir() {
        return "test/webresources";
    }

    @Override
    @Test
    public void testNoArgConstructor() {
        @SuppressWarnings("unused")
        Object obj = new JarResourceSet();
    }
}
