/*******************************************************************************
 * This file is part of Vitam Project.
 *
 * Copyright Vitam (2012, 2016)
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL license as circulated
 * by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.processing.common.model;

import fr.gouv.vitam.processing.common.config.ServerConfiguration;

/**
 * WorkParams class
 *
 * Contains all useful parameters for the process engine and handler
 */

public class WorkParams {

    private String containerName;
    private String objectName;
    private ServerConfiguration serverConfiguration;
    private String objectId;
    private String workerGUID;
    private String metaDataRequest;
    private String currentStep;

    /**
     * @return currentStep
     */
    public String getCurrentStep() {
        return currentStep;
    }

    /**
     * @param currentStep
     * @return WorkParams
     */
    public WorkParams setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
        return this;
    }

    /**
     * getContainerName
     *
     * @return the container name of workparam
     */
    public String getContainerName() {
        if (containerName == null) {
            return "";
        }
        return containerName;
    }

    /**
     * setContainerName, set the name of container for WorkParams
     *
     * @param containerName as String
     * @return WorkParams instance with container name setted
     */
    public WorkParams setContainerName(String containerName) {
        this.containerName = containerName;
        return this;

    }

    /**
     * @return the objectId
     */
    public String getObjectId() {
        if (objectId == null) {
            return "";
        }
        return objectId;
    }

    /**
     * @param objectId the objectId to set
     *
     * @return WorkParams
     */
    public WorkParams setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    /**
     * @return the objectName
     */
    public String getObjectName() {
        if (objectName == null) {
            return "";
        }
        return objectName;
    }

    /**
     * @param objectName the objectName to set
     * @return WorkParams
     */
    public WorkParams setObjectName(String objectName) {
        this.objectName = objectName;
        return this;
    }

    /**
     * @return the metaDataRequest
     */
    public String getMetaDataRequest() {
        if (metaDataRequest == null) {
            return "";
        }
        return metaDataRequest;
    }

    /**
     * @param metaData the metaDataRequest to set
     * @return WorkParams
     */
    public WorkParams setMetaDataRequest(String metaData) {
        metaDataRequest = metaData;
        return this;
    }

    /**
     * @return the serverConfiguration
     */
    public ServerConfiguration getServerConfiguration() {
        if (serverConfiguration == null) {
            return new ServerConfiguration();
        }
        return serverConfiguration;
    }

    /**
     * @param serverConfiguration the serverConfiguration to set TODO REVIEW missing returns
     */
    public WorkParams setServerConfiguration(ServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
        return this;
    }

    /**
     * @return the guuid
     */
    public String getGuuid() {
        if (workerGUID == null) {
            return "";
        }
        return workerGUID;
    }

    /**
     * @param guuid the guuid to set TODO REVIEW missing returns
     */
    public WorkParams setGuuid(String guuid) {
        workerGUID = guuid;
        return this;
    }

}