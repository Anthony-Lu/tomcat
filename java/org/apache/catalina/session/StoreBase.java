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

package org.apache.catalina.session;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Store;
import org.apache.catalina.util.LifecycleBase;
import org.apache.tomcat.util.res.StringManager;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;

/**
 * Abstract implementation of the Store interface to
 * support most of the functionality required by a Store.
 *
 * @author Bip Thelin
 */
public abstract class StoreBase extends LifecycleBase implements Store {

    // ----------------------------------------------------- Instance Variables

    /**
     * Name to register for this Store, used for logging.
     */
    protected static final String storeName = "StoreBase";
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);
    /**
     * The property change support for this component.
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);
    /**
     * The Manager with which this JDBCStore is associated.
     */
    protected Manager manager;


    // ------------------------------------------------------------- Properties

    /**
     * Return the name for this Store, used for logging.
     */
    public String getStoreName() {
        return(storeName);
    }

    /**
     * Return the Manager with which the Store is associated.
     */
    @Override
    public Manager getManager() {
        return(this.manager);
    }

    /**
     * Set the Manager with which this Store is associated.
     *
     * @param manager The newly associated Manager
     */
    @Override
    public void setManager(Manager manager) {
        Manager oldManager = this.manager;
        this.manager = manager;
        support.firePropertyChange("manager", oldManager, this.manager);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Add a property change listener to this component.
     *
     * @param listener a value of type 'PropertyChangeListener'
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    // --------------------------------------------------------- Protected Methods

    /**
     * Called by our background reaper thread to check if Sessions
     * saved in our store are subject of being expired. If so expire
     * the Session and remove it from the Store.
     *
     */
    public void processExpires() {
        String[] keys = null;

        if(!getState().isAvailable()) {
            return;
        }

        try {
            keys = keys();
        } catch (IOException e) {
            manager.getContext().getLogger().error("Error getting keys", e);
            return;
        }
        if (manager.getContext().getLogger().isDebugEnabled()) {
            manager.getContext().getLogger().debug(getStoreName()+ ": processExpires check number of " + keys.length + " sessions" );
        }

        long timeNow = System.currentTimeMillis();

        for (int i = 0; i < keys.length; i++) {
            try {
                StandardSession session = (StandardSession) load(keys[i]);
                if (session == null) {
                    continue;
                }
                int timeIdle = (int) ((timeNow - session.getThisAccessedTime()) / 1000L);
                if (timeIdle < session.getMaxInactiveInterval()) {
                    continue;
                }
                if (manager.getContext().getLogger().isDebugEnabled()) {
                    manager.getContext().getLogger().debug(getStoreName()+ ": processExpires expire store session " + keys[i] );
                }
                boolean isLoaded = false;
                if (manager instanceof PersistentManagerBase) {
                    isLoaded = ((PersistentManagerBase) manager).isLoaded(keys[i]);
                } else {
                    try {
                        if (manager.findSession(keys[i]) != null) {
                            isLoaded = true;
                        }
                    } catch (IOException ioe) {
                        // Ignore - session will be expired
                    }
                }
                if (isLoaded) {
                    // recycle old backup session
                    session.recycle();
                } else {
                    // expire swapped out session
                    session.expire();
                }
                remove(keys[i]);
            } catch (Exception e) {
                manager.getContext().getLogger().error("Session: "+keys[i]+"; ", e);
                try {
                    remove(keys[i]);
                } catch (IOException e2) {
                    manager.getContext().getLogger().error("Error removing key", e2);
                }
            }
        }
    }


    @Override
    protected void initInternal() {
        // NOOP
    }


    /**
     * Start this component and implement the requirements
     * of {@link LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop this component and implement the requirements
     * of {@link LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
    }


    @Override
    protected void destroyInternal() {
        // NOOP
    }


    /**
     * Return a String rendering of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (manager == null) {
            sb.append("Manager is null");
        } else {
            sb.append(manager);
        }
        sb.append(']');
        return sb.toString();
    }
}
