/*******************************************************************************
 * This file is part of Vitam Project.
 * <p>
 * Copyright Vitam (2012, 2015)
 * <p>
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL license as circulated
 * by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 * <p>
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 * <p>
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 * <p>
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.ingest.upload.core;

import fr.gouv.vitam.ingest.model.UploadResponseDTO;

/**
 * 
 * Constructs a representation entity data for response from upload sip
 *
 */
public class UploadSipHelper {

	/**
	 * properties from configuration file
	 */
    public static final String PROPERTIES_CORE = "ingest-core.properties";


    /**
     * @param fileName
     * @param httpCode
     * @param message
     * @param vitamCode
     * @param vitamStatus
     * @param engineCode
     * @param engineStatus
     * @return
     */
    public static final UploadResponseDTO getUploadResponseDTO(String fileName, Integer httpCode, String message, String vitamCode,
                                                               String vitamStatus, String engineCode, String engineStatus) {
        final UploadResponseDTO uploadResponseDTO = new UploadResponseDTO();
        uploadResponseDTO.setFileName(fileName);
        uploadResponseDTO.setHttpCode(httpCode);
        uploadResponseDTO.setMessage(message);
        uploadResponseDTO.setVitamCode(vitamCode);
        uploadResponseDTO.setVitamStatus(vitamStatus);
        uploadResponseDTO.setEngineCode(engineCode);
        uploadResponseDTO.setEngineStatus(engineStatus);
        return uploadResponseDTO;
    }
}