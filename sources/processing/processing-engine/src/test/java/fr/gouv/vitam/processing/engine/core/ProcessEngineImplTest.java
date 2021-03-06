/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
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
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.processing.engine.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyObject;

import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import fr.gouv.vitam.common.exception.WorkflowNotFoundException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.ProcessAction;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.ProcessStep;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.common.parameter.WorkerParametersFactory;
import fr.gouv.vitam.processing.common.utils.ProcessPopulator;
import fr.gouv.vitam.processing.data.core.ProcessDataAccess;
import fr.gouv.vitam.processing.data.core.ProcessDataAccessImpl;
import fr.gouv.vitam.processing.distributor.api.ProcessDistributor;
import fr.gouv.vitam.processing.engine.core.monitoring.ProcessMonitoringImpl;

/**
 * Do not forget init method on test method !
 */
public class ProcessEngineImplTest {
    private ProcessEngineImpl processEngine;
    private WorkerParameters workParams;
    private ItemStatus response;
    private ProcessMonitoringImpl processMonitoring;
    private ProcessDistributor processDistributor;
    private static final Integer TENANT_ID = 0;
    private static final String WORKFLOW_ID = "workflowJSONv1";
    private static final String WORKFLOW_WITH_FINALLY_STEP = "workflowJSONFinallyStep";

    private ProcessDataAccess processData;

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    @Before
    public void init() throws WorkflowNotFoundException, ProcessingException {
        workParams = WorkerParametersFactory.newWorkerParameters();
        workParams.setWorkerGUID(GUIDFactory.newGUID()).setUrlMetadata("http://localhost:8083")
            .setUrlWorkspace("http://localhost:8083")
            .setContainerName(GUIDFactory.newGUID().getId());

        processDistributor = Mockito.mock(ProcessDistributor.class);
        processEngine = new ProcessEngineImplFactory().create(processDistributor);
        processMonitoring = ProcessMonitoringImpl.getInstance();

        processData = ProcessDataAccessImpl.getInstance();
    }

    @Test
    @RunWithCustomExecutor
    public void processEngineTest() throws Exception {

        processData.initProcessWorkflow(ProcessPopulator.populate(WORKFLOW_ID), workParams.getContainerName(),
            ProcessAction.INIT, LogbookTypeProcess.INGEST, TENANT_ID);

        processData.prepareToRelaunch(workParams.getContainerName(), ProcessAction.RESUME, TENANT_ID);

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        
        Mockito.when(processDistributor.distribute(anyObject(), anyObject(), anyObject()))
            .thenReturn(new ItemStatus().increment(StatusCode.OK));

        response = processEngine.startWorkflow(workParams);

        assertNotNull(response);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        final Map<String, ProcessStep> list =
            processMonitoring.getProcessSteps(workParams.getContainerName(), TENANT_ID);
        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    @RunWithCustomExecutor
    public void processEngineTestWithFinallyStep() throws Exception {

        processData.initProcessWorkflow(ProcessPopulator.populate(WORKFLOW_WITH_FINALLY_STEP),
            workParams.getContainerName(),
            ProcessAction.INIT, LogbookTypeProcess.INGEST, TENANT_ID);

        processData.prepareToRelaunch(workParams.getContainerName(), ProcessAction.RESUME, TENANT_ID);

        VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);

        final ItemStatus responses = new ItemStatus("stepName");
        responses.increment(StatusCode.OK);

        Mockito.when(processDistributor.distribute(Matchers.anyObject(), Matchers.anyObject(), Matchers.anyObject()))
            .thenReturn(responses);

        response = processEngine.startWorkflow(workParams);
        assertNotNull(response);
        assertEquals(response.getGlobalStatus(), StatusCode.OK);

        final Map<String, ProcessStep> map =
            processMonitoring.getProcessSteps(workParams.getContainerName(), TENANT_ID);
        assertNotNull(map);
        assertEquals(2, map.size());
    }
}
