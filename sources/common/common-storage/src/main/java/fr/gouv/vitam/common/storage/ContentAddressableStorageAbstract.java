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
package fr.gouv.vitam.common.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.httpclient.util.URIUtil;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.ContainerNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.blobstore.options.ListContainerOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fr.gouv.vitam.common.CharsetUtils;
import fr.gouv.vitam.common.CommonMediaType;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.client.AbstractMockClient;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.server.application.VitamHttpHeader;
import fr.gouv.vitam.common.storage.api.ContentAddressableStorage;
import fr.gouv.vitam.common.storage.compress.VitamArchiveStreamFactory;
import fr.gouv.vitam.common.storage.constants.ErrorMessage;
import fr.gouv.vitam.common.storage.constants.StorageMessage;
import fr.gouv.vitam.common.storage.utils.UriUtils;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageCompressedFileException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.api.model.ContainerInformation;

/**
 * Abstract Content Addressable Storage
 */
public abstract class ContentAddressableStorageAbstract implements ContentAddressableStorage {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ContentAddressableStorageAbstract.class);

    /**
     * Max result for listing option TODO: have to be configurable ?
     */
    private static final int LISTING_MAX_RESULTS = 100;

    // FIXME P1: the BlobStoreContext should be build for each call, since it is
    // as a HttpClient. For now (Filesystem),
    // that's fine.
    protected final BlobStoreContext context;
    /**
     * maximum list size of the blob store. In S3, Azure, and Swift, this is
     * 1000, 5000, and 10000 respectively
     * 
     * @see <a href="https://jclouds.apache.org/start/blobstore/">Large
     *      lists</a>
     */

    private int maxResults = 51000;

    private StorageConfiguration configuration;

    /**
     * creates a new ContentAddressableStorageImpl with a storage configuration
     * param
     *
     * @param configuration
     *            {@link StorageConfiguration}
     */
    public ContentAddressableStorageAbstract(StorageConfiguration configuration) {
        this.setConfiguration(configuration);
        context = getContext(configuration);
    }

    /**
     * enables the connection to a storage service with the param provided
     *
     * @param configuration
     * @return BlobStoreContext
     */
    public abstract BlobStoreContext getContext(StorageConfiguration configuration);

    /**
     * Close context according to implementation (http client not closed)
     */
    public abstract void closeContext();

    @Override
    public void createContainer(String containerName) throws ContentAddressableStorageAlreadyExistException {
        LOGGER.info(" create container : " + containerName);
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);
        try {
            if (!context.getBlobStore().createContainerInLocation(null, containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_ALREADY_EXIST.getMessage() + containerName);
                throw new ContentAddressableStorageAlreadyExistException(
                        ErrorMessage.CONTAINER_ALREADY_EXIST.getMessage() + containerName);
            }
        } finally {
            closeContext();
        }

    }

    @Override
    public void purgeContainer(String containerName) throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);
        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            } else {
                blobStore.clearContainer(containerName);
            }

        } finally {
            closeContext();
        }

    }

    @Override
    public void deleteContainer(String containerName, boolean recursive) throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }
            if (recursive) {
                blobStore.deleteContainer(containerName);
            } else {
                blobStore.deleteContainerIfEmpty(containerName);
            }

        } finally {
            closeContext();
        }

    }

    @Override
    public boolean isExistingContainer(String containerName) {
        try {
            return context.getBlobStore().containerExists(containerName);
        } finally {
            closeContext();
        }
    }

    @Override
    public long countObjects(String containerName) throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);
        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }
            return blobStore.countBlobs(containerName);
        } finally {
            closeContext();
        }
    }

    @Override
    public void createFolder(String containerName, String folderName)
            throws ContentAddressableStorageNotFoundException, ContentAddressableStorageAlreadyExistException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_FOLDER_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
        try {

            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }

            if (isExistingFolder(containerName, folderName)) {
                LOGGER.error(ErrorMessage.FOLDER_ALREADY_EXIST + folderName);
                throw new ContentAddressableStorageAlreadyExistException(
                        ErrorMessage.FOLDER_ALREADY_EXIST.getMessage() + folderName);
            }
            blobStore.createDirectory(containerName, folderName);

        } finally {
            closeContext();
        }

    }

    @Override
    public void deleteFolder(String containerName, String folderName) throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_FOLDER_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingFolder(containerName, folderName)) {
                LOGGER.error(ErrorMessage.FOLDER_NOT_FOUND.getMessage() + folderName);
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.FOLDER_NOT_FOUND.getMessage() + folderName);
            }
            // FIXME P1 REVIEW should it be a check of emptyness?
            blobStore.deleteDirectory(containerName, folderName);
        } finally {
            closeContext();
        }
    }

    @Override
    public boolean isExistingFolder(String containerName, String folderName) {
        try {
            final BlobStore blobStore = context.getBlobStore();
            return blobStore.containerExists(containerName) && blobStore.directoryExists(containerName, folderName);
        } finally {
            closeContext();
        }
    }

    @Override
    public void putObject(String containerName, String objectName, InputStream stream) throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        final BlobStore blobStore = context.getBlobStore();
        try {
            if (isExistingObject(containerName, objectName)) {
                LOGGER.info(ErrorMessage.OBJECT_ALREADY_EXIST.getMessage() + objectName);
            }

            final Blob blob = blobStore.blobBuilder(objectName).payload(stream).build();
            blobStore.putBlob(containerName, blob);
        } catch (final ContainerNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            throw new ContentAddressableStorageNotFoundException(e);
        } catch (final Exception e) {
            LOGGER.error("Rollback", e.getMessage());
            blobStore.removeBlob(containerName, objectName);
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
            StreamUtils.closeSilently(stream);
        }
    }

    @Override
    public Response getObject(String containerName, String objectName) throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingObject(containerName, objectName)) {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName + " in container '" + containerName + "'");
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }

            final Blob blob = blobStore.getBlob(containerName, objectName);
            if (null != blob) {
                return new AbstractMockClient.FakeInboundResponse(Status.OK, blob.getPayload().openStream(),
                        MediaType.APPLICATION_OCTET_STREAM_TYPE, getXContentLengthHeader(blob));
            } else {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName + " in container '" + containerName + "'");
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }
        } catch (final ContainerNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            throw new ContentAddressableStorageNotFoundException(e);
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e.getMessage());
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage());
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
        }
    }

    private MultivaluedHashMap<String, Object> getXContentLengthHeader(Blob blob) {
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        List<Object> headersList = new ArrayList<>();
        headersList.add(blob.getMetadata().getSize().toString());
        headers.put(VitamHttpHeader.X_CONTENT_LENGTH.getName(), headersList);
        return headers;
    }

    @Override
    public Response getObjectAsync(String containerName, String objectName, AsyncResponse asyncResponse)
            throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingObject(containerName, objectName)) {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName + " in container '" + containerName + "'");
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }

            final Blob blob = blobStore.getBlob(containerName, objectName);
            if (null != blob) {
                return new AbstractMockClient.FakeInboundResponse(Status.OK, blob.getPayload().openStream(),
                        MediaType.APPLICATION_OCTET_STREAM_TYPE, getXContentLengthHeader(blob));
            } else {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName + " in container '" + containerName + "'");
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }
        } catch (final ContainerNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            throw new ContentAddressableStorageNotFoundException(e);
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e.getMessage());
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage());
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
        }
    }

    @Override
    public void deleteObject(String containerName, String objectName) throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingContainer(containerName) || !isExistingObject(containerName, objectName)) {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }

            blobStore.removeBlob(containerName, objectName);
        } finally {
            closeContext();
        }
    }

    @Override
    public String computeObjectDigest(String containerName, String objectName, DigestType algo)
            throws ContentAddressableStorageNotFoundException, ContentAddressableStorageException {

        ParametersChecker.checkParameter(ErrorMessage.ALGO_IS_A_MANDATORY_PARAMETER.getMessage(), algo);
        try (final InputStream stream = (InputStream) getObject(containerName, objectName).getEntity()) {
            final Digest digest = new Digest(algo);
            digest.update(stream);
            return digest.toString();
        } catch (final IOException e) {
            LOGGER.error(e.getMessage());
            throw new ContentAddressableStorageException(e);
        } catch (final ContentAddressableStorageException e) {
            LOGGER.error(e.getMessage());
            throw e;
        }
    }

    @Override
    public boolean isExistingObject(String containerName, String objectName) {
        try {
            boolean isExists = false;
            final BlobStore blobStore = context.getBlobStore();
            try {
                isExists = blobStore.blobExists(containerName, objectName);
            } catch (Exception e) {
                LOGGER.info(e.getMessage());
            }
            return isExists;
        } finally {
            closeContext();
        }
    }

    @Override
    public List<URI> getListUriDigitalObjectFromFolder(String containerName, String folderName)
            throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);
        ParametersChecker.checkParameter(ErrorMessage.FOLDER_NOT_FOUND.getMessage(), folderName);
        List<URI> uriFolderListFromContainer;
        try {
            final BlobStore blobStore = context.getBlobStore();
            // It's like a filter
            final ListContainerOptions listContainerOptions = new ListContainerOptions();
            StorageConfiguration storeConfig = getConfiguration();
            // List of all resources in a container recursively
            final PageSet<? extends StorageMetadata> blobStoreList = blobStore.list(containerName,
                    listContainerOptions.inDirectory(folderName).recursive().maxResults(getMaxResults()));

            uriFolderListFromContainer = new ArrayList<>();
            LOGGER.debug(StorageMessage.BEGINNING_GET_URI_LIST_OF_DIGITAL_OBJECT.getMessage());

            // TODO P1
            // Get the uri
            // Today the uri null so

            // The temporary solution is to concat the file path to the
            // generated GUID

            // if (storageMetada.getUri()!=null) {
            // uriListFromContainer.add(storageMetada.getUri());
            // }

            for (final StorageMetadata storageMetada : blobStoreList) {
                // select BLOB only, not folder nor relative path
                if (storageMetada.getType().equals(StorageType.BLOB) && storageMetada.getName() != null
                        && !storageMetada.getName().isEmpty()) {
                    String uri = URIUtil.encodeQuery(UriUtils.splitUri(storageMetada.getName()), CharsetUtils.UTF_8);
                    uriFolderListFromContainer.add(new URI(uri));
                }
            }
            LOGGER.debug(StorageMessage.ENDING_GET_URI_LIST_OF_DIGITAL_OBJECT.getMessage());
        } catch (final ContainerNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            throw new ContentAddressableStorageNotFoundException(e);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage());
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
        }
        return uriFolderListFromContainer;
    }

    @Override
    public void uncompressObject(String containerName, String folderName, String archiveMimeType, InputStream inputStreamObject)
            throws ContentAddressableStorageNotFoundException, ContentAddressableStorageAlreadyExistException,
            ContentAddressableStorageServerException, ContentAddressableStorageCompressedFileException,
            ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, folderName);
        LOGGER.debug("init unzip method  ...");

        if (!isExistingContainer(containerName)) {
            throw new ContentAddressableStorageNotFoundException(ErrorMessage.CONTAINER_NOT_FOUND.getMessage());
        }

        if (inputStreamObject == null) {
            throw new ContentAddressableStorageException(ErrorMessage.STREAM_IS_NULL.getMessage());
        }

        if (isExistingFolder(containerName, folderName)) {
            LOGGER.error(ErrorMessage.FOLDER_ALREADY_EXIST.getMessage() + ":folderName" + folderName);
            throw new ContentAddressableStorageAlreadyExistException(ErrorMessage.FOLDER_ALREADY_EXIST.getMessage());
        }
        LOGGER.debug("create folder name " + folderName);

        createFolder(containerName, folderName);

        extractArchiveInputStreamOnContainer(containerName, folderName, CommonMediaType.valueOf(archiveMimeType),
                inputStreamObject);

    }

    @Override
    public abstract ContainerInformation getContainerInformation(String containerName)
            throws ContentAddressableStorageNotFoundException;

    /**
     * Extract compressed SIP and push the objects on the SIP folder
     *
     * @param containerName
     *            GUID
     * @param folderName
     *            folder Name
     * @param archiverType
     *            archive type zip, tar tar.gz
     * @param inputStreamObject
     *            :compressed SIP stream
     * @throws ContentAddressableStorageCompressedFileException
     *             if the file is not a zip or an empty zip
     * @throws ContentAddressableStorageException
     *             if an IOException occurs when extracting the file
     * @throws ArchiveException
     */
    private void extractArchiveInputStreamOnContainer(final String containerName, final String folderName,
            final MediaType archiverType, final InputStream inputStreamObject)
            throws ContentAddressableStorageException, ContentAddressableStorageCompressedFileException {

        try (final InputStream inputStreamClosable = StreamUtils.getRemainingReadOnCloseInputStream(inputStreamObject);
                final ArchiveInputStream archiveInputStream = new VitamArchiveStreamFactory()
                        .createArchiveInputStream(archiverType, inputStreamClosable);) {
            ArchiveEntry archiveEntry;
            boolean isEmpty = true;

            // create entryInputStream to resolve the stream closed problem
            final ArchiveEntryInputStream entryInputStream = new ArchiveEntryInputStream(archiveInputStream);

            while ((archiveEntry = archiveInputStream.getNextEntry()) != null) {

                LOGGER.debug("containerName : " + containerName + "    / ArchiveEntryName : " + archiveEntry.getName());

                isEmpty = false;
                if (archiveEntry.isDirectory()) {
                    continue;
                }
                // put object in container
                putObject(containerName, folderName + File.separator + archiveEntry.getName(), entryInputStream);
                LOGGER.debug("Container name: " + containerName);
                LOGGER.debug(
                        "IsExisiting: " + isExistingObject(containerName, folderName + File.separator + archiveEntry.getName())
                                + " = " + folderName + File.separator + archiveEntry.getName());
                // after put entry stream open stream to add a next
                entryInputStream.setClosed(false);
            }
            archiveInputStream.close();
            if (isEmpty) {
                throw new ContentAddressableStorageCompressedFileException("File is empty");
            }
        } catch (final IOException e) {
            LOGGER.error(e);
            throw new ContentAddressableStorageException(e);
        } catch (final ArchiveException e) {
            LOGGER.error(e);
            throw new ContentAddressableStorageException(e);
        }

    }

    @Override
    public JsonNode getObjectInformation(String containerName, String objectName) throws ContentAddressableStorageException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
                containerName, objectName);
        ObjectNode jsonNodeObjectInformation = null;
        try {
            final BlobStore blobStore = context.getBlobStore();

            if (!isExistingObject(containerName, objectName)) {
                LOGGER.error(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND.getMessage() + objectName);
            }
            final Blob blob = blobStore.getBlob(containerName, objectName);
            if (null != blob && null != blob.getMetadata()) {
                final Long size = blob.getMetadata().getSize();
                jsonNodeObjectInformation = JsonHandler.createObjectNode();
                jsonNodeObjectInformation.put("size", size);
                jsonNodeObjectInformation.put("object_name", objectName);
                jsonNodeObjectInformation.put("container_name", containerName);
            }
        } catch (final ContainerNotFoundException e) {
            LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            throw new ContentAddressableStorageNotFoundException(e);
        } catch (final ContentAddressableStorageNotFoundException e) {
            LOGGER.error(e.getMessage());
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage());
            throw new ContentAddressableStorageException(e);
        } finally {
            closeContext();
        }
        return jsonNodeObjectInformation;
    }

    /**
     * @return the configuration
     */
    public StorageConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @param configuration
     *            the configuration to set
     *
     * @return this
     */
    public ContentAddressableStorageAbstract setConfiguration(StorageConfiguration configuration) {
        this.configuration = configuration;
        return this;
    }

    /**
     * @return the maxResults
     */
    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Archive input streams <b>MUST</b> override the
     * {@link #read(byte[], int, int)} - or {@link #read()} - method so that
     * reading from the stream generates EOF for the end of data in each entry
     * as well as at the end of the file proper.
     */
    static class ArchiveEntryInputStream extends InputStream {

        InputStream inputStream;
        boolean closed = false;

        /**
         * @param archiveInputStream
         * @throws IOException
         */
        public ArchiveEntryInputStream(InputStream archiveInputStream) throws IOException {
            inputStream = archiveInputStream;
        }

        @Override
        public int available() throws IOException {
            if (closed) {
                return -1;
            }
            return inputStream.available();
        }

        @Override
        public long skip(long n) throws IOException {
            if (closed) {
                return -1;
            }
            return inputStream.skip(n);
        }

        @Override
        public int read() throws IOException {
            if (closed) {
                return -1;
            }
            return inputStream.read();

        }

        @Override
        public int read(byte[] b) throws IOException {
            if (closed) {
                return -1;
            }
            return inputStream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                return -1;
            }
            return inputStream.read(b, off, len);
        }

        @Override
        public void close() {
            closed = true;
        }

        /**
         * Allow to "fakely" reopen this InputStream
         *
         * @param isclosed
         */
        public void setClosed(boolean isclosed) {
            closed = isclosed;
        }

    }

    @Override
    public boolean checkObject(String containerName, String objectId, String digest, DigestType digestAlgorithm)
            throws ContentAddressableStorageException {
        String offerDigest = computeObjectDigest(containerName, objectId, digestAlgorithm);
        return offerDigest.equals(digest);
    }

    @Override
    public PageSet<? extends StorageMetadata> listContainer(String containerName)
            throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);

        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }

            ListContainerOptions options = new ListContainerOptions();
            options.maxResults(LISTING_MAX_RESULTS);
            return blobStore.list(containerName, options);
        } finally {
            closeContext();
        }
    }

    @Override
    public PageSet<? extends StorageMetadata> listContainerNext(String containerName, String nextMarker)
            throws ContentAddressableStorageNotFoundException {
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(), containerName);

        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!isExistingContainer(containerName)) {
                LOGGER.error(ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
                throw new ContentAddressableStorageNotFoundException(
                        ErrorMessage.CONTAINER_NOT_FOUND.getMessage() + containerName);
            }

            ListContainerOptions options = new ListContainerOptions();
            options.maxResults(LISTING_MAX_RESULTS);
            options.afterMarker(nextMarker);

            return blobStore.list(containerName, options);
        } finally {
            closeContext();
        }
    }

}
