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
package org.apache.jasper.runtime;

import org.apache.jasper.Constants;
import org.apache.jasper.el.ELContextImpl;
import org.apache.jasper.el.JasperELResolver;

import javax.el.*;
import javax.servlet.ServletContext;
import javax.servlet.jsp.JspApplicationContext;
import javax.servlet.jsp.JspContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of JspApplicationContext
 *
 * @author Jacob Hookom
 */
public class JspApplicationContextImpl implements JspApplicationContext {

    private static final String KEY = JspApplicationContextImpl.class.getName();

    private final ExpressionFactory expressionFactory =
            ExpressionFactory.newInstance();

    private final List<ELContextListener> contextListeners = new ArrayList<>();

    private final List<ELResolver> resolvers = new ArrayList<>();

    private boolean instantiated = false;

    private ELResolver resolver;

    public JspApplicationContextImpl() {

    }

    public static JspApplicationContextImpl getInstance(ServletContext context) {
        if (context == null) {
            throw new IllegalArgumentException("ServletContext was null");
        }
        JspApplicationContextImpl impl = (JspApplicationContextImpl) context
                .getAttribute(KEY);
        if (impl == null) {
            impl = new JspApplicationContextImpl();
            context.setAttribute(KEY, impl);
        }
        return impl;
    }

    @Override
    public void addELContextListener(ELContextListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("ELConextListener was null");
        }
        this.contextListeners.add(listener);
    }

    public ELContextImpl createELContext(JspContext context) {
        if (context == null) {
            throw new IllegalArgumentException("JspContext was null");
        }

        // create ELContext for JspContext
        final ELResolver r = this.createELResolver();
        ELContextImpl ctx;
        if (Constants.IS_SECURITY_ENABLED) {
            ctx = AccessController.doPrivileged(
                    new PrivilegedAction<ELContextImpl>() {
                        @Override
                        public ELContextImpl run() {
                            return new ELContextImpl(r);
                        }
                    });
        } else {
            ctx = new ELContextImpl(r);
        }
        ctx.putContext(JspContext.class, context);

        // alert all ELContextListeners
        ELContextEvent event = new ELContextEvent(ctx);
        for (int i = 0; i < this.contextListeners.size(); i++) {
            this.contextListeners.get(i).contextCreated(event);
        }

        return ctx;
    }

    private ELResolver createELResolver() {
        this.instantiated = true;
        if (this.resolver == null) {
            CompositeELResolver r = new JasperELResolver(this.resolvers,
                    expressionFactory.getStreamELResolver());
            this.resolver = r;
        }
        return this.resolver;
    }

    @Override
    public void addELResolver(ELResolver resolver) throws IllegalStateException {
        if (resolver == null) {
            throw new IllegalArgumentException("ELResolver was null");
        }
        if (this.instantiated) {
            throw new IllegalStateException(
                    "cannot call addELResolver after the first request has been made");
        }
        this.resolvers.add(resolver);
    }

    @Override
    public ExpressionFactory getExpressionFactory() {
        return expressionFactory;
    }

}
