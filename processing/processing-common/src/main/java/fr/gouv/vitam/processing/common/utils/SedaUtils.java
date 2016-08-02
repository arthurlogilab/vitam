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
package fr.gouv.vitam.processing.common.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.CharStreams;

import de.odysseus.staxon.json.JsonXMLConfig;
import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import fr.gouv.vitam.api.exception.MetaDataAlreadyExistException;
import fr.gouv.vitam.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.api.exception.MetaDataException;
import fr.gouv.vitam.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.builder.request.construct.Insert;
import fr.gouv.vitam.client.MetaDataClient;
import fr.gouv.vitam.client.MetaDataClientFactory;
import fr.gouv.vitam.common.FileUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.exception.CycleFoundException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.graph.DirectedCycle;
import fr.gouv.vitam.common.graph.DirectedGraph;
import fr.gouv.vitam.common.graph.Graph;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.logbook.common.exception.LogbookClientAlreadyExistsException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleObjectGroupParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookLifeCycleUnitParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookOutcome;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCycleClient;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.model.StatusCode;
import fr.gouv.vitam.processing.common.model.WorkParams;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

/**
 * SedaUtils to read or split element from SEDA
 *
 */
public class SedaUtils {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(SedaUtils.class);
    private static final LogbookLifeCycleClient LOGBOOK_LIFECYCLE_CLIENT = LogbookLifeCyclesClientFactory.getInstance()
        .getLogbookLifeCyclesClient();

    private static String File_CONF = "version.conf";
    private static final String NAMESPACE_URI = "fr:gouv:culture:archivesdefrance:seda:v2.0";
    private static final String SEDA_FILE = "manifest.xml";
    private static final String SEDA_VALIDATION_FILE = "seda-2.0-main.xsd";
    private static final String XML_EXTENSION = ".xml";
    public static final String JSON_EXTENSION = ".json";
    private static final String SEDA_FOLDER = "SIP";
    private static final String BINARY_DATA_OBJECT = "BinaryDataObject";
    private static final String MESSAGE_IDENTIFIER = "MessageIdentifier";
    private static final String OBJECT_GROUP = "ObjectGroup";
    private static final String DATA_OBJECT_GROUPID = "DataObjectGroupId";
    private static final String ARCHIVE_UNIT = "ArchiveUnit";
    private static final String ARCHIVE_UNIT_FOLDER = "Units";
    private static final String BINARY_MASTER = "BinaryMaster";
    private static final String FILE_INFO = "FileInfo";
    private static final String METADATA = "Metadata";
    private static final String DATA_OBJECT_GROUP_REFERENCEID = "DataObjectGroupReferenceId";
    private static final String TAG_URI = "Uri";
    private static final String TAG_SIZE = "Size";
    private static final String TAG_DIGEST = "MessageDigest";
    private static final String TAG_VERSION = "DataObjectVersion";
    private static final String MSG_PARSING_BDO = "Parsing Binary Data Object";
    private static final String STAX_PROPERTY_PREFIX_OUTPUT_SIDE = "javax.xml.stream.isRepairingNamespaces";
    private static final String TAG_CONTENT = "Content";
    private static final String TAG_MANAGEMENT = "Management";
    private static final String TAG_OG = "_og";
    public static final String LIFE_CYCLE_EVENT_TYPE_PROCESS = "INGEST";
    public static final String UNIT_LIFE_CYCLE_CREATION_EVENT_TYPE = "CREATE_LF_UNIT";
    private static final String OG_LIFE_CYCLE_CREATION_EVENT_TYPE = "CREATE_LF_OG";
    private static final String OG_LIFE_CYCLE_CHECK_BDO_EVENT_TYPE = "CHECK_BDO";
    private static final String LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG = "LogbookClient Unsupported request";
    private static final String LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG = "LifeCycle Object already exists";
    private static final String LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG = "Logbook LifeCycle resource not found";
    private static final String LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG = "Logbook Server internal error";
    private static final String LOGBOOK_LF_MAPS_PARSING_EXCEPTION_MSG = "Parse Object Groups/BDO Maps error";
    public static final String OBJECT_GROUP_ID_TO_GUID_MAP_FILE_NAME_PREFIX = "OBJECT_GROUP_ID_TO_GUID_MAP_";
    public static final String BDO_TO_OBJECT_GROUP_ID_MAP_FILE_NAME_PREFIX = "BDO_TO_OBJECT_GROUP_ID_MAP_";
    public static final String ARCHIVE_ID_TO_GUID_MAP_FILE_NAME_PREFIX = "ARCHIVE_ID_TO_GUID_MAP_";
    public static final String TXT_EXTENSION = ".txt";
    private static final String LEVEL = "level_";
    private static final String EXEC = "Exec";

    private static final String ARCHIVE_UNIT_ELEMENT_ID_ATTRIBUTE = "id";
    private static final String ARCHIVE_UNIT_REF_ID_TAG = "ArchiveUnitRefId";
    public static final String UP_FIELD = "_up";
    public static final String ARCHIVE_TREE_TMP_FILE_NAME_PREFIX = "INGEST_TREE_";
    private static final String INVALID_INGEST_TREE_EXCEPTION_MSG =
        "INGEST_TREE invalid, can not save to temporary file";
    private static final String TMP_FOLDER = "vitam" + File.separator + "temp";
    private static final String INGEST_LEVEL_STACK = "ingestLevelStack.json";
    private static final String CYCLE_FOUND_EXCEPTION = "Seda has an archive unit cycle ";
    private static final String SAVE_ARCHIVE_ID_TO_GUID_IOEXCEPTION_MSG =
        "Can not save unitToGuidMap to temporary file";

    private final Map<String, String> binaryDataObjectIdToGuid;
    private final Map<String, String> objectGroupIdToGuid;
    // TODO : utiliser une structure avec le GUID et le témoin de passage du DataObjectGroupID .
    // objectGroup referenced before declaration
    private final Map<String, String> objectGroupIdToGuidTmp;
    private final Map<String, String> unitIdToGuid;

    private final Map<String, String> binaryDataObjectIdToObjectGroupId;
    private final Map<String, List<String>> objectGroupIdToBinaryDataObjectId;
    private final Map<String, String> unitIdToGroupId;
    private final Map<String, List<String>> objectGroupIdToUnitId;

    private final MetaDataClientFactory metaDataClientFactory;

    private final Map<String, LogbookParameters> guidToLifeCycleParameters;

    // Messages for duplicate Uri from SEDA
    private static final String MSG_DUPLICATE_URI_MANIFEST = "Présence d'un URI en doublon dans le bordereau: ";


    protected SedaUtils(MetaDataClientFactory metaDataFactory) {
        ParametersChecker.checkParameter("metaDataFactory is a mandatory parameter", metaDataFactory);
        binaryDataObjectIdToGuid = new HashMap<String, String>();
        objectGroupIdToGuid = new HashMap<String, String>();
        objectGroupIdToGuidTmp = new HashMap<String, String>();
        objectGroupIdToBinaryDataObjectId = new HashMap<String, List<String>>();
        unitIdToGuid = new HashMap<String, String>();
        binaryDataObjectIdToObjectGroupId = new HashMap<String, String>();
        objectGroupIdToUnitId = new HashMap<String, List<String>>();
        unitIdToGroupId = new HashMap<String, String>();
        metaDataClientFactory = metaDataFactory;
        guidToLifeCycleParameters = new HashMap<String, LogbookParameters>();
    }

    protected SedaUtils() {
        this(new MetaDataClientFactory());
    }

    /**
     * @return A map reflects BinaryDataObject and File(GUID)
     */
    public Map<String, String> getBinaryDataObjectIdToGuid() {
        return binaryDataObjectIdToGuid;
    }

    /**
     * @return A map reflects relation ObjectGroupId and BinaryDataObjectId
     */
    public Map<String, List<String>> getObjectGroupIdToBinaryDataObjectId() {
        return objectGroupIdToBinaryDataObjectId;
    }

    /**
     * @return A map reflects ObjectGroup and File(GUID)
     */
    public Map<String, String> getObjectGroupIdToGuid() {
        return objectGroupIdToGuid;
    }

    /**
     * @return A map reflects Unit and File(GUID)
     */
    public Map<String, String> getUnitIdToGuid() {
        return unitIdToGuid;
    }

    /**
     * @return A map reflects BinaryDataObject and ObjectGroup
     */
    public Map<String, String> getBinaryDataObjectIdToGroupId() {
        return binaryDataObjectIdToObjectGroupId;
    }

    /**
     * @return A map reflects Unit and ObjectGroup
     */
    public Map<String, String> getUnitIdToGroupId() {
        return unitIdToGroupId;
    }

