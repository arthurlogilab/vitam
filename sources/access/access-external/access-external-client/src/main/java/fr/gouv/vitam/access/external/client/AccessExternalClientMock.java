package fr.gouv.vitam.access.external.client;

import java.io.ByteArrayInputStream;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.access.external.common.exception.AccessExternalClientNotFoundException;
import fr.gouv.vitam.access.external.common.exception.AccessExternalClientServerException;
import fr.gouv.vitam.common.client.AbstractMockClient;
import fr.gouv.vitam.common.client.ClientMockResultHelper;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;

/**
 * Mock client implementation for Access External
 */
class AccessExternalClientMock extends AbstractMockClient implements AccessExternalClient {

    @Override
    public RequestResponse selectUnits(JsonNode selectQuery, Integer tenantId) throws InvalidParseOperationException {
        return RequestResponseOK.getFromJsonNode(JsonHandler.getFromString(
            "{$hint: {'total':'1'},$context:{$query: {$eq: {\"Title\" : \"Archive1\" }}, $projection: {}, $filter: {}}, $result:[{'#id': '1', 'Title': 'Archive 1', 'DescriptionLevel': 'Archive Mock'}]}"));
    }

    @Override
    public RequestResponse selectUnitbyId(JsonNode selectQuery, String unitId, Integer tenantId) throws InvalidParseOperationException {
        return RequestResponseOK.getFromJsonNode(JsonHandler.getFromString(
            "{$hint: {'total':'1'},$context:{$query: {$eq: {\"id\" : \"1\" }}, $projection: {}, $filter: {}},$result:[{'#id': '1', 'Title': 'Archive 1', 'DescriptionLevel': 'Archive Mock'}]}"));
    }

    @Override
    public RequestResponse updateUnitbyId(JsonNode updateQuery, String unitId, Integer tenantId) throws InvalidParseOperationException {
        return RequestResponseOK.getFromJsonNode(JsonHandler.getFromString(
            "{$hint: {'total':'1'},$context:{$query: {$eq: {\"id\" : \"ArchiveUnit1\" }}, $projection: {}, $filter: {}},$result:[{'#id': '1', 'Title': 'Archive 1', 'DescriptionLevel': 'Archive Mock'}]}"));
    }

    @Override
    public Response getObject(JsonNode selectQuery, String objectId, String usage, int version, Integer tenantId)
        throws InvalidParseOperationException {
        return new AbstractMockClient.FakeInboundResponse(Status.OK, new ByteArrayInputStream("test".getBytes()),
            MediaType.APPLICATION_OCTET_STREAM_TYPE, null);
    }

    @Override
    public RequestResponse selectObjectById(JsonNode selectQuery, String objectId, Integer tenantId)
        throws InvalidParseOperationException {
        return ClientMockResultHelper.getObjectGroupResult();
    }


    @Override
    public RequestResponse selectUnitObjectGroup(JsonNode selectObjectQuery, String unitId, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientServerException,
        AccessExternalClientNotFoundException {
        return ClientMockResultHelper.getObjectGroupResult();
    }

    @Override public Response getUnitObject(JsonNode selectObjectQuery, String unitId, String usage, int version,
        Integer tenantId) throws InvalidParseOperationException, AccessExternalClientServerException,
        AccessExternalClientNotFoundException {
        return new AbstractMockClient.FakeInboundResponse(Status.OK, new ByteArrayInputStream("test".getBytes()),
            MediaType.APPLICATION_OCTET_STREAM_TYPE, null);
    }

    @Override
    public RequestResponse selectOperation(JsonNode select, Integer tenantId)
        throws LogbookClientException, InvalidParseOperationException {
        return ClientMockResultHelper.getLogbooksRequestResponse();
    }

    @Override
    public RequestResponse selectOperationbyId(String processId, Integer tenantId)
        throws InvalidParseOperationException {
        return ClientMockResultHelper.getLogbookRequestResponse();
    }

    @Override
    public RequestResponse selectUnitLifeCycleById(String idUnit, Integer tenantId)
        throws LogbookClientException, InvalidParseOperationException {
        return ClientMockResultHelper.getLogbookRequestResponse();
    }

    @Override
    public RequestResponse selectUnitLifeCycle(JsonNode query, Integer tenantId)
        throws LogbookClientException, InvalidParseOperationException {
        return ClientMockResultHelper.getLogbookRequestResponse();
    }

    @Override
    public RequestResponse selectObjectGroupLifeCycleById(String idObject, Integer tenantId)
        throws LogbookClientException, InvalidParseOperationException {
        return ClientMockResultHelper.getLogbookRequestResponse();
    }

    @Override
    public RequestResponse getAccessionRegisterSummary(JsonNode query, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientServerException,
        AccessExternalClientNotFoundException {
        return ClientMockResultHelper.getAccessionRegisterSummary();
    }

    @Override
    public RequestResponse getAccessionRegisterDetail(String id, JsonNode query, Integer tenantId)
        throws InvalidParseOperationException, AccessExternalClientServerException,
        AccessExternalClientNotFoundException {
        return ClientMockResultHelper.getAccessionRegisterDetail();
    }
}
