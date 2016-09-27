/**
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
 */

package fr.gouv.vitam.common.format.identification.siegfried;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;

import static org.mockito.Mockito.mock;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.format.identification.exception.FormatIdentifierNotFoundException;
import fr.gouv.vitam.common.format.identification.exception.FormatIdentifierTechnicalException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;

public class SiegfriedClientRestTest extends JerseyTest {

    private static final String HOSTNAME = "localhost";
    private static int port;
    private static JunitHelper junitHelper;
    private final SiegfriedClientRest client;
    
    private static final String SAMPLE_VERSION_RESPONSE = "version-response.json";
    private static final String SAMPLE_OK_RESPONSE = "ok-response.json";
    
    private static final JsonNode JSON_NODE_VERSION = getJsonNode(SAMPLE_VERSION_RESPONSE);
    private static final JsonNode JSON_NODE_RESPONSE_OK = getJsonNode(SAMPLE_OK_RESPONSE);
    
    private static JsonNode getJsonNode(String file) {
        try {
            return JsonHandler.getFromFile(PropertiesUtils.findFile(file));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    protected ExpectedResults mock;

    interface ExpectedResults {
        Response get();
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        junitHelper = new JunitHelper();
        port = junitHelper.findAvailablePort();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        junitHelper.releasePort(port);
    }

    public SiegfriedClientRestTest() {
        client = new SiegfriedClientRest(HOSTNAME, port);
    }

    @Override
    protected Application configure() {
        enable(TestProperties.DUMP_ENTITY);
        forceSet(TestProperties.CONTAINER_PORT, Integer.toString(port));
        mock = mock(ExpectedResults.class);
        final ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.register(JacksonFeature.class);
        return resourceConfig.registerInstances(new MockResource(mock));
    }

    @Path("/identify")
    public static class MockResource {
        private final ExpectedResults expectedResponse;

        public MockResource(ExpectedResults expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        @GET
        @Path("/{encoded64}")
        @Produces(MediaType.APPLICATION_JSON)
        public Response getStatus() {
            return expectedResponse.get();
        }
    }

    @Test
    public void statusExecutionWithResponse() throws Exception {
        when(mock.get())
        .thenReturn(Response.status(Response.Status.OK).entity(JSON_NODE_VERSION).build());
        final JsonNode response = client.status(Paths.get("Path"));
        assertEquals("1.6.4", response.get("siegfried").asText());
    }
    
    @Test(expected = FormatIdentifierNotFoundException.class)
    public void statusExecutionNotFound() throws Exception {
        when(mock.get()).thenReturn(Response.status(Response.Status.NOT_FOUND).build());
        client.status(Paths.get("Path"));
    }

    @Test(expected = FormatIdentifierTechnicalException.class)
    public void statusExecutionInternalError() throws Exception {
        when(mock.get()).thenReturn(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        client.status(Paths.get("Path"));
    }
    
    @Test
    public void analysePathExecutionWithResponse() throws Exception {
        when(mock.get())
        .thenReturn(Response.status(Response.Status.OK).entity(JSON_NODE_RESPONSE_OK).build());
        final JsonNode response = client.analysePath(Paths.get("Path"));
        assertNotNull(response.get("files"));
    }
    
    @Test(expected = FormatIdentifierNotFoundException.class)
    public void analysePathExecutionNotFound() throws Exception {
        when(mock.get()).thenReturn(Response.status(Response.Status.NOT_FOUND).build());
        client.analysePath(Paths.get("Path"));
    }

    @Test(expected = FormatIdentifierTechnicalException.class)
    public void analysePathExecutionInternalError() throws Exception {
        when(mock.get()).thenReturn(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        client.analysePath(Paths.get("Path"));
    }
}
