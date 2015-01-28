/**
 * Licensed to the Austrian Association for Software Tool Integration (AASTI)
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. The AASTI licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openengsb.core.services.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.Constants;
import org.openengsb.core.api.OsgiUtilsService;
import org.openengsb.core.api.model.ConnectorConfiguration;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.persistence.ConfigPersistenceService;
import org.openengsb.core.api.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class ConnectorManagerImpl implements ConnectorManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorManagerImpl.class);

    private ConnectorRegistrationManager registrationManager;
    private ConfigPersistenceService configPersistence;
    private OsgiUtilsService utilsService;

    public void init() {
    }

    @Override
    public String create(ConnectorDescription connectorDescription) {
        String id = UUID.randomUUID().toString();
        createWithId(id, connectorDescription);
        return id;
    }

    @Override
    public void createWithId(String id, ConnectorDescription connectorDescription) {
        checkForExistingServices(id);
        addDefaultLocations(id, connectorDescription);
        try {
            registrationManager.updateRegistration(id, connectorDescription);
        } catch (ConnectorValidationFailedException e) {
            throw new RuntimeException(e);
        }
        ConnectorConfiguration configuration = new ConnectorConfiguration(id, connectorDescription);
        try {
            configPersistence.persist(configuration);
        } catch (PersistenceException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void addDefaultLocations(String id, ConnectorDescription connectorDescription) {
        Map<String, Object> properties = connectorDescription.getProperties();
        if (properties.get("location.root") != null) {
            return;
        }
        Map<String, Object> copy = new HashMap<String, Object>(properties);
        copy.put("location.root", id);
        connectorDescription.setProperties(copy);
    }

    @Override
    public String forceCreate(ConnectorDescription connectorDescription) {
        String id = UUID.randomUUID().toString();
        forceCreateWithId(id, connectorDescription);
        return id;
    }

    @Override
    public void forceCreateWithId(String id, ConnectorDescription connectorDescription) {
        checkForExistingServices(id);
        registrationManager.forceUpdateRegistration(id, connectorDescription);
        ConnectorConfiguration configuration = new ConnectorConfiguration(id, connectorDescription);
        try {
            configPersistence.persist(configuration);
        } catch (PersistenceException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void checkForExistingServices(String id) {
        try {
            List<ConnectorConfiguration> list =
                configPersistence.load(ImmutableMap.of(Constants.CONNECTOR_PERSISTENT_ID, id));
            if (!list.isEmpty()) {
                throw new IllegalArgumentException("connector already exists");
            }
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(String id, ConnectorDescription connectorDescpription) {
        ConnectorDescription old = getOldConfig(id);
        try {
            registrationManager.updateRegistration(id, connectorDescpription);
        } catch (ConnectorValidationFailedException e) {
            throw new RuntimeException(e);
        }
        applyConfigChanges(old, connectorDescpription);
        try {
            configPersistence.persist(new ConnectorConfiguration(id, connectorDescpription));
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void forceUpdate(String id, ConnectorDescription connectorDescription) throws IllegalArgumentException {
        ConnectorDescription old = getOldConfig(id);
        registrationManager.forceUpdateRegistration(id, connectorDescription);
        applyConfigChanges(old, connectorDescription);
        try {
            configPersistence.persist(new ConnectorConfiguration(id, connectorDescription));
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    private void applyConfigChanges(ConnectorDescription old, ConnectorDescription diff) {
        Map<String, String> updatedAttributes = updateAttributes(old.getAttributes(), diff.getAttributes());
        old.setAttributes(updatedAttributes);
        updateProperties(old.getProperties(), diff.getProperties());
    }

    private void updateProperties(Map<String, Object> properties, Map<String, Object> diff) {
        properties.putAll(diff);
    }

    private Map<String, String> updateAttributes(Map<String, String> attributes, Map<String, String> diff) {
        Map<String, String> result = new HashMap<String, String>(attributes);
        result.putAll(diff);
        return result;
    }

    private ConnectorDescription getOldConfig(String id) {
        List<ConnectorConfiguration> list;
        try {
            list = configPersistence.load(ImmutableMap.of(Constants.CONNECTOR_PERSISTENT_ID, id));
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("no connector with id " + id + " found");
        }
        if (list.size() > 1) {
            throw new IllegalStateException("multiple connectors with id " + id + " found");
        }
        return list.get(0).getContent();
    }

    @Override
    public void delete(String id) throws PersistenceException {
        registrationManager.remove(id);
        configPersistence.remove(ImmutableMap.of(Constants.CONNECTOR_PERSISTENT_ID, id));
    }

    @Override
    public ConnectorDescription getAttributeValues(String id) {
        try {
            List<ConnectorConfiguration> list =
                configPersistence.load(ImmutableMap.of(Constants.CONNECTOR_PERSISTENT_ID, id));
            if (list.isEmpty()) {
                throw new IllegalArgumentException("no connector with metadata: " + id + " found");
            }
            if (list.size() < 1) {
                LOGGER.error("multiple values found for the same meta-data");
                throw new IllegalStateException("multiple connectors with metadata: " + id + " found");
            }
            return list.get(0).getContent();
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean connectorExists(String id) {
        try {
            List<ConnectorConfiguration> list =
                configPersistence.load(ImmutableMap.of(Constants.CONNECTOR_PERSISTENT_ID, id));
            return !list.isEmpty();
        } catch (PersistenceException e) {
            throw new RuntimeException(e);
        }
    }

    public void setConfigPersistence(ConfigPersistenceService configPersistence) {
        this.configPersistence = configPersistence;
    }

    public void setRegistrationManager(ConnectorRegistrationManager registrationManager) {
        this.registrationManager = registrationManager;
    }

    protected OsgiUtilsService getUtilsService() {
        return utilsService;
    }

    public void setUtilsService(OsgiUtilsService utilsService) {
        this.utilsService = utilsService;
    }

}
