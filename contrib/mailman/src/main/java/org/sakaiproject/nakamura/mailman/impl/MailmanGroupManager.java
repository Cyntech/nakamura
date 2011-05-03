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
package org.sakaiproject.nakamura.mailman.impl;

import java.util.Arrays;
import java.util.logging.Level;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.user.AuthorizableEvent.Operation;
import org.sakaiproject.nakamura.mailman.MailmanManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Map;
import javax.jcr.RepositoryException;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;

import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;

@Component(immediate = true, metatype = true, label = "%mail.manager.impl.label", description = "%mail.manager.impl.desc")
@Service(value = EventHandler.class)
public class MailmanGroupManager implements EventHandler, ManagedService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailmanGroupManager.class);
    @SuppressWarnings("unused")
    @Property(value = "The Sakai Foundation")
    private static final String SERVICE_VENDOR = "service.vendor";
    @SuppressWarnings("unused")
    @Property(value = "Handles management of mailman integration")
    private static final String SERVICE_DESCRIPTION = "service.description";
    @SuppressWarnings("unused")
    @Property(value = {"org/sakaiproject/nakamura/lite/authorizables/ADDED"})
    private static final String EVENT_TOPICS = "event.topics";
    @Property(value = "password")
    private static final String LIST_MANAGEMENT_PASSWORD = "mailman.listmanagement.password";
    @Reference
    private MailmanManager mailmanManager;
    @Reference
    private Repository repository;
    private Session session; // fetched from the repository
    private AuthorizableManager authorizableManager = null; // fetchs from the session
    private String listManagementPassword;

    public MailmanGroupManager() {
    }

    public MailmanGroupManager(MailmanManager mailmanManager, Repository repository) {
        this.mailmanManager = mailmanManager;
        this.repository = repository;
    }

    @Activate
    public void activate(Map<?, ?> props) throws ClientPoolException, StorageClientException, AccessDeniedException {
        session = repository.loginAdministrative();
        authorizableManager = session.getAuthorizableManager();
    }

    @Deactivate
    public void deactivate() {
        try {
            session.logout();
        } catch (Exception e) {
            LOGGER.error("Error logging out of the session", e);
        } finally {
            session = null;
        }
    }

    public void handleEvent(Event event) {
        if (!event.getProperty("type").toString().equalsIgnoreCase("group")) {
            return; // we only need the events with type: group
        }
        LOGGER.info("Got event on topic: " + event.getTopic());

        Operation operation = null;
        if (event.getProperty("added") != null) {
            operation = Operation.join;
        } else if (event.getProperty("removed") != null) {
            operation = Operation.part;
        } else {
            operation = Operation.create;
        }

        String principalName = event.getProperty("path").toString();
        switch (operation) {
            case create:
                LOGGER.info("Got authorizable creation: " + principalName);

                //TODO not hardcode @example and write a comment
                try {
                    mailmanManager.createList(principalName, principalName + "@example.com", listManagementPassword);
                    mailmanManager.createList(principalName + "-managers", principalName + "-managers@example.com", listManagementPassword);
                } catch (Exception e) {
                    LOGGER.error("Unable to create mailman list for group", e);
                }
                break;
            case delete:
                LOGGER.info("Got authorizable deletion: " + principalName);
                try {
                    mailmanManager.deleteList(principalName, listManagementPassword);
                } catch (Exception e) {
                    LOGGER.error("Unable to delete mailman list for group", e);
                }
                break;
            case join: {
                LOGGER.info("Got group join event");
                String userId = event.getProperty("added").toString();
                String emailAddress = null;
                try {
                    if (userId.endsWith("-managers")) {
                        emailAddress = userId + "@example.com";
                    } else {
                        emailAddress = getEmailForUser(userId);
                        if (emailAddress != null) {
                            LOGGER.info("Adding " + userId + " to mailman group " + principalName);
                        } else {
                            LOGGER.warn("No email address recorded for user: " + userId + ". Not adding to mailman list");
                        }
                    }
                    mailmanManager.addMember(principalName, listManagementPassword, emailAddress);
                } catch (RepositoryException ex) {
                    java.util.logging.Logger.getLogger(MailmanGroupManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (AccessDeniedException ex) {
                    java.util.logging.Logger.getLogger(MailmanGroupManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (StorageClientException ex) {
                    java.util.logging.Logger.getLogger(MailmanGroupManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (MailmanException e) {
                    LOGGER.error("Mailman exception adding user to mailman group", e);
                }
            }
            break;
            case part: {
                LOGGER.info("Got group join event");
                String userId = event.getProperty("removed").toString();
                try {
                    String emailAddress = getEmailForUser(userId);
                    if (emailAddress != null) {
                        LOGGER.info("Adding " + userId + " to mailman group " + principalName);
                        mailmanManager.removeMember(userId, listManagementPassword, emailAddress);
                    } else {
                        LOGGER.warn("No email address recorded for user: " + userId + ". Not removing from mailman list");
                    }
                } catch (AccessDeniedException ex) {
                    java.util.logging.Logger.getLogger(MailmanGroupManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (StorageClientException ex) {
                    java.util.logging.Logger.getLogger(MailmanGroupManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (RepositoryException e) {
                    LOGGER.error("Repository exception removing user from mailman group", e);
                } catch (MailmanException e) {
                    LOGGER.error("Mailman exception removing user from mailman group", e);
                }
            }
            break;
        }
    }

    private String getEmailForUser(String userId) throws RepositoryException, AccessDeniedException, StorageClientException {
        LOGGER.warn("ADC: getEmailForUser - userId: " + userId);
        Authorizable authorizable = authorizableManager.findAuthorizable(userId);
        LOGGER.warn("ADC: getEmailForUser" + Arrays.toString(authorizable.getPrincipals()) + " ... " + authorizable.getId());
        String email = (String) authorizable.getProperty("email");
        return email;
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary config) throws ConfigurationException {
        LOGGER.info("Got config update");
        listManagementPassword = (String) config.get(LIST_MANAGEMENT_PASSWORD);
    }

    protected void activate(ComponentContext componentContext) {
        LOGGER.info("Got component initialization");
        listManagementPassword = (String) componentContext.getProperties().get(LIST_MANAGEMENT_PASSWORD);
    }
}