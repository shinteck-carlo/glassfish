/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
/*
 * ReplicationWebEventPersistentManager.java
 *
 * Created on November 18, 2005, 3:38 PM
 *
 */

package org.glassfish.web.ha.session.management;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.WebContainer;
import com.sun.logging.LogDomains;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.glassfish.gms.bootstrap.GMSAdapterService;
import org.glassfish.ha.store.api.BackingStoreConfiguration;
import org.glassfish.ha.store.api.BackingStoreException;
import org.glassfish.ha.store.api.BackingStoreFactory;
import org.jvnet.hk2.annotations.Inject;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author Rajiv Mordani
 */
@Service
public class ReplicationWebEventPersistentManager extends ReplicationManagerBase
        implements WebEventPersistentManager {
    

    @Inject
    Habitat habitat;

    @Inject
    GMSAdapterService gmsAdapterService;

    String clusterName = "";

    String instanceName = "";

    /**
     * The logger to use for logging ALL web container related messages.
     */
    protected static final Logger _logger 
        = LogDomains.getLogger(ReplicationWebEventPersistentManager.class, LogDomains.WEB_LOGGER);    
    
    /**
     * The descriptive information about this implementation.
     */
    private static final String info = "ReplicationWebEventPersistentManager/1.0";


    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    protected static final String name = "ReplicationWebEventPersistentManager";    


    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Manager implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {

        return (this.info);

    }   
    
    /** Creates a new instance of ReplicationWebEventPersistentManager */
    public ReplicationWebEventPersistentManager() {
        super();
        _logger.info("ReplicationWebEventPersistentManager created");
    }
    
    /**
    * called from valve; does the save of session
    *
    * @param session 
    *   The session to store
    */    
    public void doValveSave(Session session) {
        _logger.info("in doValveSave");

            try {
                ReplicationStore replicationStore = (ReplicationStore) this.getStore();
                // update this one's cache
                //replicationStore.getSessions().put(session.getIdInternal(), session);
                replicationStore.doValveSave(session);
                _logger.info("FINISHED repStore.valveSave");
            } catch (Exception ex) {
                ex.printStackTrace();

                _logger.log(Level.FINE, "exception occurred in doValveSave id=" + session.getIdInternal(),
                                ex);
                
            }
    }
   

    //START OF 6364900
    public void postRequestDispatcherProcess(ServletRequest request, ServletResponse response) {
        Context context = (Context)this.getContainer();
        Session sess = this.getSession(request);
        
        if(sess != null) {         
            doValveSave(sess);            
        }
        return;
    }
    
    private Session getSession(ServletRequest request) {
        javax.servlet.http.HttpServletRequest httpReq =
            (javax.servlet.http.HttpServletRequest) request;
        javax.servlet.http.HttpSession httpSess = httpReq.getSession(false);
        if(httpSess == null) {
            return null;
        }
        String id = httpSess.getId();
        Session sess = null;
        try {
            sess = this.findSession(id);
        } catch (java.io.IOException ex) {}

        return sess;
    } 
    //END OF 6364900 
    
    //new code start    
    

    private static int NUMBER_OF_REQUESTS_BEFORE_FLUSH = 1000;
    volatile Map<String, String> removedKeysMap = new ConcurrentHashMap<String, String>();
    private static AtomicInteger requestCounter = new AtomicInteger(0);
    private static int _messageIDCounter = 0;
    private AtomicBoolean  timeToChange = new AtomicBoolean(false);
    
//    private DispatchThread dispatchThread = new DispatchThread();
    
//    private class DispatchThread implements Runnable {
//
//        private volatile boolean done = false;
//
//        private Thread thread;
//
//        private LinkedBlockingQueue<Object> queue;
//
//        public DispatchThread() {
//            this.queue = new LinkedBlockingQueue<Object>();
//            this.thread = new Thread(this);
//            this.thread.setDaemon(true);
//            thread.start();
//        }
//
//        public void wakeup() {
//            queue.add(new Object());
//        }
//
//        public void run() {
//            while (! done) {
//                try {
//                    Object ignorableToken = queue.take();
//                    flushAllIdsFromCurrentMap(false);
//                } catch (InterruptedException inEx) {
//                    this.done = true;
//                }
//            }
//        }
//
//    }
    
    //new code end



    // ------------------------------------------------------------- Properties


    /**
     * Return the descriptive short name of this Manager implementation.
     */
    public String getName() {

        return this.name;

    }

    /**
     * Back up idle sessions.
     * Hercules: modified method we do not want
     * background saves when we are using web-event persistence-frequency
     */
    protected void processMaxIdleBackups() {
        //this is a deliberate no-op for this manager
        return;
    }
    
    /**
     * Swap idle sessions out to Store if too many are active
     * Hercules: modified method
     */
    protected void processMaxActiveSwaps() {
        //this is a deliberate no-op for this manager
        return;
    }

    /**
     * Swap idle sessions out to Store if they are idle too long.
     */
    protected void processMaxIdleSwaps() {
        //this is a deliberate no-op for this manager
        return;
    }

    /**
     * Gracefully terminate the active use of the public methods of this
     * component.  This method should be the last one called on a given
     * instance of this component.
     * We use this here to insure that pool entries are cleaned up
     *
     * @exception IllegalStateException if this component has not been started
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */                               
    public void stop() throws LifecycleException {
        super.stop();
    }


    @Override
    public void createBackingStore(String persistenceType, String storeName) {
        _logger.info("Create backing store invoked with persistence type " + persistenceType + " and store name " + storeName);
        BackingStoreFactory factory = habitat.getComponent(BackingStoreFactory.class, "replication");
        BackingStoreConfiguration<String, SimpleMetadata> conf = new BackingStoreConfiguration<String, SimpleMetadata>();
        // config.getWebContainer().getSessionConfig().getSessionManager().getStoreProperties().getDirectory();

        if(gmsAdapterService.isGmsEnabled()) {
            clusterName = gmsAdapterService.getGMSAdapter().getClusterName();
            instanceName = gmsAdapterService.getGMSAdapter().getModule().getInstanceName();
        }
        conf.setStoreName(storeName)
                .setClusterName(clusterName)
                .setInstanceName(instanceName)
                .setStoreType(persistenceType)
                .setKeyClazz(String.class).setValueClazz(SimpleMetadata.class);


        try {
            _logger.info("About to create backing store " + conf);
            this.backingStore = factory.createBackingStore(conf);
        } catch (BackingStoreException e) {
            e.printStackTrace();  
        }
    }
}
