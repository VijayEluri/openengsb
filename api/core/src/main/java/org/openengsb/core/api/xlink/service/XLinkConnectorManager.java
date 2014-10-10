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

package org.openengsb.core.api.xlink.service;

import java.util.List;

import org.openengsb.core.api.ConnectorManager;
// CHECKSTYLE:OFF
import org.openengsb.core.api.LinkingSupport;
// CHECKSTYLE:ON
import org.openengsb.core.api.model.ModelDescription;
import org.openengsb.core.api.xlink.model.ModelViewMapping;
import org.openengsb.core.api.xlink.model.XLinkConnectorRegistration;
import org.openengsb.core.api.xlink.model.XLinkConnectorView;

/**
 * This interface provides additional internal functionality to the ConnectorManager which is used for XLink by other
 * bundles of the Server
 */
public interface XLinkConnectorManager extends ConnectorManager {

    /**
     * Registers the connector with the given id with XLink.
     * 
     * @param connectorId the connector to register for XLink
     * @param remoteHostId the id of the host where the tool is running, e.g. host IP
     * @param toolName the name of the tool
     * @param modelViewMappings a list of models the tool can display and their associated views.
     */
    void registerWithXLink(String connectorId, String remoteHostId, String toolName,
            ModelViewMapping... modelViewMappings);

    /**
     * Unregisters the connector from XLink.
     * 
     * @param connectorId the connector to unregister
     */
    void unregisterFromXLink(String connectorId);

    /**
     * Returns a list of XLinkConnectorRegistration to a given remoteHostIp. If the remoteHostIp is unknown, returns an
     * empty list.
     */
    List<XLinkConnectorRegistration> getXLinkRegistrations(String remoteHostIp);

    /**
     * This method collects all views which can display the model object or a transformation of it and 
     * triggers the callback method {@link LinkingSupport#showXLinks(XLinkObject[])} of the connector 
     * identified with connectorId. 
     * 
     * @param connectorId the requestor's connector id.
     * @param context 
     * @param modelObject 
     * @param hostOnly <code>true</code> if only views local to the requestor are to be collected.
     * @return the XLink-URL
     */
    String requestXLinkSwitch(String connectorId, String context, Object modelObject, boolean hostOnly);

    /**
     * Calls {@link LinkingSupport#openXLink(ModelDescription, Object, XLinkConnectorView)} of the connector 
     * identified with connectorId.
     * 
     * @param connectorId
     * @param modelDescription
     * @param modelObject
     * @param view
     */
    void openXLink(String connectorId, ModelDescription modelDescription, Object modelObject, XLinkConnectorView view);
    
    /**
     * Generates the XLink-URI for the given model object.
     * 
     * @param connectorId the requestor's connector id.
     * @param context
     * @param modelObject the model object to generate an xlink for.
     * @return the XLink-URL
     */
    String generateXLink(String connectorId, String context, Object modelObject);
}