    /**
     * Split Element from InputStream and write it to workspace
     *
     * @param params parameters of workspace server
     * @throws ProcessingException throw when can't read or extract element from SEDA
     */
    public void extractSEDA(WorkParams params) throws ProcessingException {
        ParametersChecker.checkParameter("WorkParams is a mandatory parameter", params);
        final String containerId = params.getContainerName();
        final WorkspaceClient client = WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());
        extractSEDAWithWorkspaceClient(client, containerId);
    }

    /**
     * get Message Identifier from seda
     * 
     * @param params parameters of workspace server
     * @return message id
     * @throws ProcessingException throw when can't read or extract message id from SEDA
     */
    public String getMessageIdentifier(WorkParams params) throws ProcessingException {
        ParametersChecker.checkParameter("WorkParams is a mandatory parameter", params);
        final String containerId = params.getContainerName();
        String messageId = "";
        final WorkspaceClient client = WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());
        InputStream xmlFile = null;
        try {
            xmlFile = client.getObject(containerId, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Manifest.xml Not Found");
            throw new ProcessingException(e);
        }

        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLEventReader reader = null;
        final QName messageObjectName = new QName(NAMESPACE_URI, MESSAGE_IDENTIFIER);

        try {
            reader = xmlInputFactory.createXMLEventReader(xmlFile);
            while (true) {
                final XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    final StartElement element = event.asStartElement();
                    if (element.getName().equals(messageObjectName)) {
                        messageId = reader.getElementText();
                        break;
                    }
                }
                if (event.isEndDocument()) {
                    break;
                }
            }
            reader.close();
        } catch (final XMLStreamException e) {
            LOGGER.error("Can not read SEDA", e);
            throw new ProcessingException(e);
        }

        return messageId;
    }

    private void extractSEDAWithWorkspaceClient(WorkspaceClient client, String containerId) throws ProcessingException {
        ParametersChecker.checkParameter("WorkspaceClient is a mandatory parameter", client);
        ParametersChecker.checkParameter("ContainerId is a mandatory parameter", containerId);

        /**
         * Retrieves SEDA
         **/
        InputStream xmlFile = null;
        try {
            xmlFile = client.getObject(containerId, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Manifest.xml Not Found");
            throw new ProcessingException(e);
        }

        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLEventReader reader = null;
        final QName dataObjectName = new QName(NAMESPACE_URI, BINARY_DATA_OBJECT);
        final QName unitName = new QName(NAMESPACE_URI, ARCHIVE_UNIT);

        // Archive Unit Tree
        ObjectNode archiveUnitTree = JsonHandler.createObjectNode();

        try {
            reader = xmlInputFactory.createXMLEventReader(xmlFile);
            while (true) {
                final XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    final StartElement element = event.asStartElement();
                    if (element.getName().equals(unitName)) {
                        writeArchiveUnitToWorkspace(client, containerId, reader, element, archiveUnitTree);

                        // Update created Unit life cycles
                        for (String unitGuid : unitIdToGuid.values()) {
                            if (guidToLifeCycleParameters.get(unitGuid) != null) {
                                guidToLifeCycleParameters.get(unitGuid).setStatus(LogbookOutcome.OK);
                                LOGBOOK_LIFECYCLE_CLIENT.update(guidToLifeCycleParameters.get(unitGuid));
                            }
                        }
                    } else if (element.getName().equals(dataObjectName)) {
                        String objectGroupGuid = writeBinaryDataObjectInLocal(reader, element, containerId);

                        if (guidToLifeCycleParameters.get(objectGroupGuid) != null) {
                            guidToLifeCycleParameters.get(objectGroupGuid).setStatus(LogbookOutcome.OK);
                            LOGBOOK_LIFECYCLE_CLIENT.update(guidToLifeCycleParameters.get(objectGroupGuid));
                        }
                    }
                }
                if (event.isEndDocument()) {
                    break;
                }
            }
            reader.close();

            // Save Archive Unit Tree
            // Create temporary file to store archive unit tree
            final File archiveTreeTmpFile = PropertiesUtils
                .fileFromTmpFolder(ARCHIVE_TREE_TMP_FILE_NAME_PREFIX + containerId + JSON_EXTENSION);
            JsonHandler.writeAsFile(archiveUnitTree, archiveTreeTmpFile);
            // check cycle and create level stack; will be used when indexing unit
            // 1-detect cycle : if graph has a cycle throw CycleFoundException
            new DirectedCycle(new DirectedGraph(archiveUnitTree));

            // Save unitToGuidMap
            saveArchiveUnitIdToGuidMap(containerId);


            // 2- create graph and create level
            createIngestLevelStackFile(client, containerId, new Graph(archiveUnitTree).getGraphWithLongestPaths());

            checkArchiveUnitIdReference();
            saveObjectGroupsToWorkspace(client, containerId);
        } catch (final XMLStreamException e) {
            LOGGER.error("Can not read SEDA");
            throw new ProcessingException(e);
        } catch (LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientNotFoundException e) {
            LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (InvalidParseOperationException e) {
            LOGGER.error(INVALID_INGEST_TREE_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (CycleFoundException e) {
            LOGGER.error(CYCLE_FOUND_EXCEPTION, e);
            throw new ProcessingException(e);
        } catch (IOException e) {
            LOGGER.error(SAVE_ARCHIVE_ID_TO_GUID_IOEXCEPTION_MSG, e);
            throw new ProcessingException(e);
        }
    }

    private Map<String, File> extractArchiveUnitToLocalFile(XMLEventReader reader, StartElement startElement,
        String archiveUnitId, ObjectNode archiveUnitTree)
        throws ProcessingException {

        Map<String, File> archiveUnitToTmpFileMap = new HashMap<String, File>();
        final String elementGuid = GUIDFactory.newGUID().toString();

        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        final String elementID = ((Attribute) startElement.getAttributes().next()).getValue();
        final QName name = startElement.getName();
        int stack = 1;
        String groupGuid = "";
        final File tmpFile = PropertiesUtils.fileFromTmpFolder(GUIDFactory.newGUID().toString() + elementGuid);
        XMLEventWriter writer;

        final QName unitName = new QName(NAMESPACE_URI, ARCHIVE_UNIT);
        final QName archiveUnitRefIdTag = new QName(NAMESPACE_URI, ARCHIVE_UNIT_REF_ID_TAG);

        // Add new node in archiveUnitNode
        ObjectNode archiveUnitNode = (ObjectNode) archiveUnitTree.get(archiveUnitId);
        if (archiveUnitNode == null) {
            // Create node
            archiveUnitNode = JsonHandler.createObjectNode();
        }

        // Add new Archive Unit Entry
        archiveUnitTree.set(archiveUnitId, archiveUnitNode);

        try {
            tmpFile.createNewFile();
            writer = xmlOutputFactory.createXMLEventWriter(new FileWriter(tmpFile));
            unitIdToGuid.put(elementID, elementGuid);

            // Create new startElement for object with new guid
            writer.add(eventFactory.createStartElement("", NAMESPACE_URI, startElement.getName().getLocalPart()));
            writer.add(eventFactory.createAttribute("id", elementGuid));
            // TODO allow recursive
            while (true) {
                final XMLEvent event = reader.nextEvent();
                if (event.isStartElement() && event.asStartElement().getName().equals(name)) {
                    String currentArchiveUnit = event.asStartElement()
                        .getAttributeByName(new QName(ARCHIVE_UNIT_ELEMENT_ID_ATTRIBUTE)).getValue();
                    if (archiveUnitId.equalsIgnoreCase(currentArchiveUnit)) {
                        stack++;
                    }
                }

                if (event.isEndElement()) {
                    final EndElement end = event.asEndElement();
                    if (end.getName().equals(name)) {
                        stack--;
                        if (stack == 0) {
                            // Create objectgroup reference id
                            writer.add(eventFactory.createStartElement("", "", TAG_OG));
                            writer.add(eventFactory.createCharacters(groupGuid));
                            writer.add(eventFactory.createEndElement("", "", TAG_OG));

                            writer.add(event);
                            break;
                        }
                    }
                }
                if (event.isStartElement() &&
                    event.asStartElement().getName().getLocalPart() == DATA_OBJECT_GROUP_REFERENCEID) {
                    groupGuid = GUIDFactory.newGUID().toString();
                    final String groupId = reader.getElementText();
                    unitIdToGroupId.put(elementID, groupId);
                    if (objectGroupIdToUnitId.get(groupId) == null) {
                        final ArrayList<String> archiveUnitList = new ArrayList<String>();
                        archiveUnitList.add(elementID);
                        objectGroupIdToUnitId.put(groupId, archiveUnitList);
                    } else {
                        final List<String> archiveUnitList = objectGroupIdToUnitId.get(groupId);
                        archiveUnitList.add(elementID);
                        objectGroupIdToUnitId.put(groupId, archiveUnitList);
                    }
                    // Create new startElement for group with new guid
                    writer.add(eventFactory.createStartElement("", NAMESPACE_URI, DATA_OBJECT_GROUP_REFERENCEID));
                    writer.add(eventFactory.createCharacters(groupGuid));
                    writer.add(eventFactory.createEndElement("", NAMESPACE_URI, DATA_OBJECT_GROUP_REFERENCEID));

                } else if (event.isStartElement() && event.asStartElement().getName().equals(unitName)) {

                    // Update archiveUnitTree
                    String nestedArchiveUnitId = event.asStartElement()
                        .getAttributeByName(new QName(ARCHIVE_UNIT_ELEMENT_ID_ATTRIBUTE)).getValue();

                    ObjectNode nestedArchiveUnitNode = (ObjectNode) archiveUnitTree.get(nestedArchiveUnitId);
                    if (nestedArchiveUnitNode == null) {
                        // Create new Archive Unit Node
                        nestedArchiveUnitNode = JsonHandler.createObjectNode();
                    }

                    // Add immediate parents
                    ArrayNode parentsField = nestedArchiveUnitNode.withArray(UP_FIELD);
                    parentsField.add(archiveUnitId);

                    // Update global tree
                    archiveUnitTree.set(nestedArchiveUnitId, nestedArchiveUnitNode);

                    // Process Archive Unit element: recursive call
                    archiveUnitToTmpFileMap.putAll(extractArchiveUnitToLocalFile(reader, event.asStartElement(),
                        nestedArchiveUnitId, archiveUnitTree));
                } else if (event.isStartElement() && event.asStartElement().getName().equals(archiveUnitRefIdTag)) {
                    // Referenced Child Archive Unit
                    String childArchiveUnitRef = reader.getElementText();

                    ObjectNode childArchiveUnitNode = (ObjectNode) archiveUnitTree.get(childArchiveUnitRef);
                    if (childArchiveUnitNode == null) {
                        // Create new Archive Unit Node
                        childArchiveUnitNode = JsonHandler.createObjectNode();
                    }

                    ArrayNode parentsField = childArchiveUnitNode.withArray(UP_FIELD);
                    parentsField.add(archiveUnitId);
                    archiveUnitTree.set(childArchiveUnitRef, childArchiveUnitNode);
                } else {
                    writer.add(event);
                }

            }
            reader.close();
            writer.close();
        } catch (final XMLStreamException e) {
            LOGGER.error("Can not extract Object from SEDA XMLStreamException");
            throw new ProcessingException(e);
        } catch (final IOException e) {
            LOGGER.error("Can not extract Object from SEDA IOException " + elementGuid);
            throw new ProcessingException(e);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage());
            throw new ProcessingException(e);
        }

        archiveUnitToTmpFileMap.put(elementGuid, tmpFile);
        return archiveUnitToTmpFileMap;
    }

    private LogbookParameters initLogbookLifeCycleParameters(String guid, boolean isArchive, boolean isObjectGroup) {
        LogbookParameters logbookLifeCycleParameters = guidToLifeCycleParameters.get(guid);
        if (logbookLifeCycleParameters == null) {
            logbookLifeCycleParameters = isArchive ? LogbookParametersFactory.newLogbookLifeCycleUnitParameters()
                : (isObjectGroup ? LogbookParametersFactory.newLogbookLifeCycleObjectGroupParameters()
                    : LogbookParametersFactory.newLogbookOperationParameters());

            logbookLifeCycleParameters.putParameterValue(LogbookParameterName.objectIdentifier, guid);
        }
        return logbookLifeCycleParameters;
    }

    private void createObjectGroupLifeCycle(String groupGuid, String containerId)
        throws LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException {
        LogbookLifeCycleObjectGroupParameters logbookLifecycleObjectGroupParameters =
            (LogbookLifeCycleObjectGroupParameters) initLogbookLifeCycleParameters(
                groupGuid, false, true);

        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            containerId);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventIdentifier,
            GUIDFactory.newGUID().toString());
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventTypeProcess,
            LIFE_CYCLE_EVENT_TYPE_PROCESS);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventType,
            OG_LIFE_CYCLE_CREATION_EVENT_TYPE);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.outcome,
            LogbookOutcome.STARTED.toString());
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.outcomeDetail,
            LogbookOutcome.STARTED.toString());
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            LogbookOutcome.STARTED.toString());
        LOGBOOK_LIFECYCLE_CLIENT.create(logbookLifecycleObjectGroupParameters);

        // Update guidToLifeCycleParameters
        guidToLifeCycleParameters.put(groupGuid, logbookLifecycleObjectGroupParameters);
    }

    private void createUnitLifeCycle(String unitGuid, String containerId)
        throws LogbookClientBadRequestException, LogbookClientAlreadyExistsException, LogbookClientServerException {
        LogbookLifeCycleUnitParameters logbookLifecycleUnitParameters =
            (LogbookLifeCycleUnitParameters) initLogbookLifeCycleParameters(
                unitGuid, true, false);

        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess, containerId);
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventIdentifier,
            GUIDFactory.newGUID().toString());
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventTypeProcess,
            LIFE_CYCLE_EVENT_TYPE_PROCESS);
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.eventType,
            UNIT_LIFE_CYCLE_CREATION_EVENT_TYPE);
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.outcome,
            LogbookOutcome.STARTED.toString());
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.outcomeDetail,
            LogbookOutcome.STARTED.toString());
        logbookLifecycleUnitParameters.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            LogbookOutcome.STARTED.toString());
        LOGBOOK_LIFECYCLE_CLIENT.create(logbookLifecycleUnitParameters);

        // Update guidToLifeCycleParameters
        guidToLifeCycleParameters.put(unitGuid, logbookLifecycleUnitParameters);
    }

    /**
     * @param unitGuid
     * @param containerId
     * @param stepName
     * @return
     * @throws ProcessingException
     */
    public void updateLifeCycleByStep(LogbookParameters logbookLifecycleParameters, WorkParams params)
        throws ProcessingException {

        try {
            String extension = FilenameUtils.getExtension(params.getObjectName());
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.objectIdentifier,
                params.getObjectName().replace("." + extension, ""));
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess,
                params.getContainerName());
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.eventIdentifier,
                GUIDFactory.newGUID().toString());
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.eventTypeProcess,
                LIFE_CYCLE_EVENT_TYPE_PROCESS);
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.eventType, params.getCurrentStep());
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.outcome,
                LogbookOutcome.STARTED.toString());
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.outcomeDetail,
                LogbookOutcome.STARTED.toString());
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.outcomeDetailMessage,
                LogbookOutcome.STARTED.toString());

            LOGBOOK_LIFECYCLE_CLIENT.update(logbookLifecycleParameters);
        } catch (LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientNotFoundException e) {
            LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        }
    }

    /**
     * @param logbookLifecycleUnitParameters
     * @param stepStatus
     * @throws ProcessingException
     */
    public void setLifeCycleFinalEventStatusByStep(LogbookParameters logbookLifecycleParameters, StatusCode stepStatus)
        throws ProcessingException {

        try {
            logbookLifecycleParameters.putParameterValue(LogbookParameterName.outcome, stepStatus.toString());
            LOGBOOK_LIFECYCLE_CLIENT.update(logbookLifecycleParameters);
        } catch (LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientNotFoundException e) {
            LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        }
    }

    private void writeArchiveUnitToWorkspace(WorkspaceClient client, String containerId, XMLEventReader reader,
        StartElement startElement, ObjectNode archiveUnitTree) throws ProcessingException {

        try {
            // Get ArchiveUnit Id
            String archiveUnitId = startElement.getAttributeByName(new QName(ARCHIVE_UNIT_ELEMENT_ID_ATTRIBUTE))
                .getValue();
            final Map<String, File> archiveUnitGuidToFileMap = extractArchiveUnitToLocalFile(reader, startElement,
                archiveUnitId, archiveUnitTree);

            if (archiveUnitGuidToFileMap != null && !archiveUnitGuidToFileMap.isEmpty()) {
                for (Entry<String, File> unitEntry : archiveUnitGuidToFileMap.entrySet()) {
                    File tmpFile = unitEntry.getValue();
                    client.putObject(containerId, ARCHIVE_UNIT_FOLDER + "/" + unitEntry.getKey() + XML_EXTENSION,
                        new FileInputStream(tmpFile));

                    // Create Archive Unit LifeCycle
                    createUnitLifeCycle(unitEntry.getKey(), containerId);

                    if (!tmpFile.delete()) {
                        LOGGER.warn("File could not be deleted");
                    }
                }
            }
        } catch (final ProcessingException e) {
            LOGGER.error("Can not extract Object from SEDA XMLStreamException", e);
            throw e;
        } catch (final IOException e) {
            LOGGER.error("Can not extract Object from SEDA IOException ", e);
            throw new ProcessingException(e);
        } catch (final ContentAddressableStorageServerException e) {
            LOGGER.error("Can not write to workspace ", e);
            throw new ProcessingException(e);
        } catch (LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientAlreadyExistsException e) {
            LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        }
    }

    private void checkArchiveUnitIdReference() throws ProcessingException {
        for (final Entry<String, String> entry : unitIdToGroupId.entrySet()) {
            if (objectGroupIdToGuid.get(entry.getValue()) == null) {
                final String groupId = binaryDataObjectIdToObjectGroupId.get(entry.getValue());
                if (groupId == null || groupId != "") {
                    throw new ProcessingException("Archive Unit reference Id is not correct");
                }
            }
        }
    }

    private String writeBinaryDataObjectInLocal(XMLEventReader reader, StartElement startElement, String containerId)
        throws ProcessingException {
        final String elementGuid = GUIDFactory.newGUID().toString();
        final File tmpFile = PropertiesUtils.fileFromTmpFolder(elementGuid + ".json");
        final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        final JsonXMLConfig config = new JsonXMLConfigBuilder().build();
        String groupGuid = null;
        try {
            final FileWriter tmpFileWriter = new FileWriter(tmpFile);

            final XMLEventWriter writer = new JsonXMLOutputFactory(config).createXMLEventWriter(tmpFileWriter);

            final Iterator<?> it = startElement.getAttributes();
            String binaryOjectId = "";
            if (it.hasNext()) {
                binaryOjectId = ((Attribute) it.next()).getValue();
                binaryDataObjectIdToGuid.put(binaryOjectId, elementGuid);
                binaryDataObjectIdToObjectGroupId.put(binaryOjectId, "");
                writer.add(eventFactory.createStartDocument());
                writer.add(eventFactory.createStartElement("", "", startElement.getName().getLocalPart()));
                writer.add(eventFactory.createStartElement("", "", "_id"));
                writer.add(eventFactory.createCharacters(binaryOjectId));
                writer.add(eventFactory.createEndElement("", "", "_id"));
            }
            while (true) {
                boolean writable = true;
                final XMLEvent event = reader.nextEvent();
                if (event.isEndElement()) {
                    final EndElement end = event.asEndElement();
                    if (end.getName().getLocalPart() == BINARY_DATA_OBJECT) {
                        writer.add(event);
                        writer.add(eventFactory.createEndDocument());
                        break;
                    }
                }

                if (event.isStartElement()) {
                    final String localPart = event.asStartElement().getName().getLocalPart();
                    if (localPart == DATA_OBJECT_GROUPID) {
                        groupGuid = GUIDFactory.newGUID().toString();
                        final String groupId = reader.getElementText();
                        // Having DataObjectGroupID after a DataObjectGroupReferenceID in the XML flow .
                        // We get the GUID defined earlier during the DataObjectGroupReferenceID analysis
                        if (objectGroupIdToGuidTmp.get(groupId) != null) {
                            groupGuid = objectGroupIdToGuidTmp.get(groupId);
                            objectGroupIdToGuidTmp.remove(groupId);
                        }
                        binaryDataObjectIdToObjectGroupId.put(binaryOjectId, groupId);
                        objectGroupIdToGuid.put(groupId, groupGuid);

                        // Create OG lifeCycle
                        createObjectGroupLifeCycle(groupGuid, containerId);
                        if (objectGroupIdToBinaryDataObjectId.get(groupId) == null) {
                            final List<String> binaryOjectList = new ArrayList<String>();
                            binaryOjectList.add(binaryOjectId);
                            objectGroupIdToBinaryDataObjectId.put(groupId, binaryOjectList);
                        } else {
                            objectGroupIdToBinaryDataObjectId.get(groupId).add(binaryOjectId);
                        }

                        // Create new startElement for group with new guid
                        writer.add(eventFactory.createStartElement("", "", DATA_OBJECT_GROUPID));
                        writer.add(eventFactory.createCharacters(groupGuid));
                        writer.add(eventFactory.createEndElement("", "", DATA_OBJECT_GROUPID));
                    } else if (localPart == DATA_OBJECT_GROUP_REFERENCEID) {
                        final String groupId = reader.getElementText();
                        String groupGuidTmp = GUIDFactory.newGUID().toString();
                        binaryDataObjectIdToObjectGroupId.put(binaryOjectId, groupId);
                        // The DataObjectGroupReferenceID is after DataObjectGroupID in the XML flow
                        if (objectGroupIdToBinaryDataObjectId.get(groupId) != null) {
                            objectGroupIdToBinaryDataObjectId.get(groupId).add(binaryOjectId);
                            groupGuidTmp = objectGroupIdToGuid.get(groupId);
                        } else {
                            // The DataObjectGroupReferenceID is before DataObjectGroupID in the XML flow
                            final List<String> binaryOjectList = new ArrayList<String>();
                            binaryOjectList.add(binaryOjectId);
                            objectGroupIdToBinaryDataObjectId.put(groupId, binaryOjectList);
                            objectGroupIdToGuidTmp.put(groupId, groupGuidTmp);

                        }

                        // Create new startElement for group with new guid
                        writer.add(eventFactory.createStartElement("", "", DATA_OBJECT_GROUPID));
                        writer.add(eventFactory.createCharacters(groupGuidTmp));
                        writer.add(eventFactory.createEndElement("", "", DATA_OBJECT_GROUPID));
                    } else if (localPart == "Uri") {
                        reader.getElementText();
                    } else {
                        writer.add(eventFactory.createStartElement("", "", localPart));
                    }

                    writable = false;
                }

                if (writable) {
                    writer.add(event);
                }
            }
            reader.close();
            writer.close();
            tmpFileWriter.close();

        } catch (final XMLStreamException e) {
            LOGGER.debug("Can not read input stream");
            throw new ProcessingException(e);
        } catch (final IOException e) {
            LOGGER.debug("Closing stream error");
            throw new ProcessingException(e);
        } catch (LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientAlreadyExistsException e) {
            LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        } catch (LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
            throw new ProcessingException(e);
        }

        return groupGuid;

    }

    private void completeBinaryObjectToObjectGroupMap() {
        for (final String key : binaryDataObjectIdToObjectGroupId.keySet()) {
            if (binaryDataObjectIdToObjectGroupId.get(key) == "") {
                final List<String> binaryOjectList = new ArrayList<String>();
                binaryOjectList.add(key);
                objectGroupIdToBinaryDataObjectId.put(GUIDFactory.newGUID().toString(), binaryOjectList);
                // TODO Create OG / OG lifeCycle
            }
        }
    }

    private void saveObjectGroupBdoMaps(String containerId) throws IOException {
        // Save binaryDataObjectIdToObjectGroupId and objectGroupIdToGuid
        final File firstMapTmpFile = PropertiesUtils
            .fileFromTmpFolder(BDO_TO_OBJECT_GROUP_ID_MAP_FILE_NAME_PREFIX + containerId + TXT_EXTENSION);
        final FileWriter firstMapTmpFileWriter = new FileWriter(firstMapTmpFile);
        firstMapTmpFileWriter.write(binaryDataObjectIdToObjectGroupId.toString());
        firstMapTmpFileWriter.flush();
        firstMapTmpFileWriter.close();

        final File secondMapTmpFile = PropertiesUtils
            .fileFromTmpFolder(OBJECT_GROUP_ID_TO_GUID_MAP_FILE_NAME_PREFIX + containerId + TXT_EXTENSION);
        final FileWriter secondMapTmpFileWriter = new FileWriter(secondMapTmpFile);
        secondMapTmpFileWriter.write(objectGroupIdToGuid.toString());
        secondMapTmpFileWriter.flush();
        secondMapTmpFileWriter.close();
    }

    private void saveArchiveUnitIdToGuidMap(String containerId) throws IOException {
        // Save unitIdToGuid
        final File firstMapTmpFile = PropertiesUtils
            .fileFromTmpFolder(ARCHIVE_ID_TO_GUID_MAP_FILE_NAME_PREFIX + containerId + TXT_EXTENSION);
        final FileWriter firstMapTmpFileWriter = new FileWriter(firstMapTmpFile);
        firstMapTmpFileWriter.write(unitIdToGuid.toString());
        firstMapTmpFileWriter.flush();
        firstMapTmpFileWriter.close();
    }

    private void saveObjectGroupsToWorkspace(WorkspaceClient client, String containerId) throws ProcessingException {

        completeBinaryObjectToObjectGroupMap();

        // Save maps
        try {
            saveObjectGroupBdoMaps(containerId);
        } catch (IOException e1) {
            LOGGER.error("Can not write to tmp folder ", e1);
            throw new ProcessingException(e1);
        }

        for (final Entry<String, List<String>> entry : objectGroupIdToBinaryDataObjectId.entrySet()) {
            final ObjectNode objectGroup = JsonHandler.createObjectNode();
            ObjectNode fileInfo = JsonHandler.createObjectNode();
            final ArrayNode unitParent = JsonHandler.createArrayNode();
            String objectGroupType = "";
            final String objectGroupGuid = objectGroupIdToGuid.get(entry.getKey());
            final File tmpFile = PropertiesUtils.fileFromTmpFolder(objectGroupGuid + JSON_EXTENSION);

            try {
                final FileWriter tmpFileWriter = new FileWriter(tmpFile);
                final Map<String, ArrayList<JsonNode>> categoryMap = new HashMap<String, ArrayList<JsonNode>>();
                objectGroup.put("_id", objectGroupGuid);
                objectGroup.put("_tenantId", 0);
                for (final String id : entry.getValue()) {
                    final File binaryObjectFile = PropertiesUtils
                        .fileFromTmpFolder(binaryDataObjectIdToGuid.get(id) + JSON_EXTENSION);
                    final JsonNode binaryNode = JsonHandler.getFromFile(binaryObjectFile).get("BinaryDataObject");
                    String nodeCategory = "BinaryMaster";
                    if (binaryNode.get("DataObjectVersion") != null) {
                        nodeCategory = binaryNode.get("DataObjectVersion").asText();
                    }
                    ArrayList<JsonNode> nodeCategoryArray = categoryMap.get(nodeCategory);
                    if (nodeCategoryArray == null) {
                        nodeCategoryArray = new ArrayList<JsonNode>();
                        nodeCategoryArray.add(binaryNode);
                    } else {
                        nodeCategoryArray.add(binaryNode);
                    }
                    categoryMap.put(nodeCategory, nodeCategoryArray);
                    if (BINARY_MASTER.equals(nodeCategory)) {

                        fileInfo = (ObjectNode) binaryNode.get(FILE_INFO);
                        if (binaryNode.get(METADATA) != null) {
                            objectGroupType = binaryNode.get(METADATA).fieldNames().next();
                        }
                    }
                    if (!binaryObjectFile.delete()) {
                        LOGGER.warn("File could not be deleted");
                    }
                }

                for (final String objectGroupId : objectGroupIdToUnitId.get(entry.getKey())) {
                    unitParent.add(unitIdToGuid.get(objectGroupId));
                }

                objectGroup.put("_type", objectGroupType);
                objectGroup.set("FileInfo", fileInfo);
                final ObjectNode qualifiersNode = getObjectGroupQualifiers(categoryMap);
                objectGroup.set("_qualifiers", qualifiersNode);
                objectGroup.set("_up", unitParent);
                objectGroup.put("_nb", entry.getValue().size());
                tmpFileWriter.write(objectGroup.toString());
                tmpFileWriter.close();

                client.putObject(containerId, OBJECT_GROUP + "/" + objectGroupGuid + JSON_EXTENSION,
                    new FileInputStream(tmpFile));
                if (!tmpFile.delete()) {
                    LOGGER.warn("File could not be deleted");
                }

                // Create unreferenced object group
                if (guidToLifeCycleParameters.get(objectGroupGuid) == null) {
                    createObjectGroupLifeCycle(objectGroupGuid, containerId);
                }

                // Update Object Group lifeCycle creation event
                guidToLifeCycleParameters.get(objectGroupGuid).setStatus(LogbookOutcome.OK);
                LOGBOOK_LIFECYCLE_CLIENT.update(guidToLifeCycleParameters.get(objectGroupGuid));

            } catch (final InvalidParseOperationException e) {
                LOGGER.error("Can not parse ObjectGroup", e);
                throw new ProcessingException(e);
            } catch (final IOException e) {
                LOGGER.error("Can not write to tmp folder ", e);
                throw new ProcessingException(e);
            } catch (final ContentAddressableStorageServerException e) {
                LOGGER.error("Workspace exception ", e);
                throw new ProcessingException(e);
            } catch (LogbookClientBadRequestException e) {
                LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (LogbookClientAlreadyExistsException e) {
                LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (LogbookClientServerException e) {
                LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            } catch (LogbookClientNotFoundException e) {
                LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG, e);
                throw new ProcessingException(e);
            }
        }
    }

    private ObjectNode getObjectGroupQualifiers(Map<String, ArrayList<JsonNode>> categoryMap) {
        final ObjectNode qualifierObject = JsonHandler.createObjectNode();
        for (final Entry<String, ArrayList<JsonNode>> entry : categoryMap.entrySet()) {
            final ObjectNode binaryNode = JsonHandler.createObjectNode();
            binaryNode.put("nb", entry.getValue().size());
            final ArrayNode arrayNode = JsonHandler.createArrayNode();
            for (final JsonNode node : entry.getValue()) {
                arrayNode.add(node);
            }
            binaryNode.set("versions", arrayNode);
            qualifierObject.set(entry.getKey(), binaryNode);
        }
        return qualifierObject;
    }

    /**
     * The method is used to validate SEDA by XSD
     *
     * @param params worker parameter
     * @return boolean true/false
     */
    public boolean checkSedaValidation(WorkParams params) {
        ParametersChecker.checkParameter("WorkParams is a mandatory parameter", params);
        final String containerId = params.getContainerName();
        final WorkspaceClient client = WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());
        try {
            final InputStream input = checkExistenceManifest(client, containerId);
            return new ValidationXsdUtils().checkWithXSD(input, SEDA_VALIDATION_FILE);

        } catch (ProcessingException | XMLStreamException | SAXException e) {
            LOGGER.error("Manifest.xml is not valid ", e);
            return false;
        } catch (IOException e) {
            LOGGER.error("Seda validation file not found", e);
            return false;
        }
    }

    private InputStream checkExistenceManifest(WorkspaceClient client, String guid)
        throws IOException, ProcessingException {
        ParametersChecker.checkParameter("WorkspaceClient is a mandatory parameter", client);
        ParametersChecker.checkParameter("guid is a mandatory parameter", guid);
        InputStream manifest = null;
        try {
            manifest = client.getObject(guid, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Manifest not found");
            throw new ProcessingException("Manifest not found", e);
        }
        return manifest;
    }

    /**
     * @param params work parameters
     * @throws ProcessingException when error in execution
     */
    public void indexArchiveUnit(WorkParams params) throws ProcessingException {
        ParametersChecker.checkParameter("Work parameters is a mandatory parameter", params);

        final String containerId = params.getContainerName();
        final String objectName = params.getObjectName();
        ParametersChecker.checkParameter("Container id is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("ObjectName id is a mandatory parameter", objectName);

        final WorkspaceClient workspaceClient = WorkspaceClientFactory
            .create(params.getServerConfiguration().getUrlWorkspace());
        final MetaDataClient metadataClient = metaDataClientFactory
            .create(params.getServerConfiguration().getUrlMetada());
        InputStream input;
        try {
            input = workspaceClient.getObject(containerId, ARCHIVE_UNIT_FOLDER + "/" + objectName);

            if (input != null) {
                final JsonNode json = convertArchiveUnitToJson(input, containerId, objectName).get(ARCHIVE_UNIT);

                // Add _up to archive unit json object
                String extension = FilenameUtils.getExtension(objectName);
                addParents(json, objectName.replace("." + extension, ""), containerId);

                Insert insertQuery = new Insert();
                final String insertRequest = insertQuery.addData((ObjectNode) json).getFinalInsert().toString();

                metadataClient.insertUnit(insertRequest);
            } else {
                LOGGER.error("Archive unit not found");
                throw new ProcessingException("Archive unit not found");
            }

        } catch (final InvalidParseOperationException e) {
            LOGGER.debug("Archive unit json invalid");
            throw new ProcessingException(e);
        } catch (final MetaDataNotFoundException e) {
            LOGGER.debug("Archive unit not found");
            throw new ProcessingException(e);
        } catch (final MetaDataAlreadyExistException e) {
            LOGGER.debug("Archive unit already exists");
            throw new ProcessingException(e);
        } catch (final MetaDataDocumentSizeException e) {
            LOGGER.debug("Archive unit Document size error");
            throw new ProcessingException(e);
        } catch (final MetaDataException e) {
            LOGGER.debug("Internal Server Error");
            throw new ProcessingException(e);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.debug("Workspace Server Error");
            throw new ProcessingException(e);
        } catch (IOException e) {
            LOGGER.debug("Error occured when opening required temporary files");
            throw new ProcessingException(e);
        }

    }

    private void addParents(JsonNode archiveUnitJsonObject, String archiveUnitGuid, String containerId)
        throws IOException, InvalidParseOperationException {

        final File unitIdToGuidMapFile = PropertiesUtils
            .fileFromTmpFolder(ARCHIVE_ID_TO_GUID_MAP_FILE_NAME_PREFIX + containerId + TXT_EXTENSION);

        final File archiveunitTreeTmpFile = PropertiesUtils
            .fileFromTmpFolder(ARCHIVE_TREE_TMP_FILE_NAME_PREFIX + containerId + JSON_EXTENSION);

        JsonNode archiveUnitTree = JsonHandler.getFromFile(archiveunitTreeTmpFile);
        String unitIdToGuidStoredContent = FileUtil.readFile(unitIdToGuidMapFile);
        Map<String, Object> unitIdToGuidStoredMap = getMapFromString(unitIdToGuidStoredContent);

        Map<Object, String> guidToUnitIdMap =
            unitIdToGuidStoredMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        if (guidToUnitIdMap.containsKey(archiveUnitGuid) && archiveUnitTree.has(guidToUnitIdMap.get(archiveUnitGuid)) &&
            archiveUnitTree.get(guidToUnitIdMap.get(archiveUnitGuid)).has(UP_FIELD) &&
            archiveUnitTree.get(guidToUnitIdMap.get(archiveUnitGuid)).get(UP_FIELD).isArray()) {

            ArrayNode parents = (ArrayNode) archiveUnitTree.get(guidToUnitIdMap.get(archiveUnitGuid)).get(UP_FIELD);
            ArrayNode upNode = (ArrayNode) archiveUnitJsonObject.withArray(UP_FIELD);
            for (JsonNode currentParentNode : parents) {
                String currentParentId = currentParentNode.asText();
                if (unitIdToGuidStoredMap.containsKey(currentParentId)) {
                    upNode.add(unitIdToGuidStoredMap.get(currentParentId).toString());
                }
            }
        }
    }

    /**
     * The function is used for retrieving ObjectGroup in workspace and use metadata client to index ObjectGroup
     *
     * @param params work parameters
     * @throws ProcessingException when error in execution
     */
    public void indexObjectGroup(WorkParams params) throws ProcessingException {
        ParametersChecker.checkParameter("Work parameters is a mandatory parameter", params);

        final String containerId = params.getContainerName();
        final String objectName = params.getObjectName();
        ParametersChecker.checkParameter("Container id is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("ObjectName id is a mandatory parameter", objectName);

        final WorkspaceClient workspaceClient = WorkspaceClientFactory
            .create(params.getServerConfiguration().getUrlWorkspace());
        final MetaDataClient metadataClient = metaDataClientFactory
            .create(params.getServerConfiguration().getUrlMetada());
        InputStream input = null;
        try {
            input = workspaceClient.getObject(containerId, OBJECT_GROUP + "/" + objectName);

            if (input != null) {
                final String inputStreamString = CharStreams.toString(new InputStreamReader(input, "UTF-8"));
                final JsonNode json = JsonHandler.getFromString(inputStreamString);
                final Insert insertRequest = new Insert().addData((ObjectNode) json);
                metadataClient.insertObjectGroup(insertRequest.getFinalInsert().toString());
            } else {
                LOGGER.error("Object group not found");
                throw new ProcessingException("Object group not found");
            }

        } catch (final MetaDataException e) {
            LOGGER.debug("Metadata Server Error", e);
            throw new ProcessingException(e);
        } catch (InvalidParseOperationException | IOException e) {
            LOGGER.debug("Json wrong format", e);
            throw new ProcessingException(e);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.debug("Workspace Server Error", e);
            throw new ProcessingException(e);
        }

    }

    private JsonNode convertArchiveUnitToJson(InputStream input, String containerId, String objectName)
        throws InvalidParseOperationException, ProcessingException {
        ParametersChecker.checkParameter("Input stream is a mandatory parameter", input);
        ParametersChecker.checkParameter("Container id is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("ObjectName id is a mandatory parameter", objectName);
        final File tmpFile = PropertiesUtils.fileFromTmpFolder(GUIDFactory.newGUID().toString());
        FileWriter tmpFileWriter = null;
        final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
        final JsonXMLConfig config = new JsonXMLConfigBuilder().build();
        JsonNode data = null;
        try {
            tmpFileWriter = new FileWriter(tmpFile);
            final XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(input);

            final XMLEventWriter writer = new JsonXMLOutputFactory(config).createXMLEventWriter(tmpFileWriter);
            boolean contentWritable = true;
            while (true) {
                final XMLEvent event = reader.nextEvent();
                boolean eventWritable = true;
                if (event.isStartElement()) {
                    final StartElement startElement = event.asStartElement();
                    final Iterator<?> it = startElement.getAttributes();
                    final String tag = startElement.getName().getLocalPart();
                    if (it.hasNext() && tag != TAG_CONTENT) {
                        writer.add(eventFactory.createStartElement("", "", tag));

                        if (tag == ARCHIVE_UNIT) {
                            writer.add(eventFactory.createStartElement("", "", "#id"));
                            writer.add(eventFactory.createCharacters(((Attribute) it.next()).getValue()));
                            writer.add(eventFactory.createEndElement("", "", "#id"));
                        }
                        eventWritable = false;
                    }

                    if (tag == TAG_CONTENT) {
                        eventWritable = false;
                    }

                    if (tag == TAG_OG) {
                        contentWritable = true;
                    }

                    if (tag == TAG_MANAGEMENT) {
                        writer.add(eventFactory.createStartElement("", "", "_mgt"));
                        eventWritable = false;
                    }
                }

                if (event.isEndElement()) {

                    if (event.asEndElement().getName().getLocalPart() == ARCHIVE_UNIT) {
                        eventWritable = false;
                    }

                    if (event.asEndElement().getName().getLocalPart() == "Content") {
                        eventWritable = false;
                        contentWritable = false;
                    }

                    if (event.asEndElement().getName().getLocalPart() == "Management") {
                        writer.add(eventFactory.createEndElement("", "", "_mgt"));
                        eventWritable = false;
                    }
                }

                if (event.isEndDocument()) {
                    writer.add(event);
                    break;
                }
                if (eventWritable && contentWritable) {
                    writer.add(event);
                }
            }
            reader.close();
            writer.close();
            input.close();
            tmpFileWriter.close();
            data = JsonHandler.getFromFile(tmpFile);
            if (!tmpFile.delete()) {
                LOGGER.warn("File could not be deleted");
            }
        } catch (final XMLStreamException e) {
            LOGGER.debug("Can not read input stream");
            throw new ProcessingException(e);
        } catch (final IOException e) {
            LOGGER.debug("Closing stream error");
            throw new ProcessingException(e);
        }
        return data;
    }

    /**
     *
     * @param params - parameters of workspace server
     * @return ExtractUriResponse - Object ExtractUriResponse contains listURI, listMessages and value boolean(error).
     * @throws ProcessingException - throw when error in execution.
     */
    public ExtractUriResponse getAllDigitalObjectUriFromManifest(WorkParams params)
        throws ProcessingException {
        final String guid = params.getContainerName();
        final WorkspaceClient client = WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());
        final ExtractUriResponse extractUriResponse = parsingUriSEDAWithWorkspaceClient(client, guid);
        return extractUriResponse;
    }

    /**
     * Parsing file Manifest
     *
     * @param client - the InputStream to read from
     * @param guid - Identification file seda.
     * @return ExtractUriResponse - Object ExtractUriResponse contains listURI, listMessages and value boolean(error).
     * @throws XMLStreamException-This Exception class is used to report well format SEDA.
     */
    private ExtractUriResponse parsingUriSEDAWithWorkspaceClient(WorkspaceClient client, String guid)
        throws ProcessingException {

        /**
         * Extract SEDA
         **/
        InputStream xmlFile = null;

        try {
            xmlFile = client.getObject(guid, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e1) {
            LOGGER.error("Workspace error: Can not get file");
            throw new ProcessingException(e1.getMessage());
        }
        LOGGER.info(SedaUtils.MSG_PARSING_BDO);

        final ExtractUriResponse extractUriResponse = new ExtractUriResponse();

        // create URI list String for add elements uri from inputstream Seda
        final List<URI> listUri = new ArrayList<URI>();
        // create String Messages list
        final List<String> listMessages = new ArrayList<>();

        extractUriResponse.setUriListManifest(listUri);
        extractUriResponse.setErrorNumber(listMessages.size());

        // Create the XML input factory
        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        // Create the XML output factory
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

        xmlOutputFactory.setProperty(SedaUtils.STAX_PROPERTY_PREFIX_OUTPUT_SIDE, Boolean.TRUE);

        final QName binaryDataObject = new QName(SedaUtils.NAMESPACE_URI, SedaUtils.BINARY_DATA_OBJECT);

        try {

            // Create event reader
            final XMLEventReader evenReader = xmlInputFactory.createXMLEventReader(xmlFile);

            while (true) {
                final XMLEvent event = evenReader.nextEvent();
                // reach the start of an BinaryDataObject
                if (event.isStartElement()) {
                    final StartElement element = event.asStartElement();

                    if (element.getName().equals(binaryDataObject)) {
                        getUri(extractUriResponse, evenReader);
                    }
                }
                if (event.isEndDocument()) {
                    LOGGER.info("data : " + event);
                    break;
                }
            }
            LOGGER.info("End of extracting  Uri from manifest");
            evenReader.close();

        } catch (XMLStreamException | URISyntaxException e) {
            LOGGER.error(e.getMessage());
            throw new ProcessingException(e);
        } finally {
            extractUriResponse.setErrorDuplicateUri(!extractUriResponse.getOutcomeMessages().isEmpty());
        }
        return extractUriResponse;
    }

    private void getUri(ExtractUriResponse extractUriResponse, XMLEventReader evenReader)
        throws XMLStreamException, URISyntaxException {

        while (evenReader.hasNext()) {
            XMLEvent event = evenReader.nextEvent();

            if (event.isStartElement()) {
                final StartElement startElement = event.asStartElement();

                // If we have an Tag Uri element equal Uri into SEDA
                if (startElement.getName().getLocalPart() == SedaUtils.TAG_URI) {
                    event = evenReader.nextEvent();
                    final String uri = event.asCharacters().getData();
                    // Check element is duplicate
                    checkDuplicatedUri(extractUriResponse, uri);
                    extractUriResponse.getUriListManifest().add(new URI(uri));
                    break;
                }
            }
        }
    }

    private void checkDuplicatedUri(ExtractUriResponse extractUriResponse, String uriString) throws URISyntaxException {

        if (extractUriResponse.getUriListManifest().contains(new URI(uriString))) {
            extractUriResponse.setErrorNumber(extractUriResponse.getErrorNumber() + 1);
        }
    }

    /**
     * check if the version list of the manifest.xml in workspace is valid
     *
     * @param params worker parameter
     * @return list of unsupported version
     * @throws ProcessingException throws when error occurs
     */
    public List<String> checkSupportedBinaryObjectVersion(WorkParams params)
        throws ProcessingException {
        ParametersChecker.checkParameter("WorkParams is a mandatory parameter", params);
        final String containerId = params.getContainerName();
        final WorkspaceClient client = WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());
        return isSedaVersionValid(client, containerId);
    }

    private List<String> isSedaVersionValid(WorkspaceClient client,
        String containerId) throws ProcessingException {
        ParametersChecker.checkParameter("WorkspaceClient is a mandatory parameter", client);
        ParametersChecker.checkParameter("ContainerId is a mandatory parameter", containerId);

        InputStream xmlFile = null;
        List<String> invalidVersionList = new ArrayList<String>();
        try {
            xmlFile = client.getObject(containerId, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Manifest.xml Not Found");
            throw new ProcessingException(e);
        }

        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLEventReader reader = null;

        try {
            reader = xmlInputFactory.createXMLEventReader(xmlFile);
            invalidVersionList = compareVersionList(reader, File_CONF);
            reader.close();
        } catch (final XMLStreamException e) {
            LOGGER.error("Can not read SEDA");
            throw new ProcessingException(e);
        }

        return invalidVersionList;
    }


    private SedaUtilInfo getBinaryObjectInfo(XMLEventReader evenReader)
        throws ProcessingException {
        final SedaUtilInfo sedaUtilInfo = new SedaUtilInfo();
        BinaryObjectInfo binaryObjectInfo = new BinaryObjectInfo();
        while (evenReader.hasNext()) {
            XMLEvent event;
            try {
                event = evenReader.nextEvent();


                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();

                    if (startElement.getName().getLocalPart() == BINARY_DATA_OBJECT) {
                        event = evenReader.nextEvent();
                        final String id = ((Attribute) startElement.getAttributes().next()).getValue();
                        binaryObjectInfo.setId(id);

                        while (evenReader.hasNext()) {
                            event = evenReader.nextEvent();
                            if (event.isStartElement()) {
                                startElement = event.asStartElement();

                                final String tag = startElement.getName().getLocalPart();
                                if (tag == TAG_URI) {
                                    final String uri = evenReader.getElementText();
                                    binaryObjectInfo.setUri(new URI(uri));
                                }

                                if (tag == TAG_VERSION) {
                                    final String version = evenReader.getElementText();
                                    binaryObjectInfo.setVersion(version);
                                }

                                if (tag == TAG_DIGEST) {
                                    binaryObjectInfo
                                        .setAlgo(((Attribute) startElement.getAttributes().next()).getValue());
                                    final String messageDigest = evenReader.getElementText();
                                    binaryObjectInfo.setMessageDigest(messageDigest);
                                }

                                if (tag == TAG_SIZE) {
                                    final int size = Integer.parseInt(evenReader.getElementText());
                                    binaryObjectInfo.setSize(size);
                                }
                            }

                            if (event.isEndElement() &&
                                event.asEndElement().getName().getLocalPart() == BINARY_DATA_OBJECT) {
                                sedaUtilInfo.setBinaryObjectMap(binaryObjectInfo);
                                binaryObjectInfo = new BinaryObjectInfo();
                                break;
                            }

                        }
                    }
                }
            } catch (XMLStreamException | URISyntaxException e) {
                LOGGER.error("Can not get BinaryObject info");
                throw new ProcessingException(e);
            }
        }
        return sedaUtilInfo;
    }

    /**
     * @param evenReader XMLEventReader for the file manifest.xml
     * @return List of version for file manifest.xml
     * @throws ProcessingException when error in execution
     */

    public List<String> manifestVersionList(XMLEventReader evenReader)
        throws ProcessingException {
        final List<String> versionList = new ArrayList<String>();
        final SedaUtilInfo sedaUtilInfo = getBinaryObjectInfo(evenReader);
        final Map<String, BinaryObjectInfo> binaryObjectMap = sedaUtilInfo.getBinaryObjectMap();

        for (final String mapKey : binaryObjectMap.keySet()) {
            if (!versionList.contains(binaryObjectMap.get(mapKey).getVersion())) {
                versionList.add(binaryObjectMap.get(mapKey).getVersion());
            }
        }

        return versionList;
    }

    /**
     * compare if the version list of manifest.xml is included in or equal to the version list of version.conf
     *
     * @param eventReader xml event reader
     * @param fileConf version file
     * @return list of unsupported version
     * @throws ProcessingException when error in execution
     */
    public List<String> compareVersionList(XMLEventReader eventReader, String fileConf)
        throws ProcessingException {

        File file;

        try {
            file = PropertiesUtils.findFile(fileConf);
        } catch (FileNotFoundException e) {
            LOGGER.error("Can not get config file ");
            throw new ProcessingException(e);
        }

        List<String> fileVersionList = new ArrayList<>();

        try {
            fileVersionList = SedaVersion.fileVersionList(file);
        } catch (IOException e) {
            LOGGER.error("Can not read config file");
            throw new ProcessingException(e);
        }

        final List<String> manifestVersionList = manifestVersionList(eventReader);
        final List<String> invalidVersionList = new ArrayList<String>();

        for (final String s : manifestVersionList) {
            if (s != null) {
                if (!fileVersionList.contains(s)) {
                    LOGGER.info(s + ": invalid version");
                    invalidVersionList.add(s);
                } else {
                    LOGGER.info(s + ": valid version");
                }
            }
        }
        return invalidVersionList;
    }

    /**
     * check the conformity of the binary object
     *
     * @param params worker parameter
     * @return List of the invalid digest message
     * @throws ProcessingException when error in execution
     * @throws ContentAddressableStorageException
     * @throws URISyntaxException
     * @throws ContentAddressableStorageServerException
     * @throws ContentAddressableStorageNotFoundException
     */
    public List<String> checkConformityBinaryObject(WorkParams params)
        throws ProcessingException, ContentAddressableStorageNotFoundException,
        ContentAddressableStorageServerException, URISyntaxException, ContentAddressableStorageException {
        ParametersChecker.checkParameter("WorkParams is a mandatory parameter", params);
        final String containerId = params.getContainerName();
        final WorkspaceClient client = WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());

        InputStream xmlFile = null;
        List<String> digestMessageInvalidList = new ArrayList<String>();
        try {
            xmlFile = client.getObject(containerId, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Manifest.xml Not Found");
            throw new ProcessingException(e);
        }

        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLEventReader reader = null;

        try {
            reader = xmlInputFactory.createXMLEventReader(xmlFile);
            digestMessageInvalidList = compareDigestMessage(reader, client, containerId);
            reader.close();
        } catch (final XMLStreamException e) {
            LOGGER.error("Can not read SEDA");
            throw new ProcessingException(e);
        } catch (LogbookClientBadRequestException e) {
            LOGGER.error(LOGBOOK_LF_BAD_REQUEST_EXCEPTION_MSG);
            throw new ProcessingException(e);
        } catch (LogbookClientAlreadyExistsException e) {
            LOGGER.error(LOGBOOK_LF_OBJECT_EXISTS_EXCEPTION_MSG);
            throw new ProcessingException(e);
        } catch (LogbookClientServerException e) {
            LOGGER.error(LOGBOOK_SERVER_INTERNAL_EXCEPTION_MSG);
            throw new ProcessingException(e);
        } catch (LogbookClientNotFoundException e) {
            LOGGER.error(LOGBOOK_LF_RESOURCE_NOT_FOUND_EXCEPTION_MSG);
            throw new ProcessingException(e);
        } catch (InvalidParseOperationException e) {
            LOGGER.error(LOGBOOK_LF_MAPS_PARSING_EXCEPTION_MSG);
            throw new ProcessingException(e);
        } catch (IOException e) {
            LOGGER.error(LOGBOOK_LF_MAPS_PARSING_EXCEPTION_MSG);
            throw new ProcessingException(e);
        }
        return digestMessageInvalidList;
    }

    /**
     * compare the digest message between the manifest.xml and related uri content in workspace container
     *
     * 
     * @param evenReader
     * @param client
     * @return List<String> list of the invalid digest message
     * @throws XMLStreamException
     * @throws URISyntaxException
     * @throws ContentAddressableStorageNotFoundException
     * @throws ContentAddressableStorageServerException
     * @throws ContentAddressableStorageException
     * @throws InvalidParseOperationException
     * @throws LogbookClientServerException
     * @throws LogbookClientAlreadyExistsException
     * @throws LogbookClientBadRequestException
     * @throws LogbookClientNotFoundException
     * @throws IOException
     * @throws ProcessingException
     */

    public List<String> compareDigestMessage(XMLEventReader evenReader, WorkspaceClient client, String containerId)
        throws XMLStreamException, URISyntaxException, ContentAddressableStorageNotFoundException,
        ContentAddressableStorageServerException, ContentAddressableStorageException,
        InvalidParseOperationException, LogbookClientBadRequestException, LogbookClientAlreadyExistsException,
        LogbookClientServerException, LogbookClientNotFoundException, IOException, ProcessingException {

        final SedaUtilInfo sedaUtilInfo = getBinaryObjectInfo(evenReader);
        final Map<String, BinaryObjectInfo> binaryObjectMap = sedaUtilInfo.getBinaryObjectMap();
        final List<String> digestMessageInvalidList = new ArrayList<String>();

        final File firstMapTmpFile = PropertiesUtils
            .fileFromTmpFolder(BDO_TO_OBJECT_GROUP_ID_MAP_FILE_NAME_PREFIX + containerId + TXT_EXTENSION);
        final File secondMapTmpFile = PropertiesUtils
            .fileFromTmpFolder(OBJECT_GROUP_ID_TO_GUID_MAP_FILE_NAME_PREFIX + containerId + TXT_EXTENSION);
        String firstStoredMap = FileUtil.readFile(firstMapTmpFile);
        String secondStoredMap = FileUtil.readFile(secondMapTmpFile);

        Map<String, Object> binaryDataObjectIdToObjectGroupIdBackupMap = getMapFromString(firstStoredMap);
        Map<String, Object> objectGroupIdToGuidBackupMap = getMapFromString(secondStoredMap);

        for (final String mapKey : binaryObjectMap.keySet()) {

            // Update OG lifecycle
            String bdoXmlId = binaryObjectMap.get(mapKey).getId();
            String objectGroupId = (String) binaryDataObjectIdToObjectGroupIdBackupMap.get(bdoXmlId);
            LogbookLifeCycleObjectGroupParameters logbookLifeCycleObjGrpParam = null;
            if (objectGroupId != null) {
                String objectGroupGuid = (String) objectGroupIdToGuidBackupMap.get(objectGroupId);
                logbookLifeCycleObjGrpParam = updateObjectGroupLifeCycleOnBdoCheck(objectGroupGuid, bdoXmlId,
                    containerId);
            }

            final String uri = binaryObjectMap.get(mapKey).getUri().toString();
            final String digestMessageManifest = binaryObjectMap.get(mapKey).getMessageDigest();
            final DigestType algo = binaryObjectMap.get(mapKey).getAlgo();
            final String digestMessage = client.computeObjectDigest(containerId, SEDA_FOLDER + "/" + uri, algo);
            if (!digestMessage.equals(digestMessageManifest)) {
                LOGGER.info("Binary object Digest Message Invalid : " + uri);
                digestMessageInvalidList.add(digestMessageManifest);

                // Set KO status
                if (logbookLifeCycleObjGrpParam != null) {
                    logbookLifeCycleObjGrpParam.putParameterValue(LogbookParameterName.outcome,
                        StatusCode.KO.toString());
                    LOGBOOK_LIFECYCLE_CLIENT.update(logbookLifeCycleObjGrpParam);
                }
            } else {
                LOGGER.info("Binary Object Digest Message Valid : " + uri);

                // Set OK status
                if (logbookLifeCycleObjGrpParam != null) {
                    logbookLifeCycleObjGrpParam.putParameterValue(LogbookParameterName.outcome,
                        StatusCode.OK.toString());
                    LOGBOOK_LIFECYCLE_CLIENT.update(logbookLifeCycleObjGrpParam);
                }
            }
        }

        return digestMessageInvalidList;
    }

    private Map<String, Object> getMapFromString(String mapStr) {
        Map<String, Object> map = new HashMap<String, Object>();
        String value =
            !StringUtils.isBlank(mapStr) && mapStr.length() >= 2 ? mapStr.substring(1, mapStr.length() - 2) : "";
        String[] keyValuePairs = value.split(",");
        for (String pair : keyValuePairs) {
            if (!StringUtils.isBlank(pair) && pair.contains("=")) {
                String[] entry = pair.split("=");
                map.put(entry[0].trim(), entry[1].trim());
            }
        }
        return map;
    }

    private LogbookLifeCycleObjectGroupParameters updateObjectGroupLifeCycleOnBdoCheck(String objectGroupGuid,
        String bdoXmlId, String containerId) throws LogbookClientBadRequestException,
        LogbookClientAlreadyExistsException, LogbookClientServerException, LogbookClientNotFoundException {

        LogbookLifeCycleObjectGroupParameters logbookLifecycleObjectGroupParameters =
            (LogbookLifeCycleObjectGroupParameters) initLogbookLifeCycleParameters(
                objectGroupGuid, false, true);

        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventIdentifierProcess,
            containerId);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventIdentifier, bdoXmlId);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventTypeProcess,
            LIFE_CYCLE_EVENT_TYPE_PROCESS);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.eventType,
            OG_LIFE_CYCLE_CHECK_BDO_EVENT_TYPE);
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.outcome,
            LogbookOutcome.STARTED.toString());
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.outcomeDetail,
            LogbookOutcome.STARTED.toString());
        logbookLifecycleObjectGroupParameters.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            LogbookOutcome.STARTED.toString());
        LOGBOOK_LIFECYCLE_CLIENT.update(logbookLifecycleObjectGroupParameters);

        return logbookLifecycleObjectGroupParameters;
    }

    /**
     * create level stack on Json file
     * 
     * @param client workspace client
     * @param containerId
     * @param levelStackMap
     * @throws ProcessingException
     */
    private void createIngestLevelStackFile(WorkspaceClient client, String containerId,
        Map<Integer, Set<String>> levelStackMap) throws ProcessingException {
        LOGGER.info("Begin createIngestLevelStackFile/containerId:" + containerId);
        ParametersChecker.checkParameter("levelStackMap is a mandatory parameter", levelStackMap);
        ParametersChecker.checkParameter("unitIdToGuid is a mandatory parameter", unitIdToGuid);
        ParametersChecker.checkParameter("WorkspaceClient is a mandatory parameter", client);

        File tempFile = null;
        try {
            tempFile = File.createTempFile(TMP_FOLDER, INGEST_LEVEL_STACK);
            // tempFile will be deleted on exit
            tempFile.deleteOnExit();
            // create level json object node
            ObjectNode IngestLevelStack = JsonHandler.createObjectNode();
            for (Entry<Integer, Set<String>> entry : levelStackMap.entrySet()) {
                ArrayNode unitList = IngestLevelStack.withArray(LEVEL + entry.getKey());
                Set<String> unitGuidList = entry.getValue();
                for (String idXml : unitGuidList) {

                    String unitGuid = unitIdToGuid.get(idXml);
                    if (unitGuid == null) {
                        throw new IllegalArgumentException("Unit guid not found in map");
                    }
                    unitList.add(unitGuid);
                }
                IngestLevelStack.set(LEVEL + entry.getKey(), unitList);
            }
            LOGGER.debug("IngestLevelStack:" + IngestLevelStack.toString());
            // create json file
            JsonHandler.writeAsFile(IngestLevelStack, tempFile);
            // put file in workspace
            client.putObject(containerId, EXEC + "/" + INGEST_LEVEL_STACK, new FileInputStream(tempFile));
        } catch (IOException e) {
            LOGGER.error(e);
            throw new ProcessingException(e);
        } catch (InvalidParseOperationException e) {
            LOGGER.error(e);
            throw new ProcessingException(e);
        } catch (ContentAddressableStorageServerException e) {
            LOGGER.error(e);
            throw new ProcessingException(e);
        } finally {
            if (tempFile != null) {
                tempFile.exists();
            }
        }
        LOGGER.info("End createIngestLevelStackFile/containerId:" + containerId);

    }

    /**
     * Retrieve the binary data object infos linked to the object group. <br>
     * TODO : should not need to parse the manifest.xml to link a binary data object present in workspace to an object
     * group. To refactor when Object Group and Binary Data Object link is defined. TODO : during the next refactoring
     * of Sedautils, it has to be refactored to StoreObjectGroupActionHandler
     * 
     * @param params worker parameters
     * @return list binary data object informations
     * @throws ProcessingException throws when error occurs
     */
    public List<BinaryObjectInfo> retrieveStorageInformationForObjectGroup(WorkParams params)
        throws ProcessingException {
        ParametersChecker.checkParameter("Work parameters is a mandatory parameter", params);
        final String containerId = params.getContainerName();
        final String objectName = params.getObjectName();
        ParametersChecker.checkParameter("Container id is a mandatory parameter", containerId);
        ParametersChecker.checkParameter("ObjectName id is a mandatory parameter", objectName);

        final WorkspaceClient workspaceClient =
            WorkspaceClientFactory.create(params.getServerConfiguration().getUrlWorkspace());
        // retrieve SEDA FILE and get the list of objectsDatas
        List<BinaryObjectInfo> binaryObjectsToStore = new ArrayList<>();
        // Get binary objects informations of the SIP
        SedaUtilInfo sedaUtilInfo = getSedaUtilInfo(workspaceClient, containerId);
        // Get objectGroup objects ids
        final JsonNode jsonOG = getJsonFromWorkspace(workspaceClient, containerId, OBJECT_GROUP + "/" + objectName);

        // Filter on objectGroup objects ids to retrieve only binary objects informations linked to the ObjectGroup
        JsonNode qualifiers = jsonOG.get("_qualifiers");
        if (qualifiers == null) {
            return binaryObjectsToStore;
        }

        List<JsonNode> versions = qualifiers.findValues("versions");
        if (versions == null || versions.isEmpty()) {
            return binaryObjectsToStore;
        }
        for (JsonNode version : versions) {
            for (JsonNode binaryObject : version) {
                Optional<Entry<String, BinaryObjectInfo>> objectEntry =
                    sedaUtilInfo.getBinaryObjectMap().entrySet().stream()
                        .filter(entry -> entry.getKey().equals(binaryObject.get("_id").asText())).findFirst();
                if (objectEntry.isPresent()) {
                    binaryObjectsToStore.add(objectEntry.get().getValue());
                }
            }
        }

        return binaryObjectsToStore;
    }

    /**
     * Parse SEDA file manifest.xml to retrieve all its binary data objects informations as a SedaUtilInfo.
     * 
     * @param workspaceClient workspace connector
     * @param containerId container id
     * @return SedaUtilInfo
     * @throws ProcessingException throws when error occurs
     */
    private SedaUtilInfo getSedaUtilInfo(WorkspaceClient workspaceClient, String containerId)
        throws ProcessingException {
        InputStream xmlFile = null;
        try {
            xmlFile = workspaceClient.getObject(containerId, SEDA_FOLDER + "/" + SEDA_FILE);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.error("Manifest.xml Not Found");
            IOUtils.closeQuietly(xmlFile);
            throw new ProcessingException(e);
        }

        final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

        SedaUtilInfo sedaUtilInfo = null;
        XMLEventReader reader = null;
        try {
            reader = xmlInputFactory.createXMLEventReader(xmlFile);
            sedaUtilInfo = getBinaryObjectInfo(reader);
            return sedaUtilInfo;
        } catch (final XMLStreamException e) {
            LOGGER.error("Can not read SEDA");
            throw new ProcessingException(e);
        } finally {
            IOUtils.closeQuietly(xmlFile);
            try {
                reader.close();
            } catch (XMLStreamException e) {
                // nothing to throw
                LOGGER.info("Can not close XML reader SEDA");
            }
        }

    }

    /**
     * Retrieve a json file as a {@link JsonNode} from the workspace.
     * 
     * @param workspaceClient workspace connector
     * @param containerId container id
     * @param jsonFilePath path in workspace of the json File
     * @return JsonNode of the json file
     * @throws ProcessingException throws when error occurs
     */
    private JsonNode getJsonFromWorkspace(WorkspaceClient workspaceClient, String containerId, String jsonFilePath)
        throws ProcessingException {
        try (InputStream is = workspaceClient.getObject(containerId, jsonFilePath)) {
            if (is != null) {
                final String inputStreamString = CharStreams.toString(new InputStreamReader(is, "UTF-8"));
                return JsonHandler.getFromString(inputStreamString);
            } else {
                LOGGER.error("Object group not found");
                throw new ProcessingException("Object group not found");
            }

        } catch (InvalidParseOperationException | IOException e) {
            LOGGER.debug("Json wrong format", e);
            throw new ProcessingException(e);
        } catch (ContentAddressableStorageNotFoundException | ContentAddressableStorageServerException e) {
            LOGGER.debug("Workspace Server Error", e);
            throw new ProcessingException(e);
        }
    }
}