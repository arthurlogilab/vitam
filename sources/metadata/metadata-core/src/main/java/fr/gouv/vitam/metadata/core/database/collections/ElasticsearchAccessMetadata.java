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
package fr.gouv.vitam.metadata.core.database.collections;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest.OpType;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import com.mongodb.DBObject;
import com.mongodb.client.MongoCursor;

import fr.gouv.vitam.common.database.builder.request.configuration.BuilderToken.FILTERARGS;
import fr.gouv.vitam.common.database.builder.request.configuration.GlobalDatas;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchAccess;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.database.server.mongodb.VitamDocument;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.metadata.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.metadata.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.metadata.core.database.configuration.GlobalDatasDb;

/**
 * ElasticSearch model with MongoDB as main database
 */
public class ElasticsearchAccessMetadata extends ElasticsearchAccess {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ElasticsearchAccessMetadata.class);

    public static final String MAPPING_UNIT_FILE = "/mapping-unit.json";

    /**
     * @param clusterName
     * @param nodes
     * @throws VitamException
     */
    public ElasticsearchAccessMetadata(final String clusterName, List<ElasticsearchNode> nodes) throws VitamException {
        super(clusterName, nodes);
    }

    /**
     * Delete one index
     *
     * @param collection
     * @param tenantId
     * @return True if ok
     */
    public final boolean deleteIndex(final MetadataCollections collection, Integer tenantId) {
        try {
            if (client.admin().indices().prepareExists(getIndexName(collection, tenantId)).get().isExists()) {
                if (!client.admin().indices().prepareDelete(getIndexName(collection, tenantId)).get()
                    .isAcknowledged()) {
                    LOGGER.error("Error on index delete");
                }
            }
            return true;
        } catch (final Exception e) {
            LOGGER.error("Error while deleting index", e);
            return true;
        }
    }

    /**
     * Add a type to an index
     *
     * @param collection
     * @param tenantId
     * @return True if ok
     */
    public final boolean addIndex(final MetadataCollections collection, Integer tenantId) {
        LOGGER.debug("addIndex: " + getIndexName(collection, tenantId));
        if (!client.admin().indices().prepareExists(getIndexName(collection, tenantId)).get().isExists()) {
            try {
                LOGGER.debug("createIndex");
                final String mapping;
                final String type;
                if (collection == MetadataCollections.C_UNIT) {
                    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(Unit.class.getResourceAsStream(MAPPING_UNIT_FILE)))) {
                        mapping = buffer.lines().collect(Collectors.joining("\n"));
                    }
                    type = Unit.TYPEUNIQUE;
                }
                else {
                    type = ObjectGroup.TYPEUNIQUE;
                    mapping = ObjectGroup.MAPPING;

                }
                LOGGER.debug("setMapping: " + getIndexName(collection, tenantId) + " type: " + type + "\n\t" + mapping);
                final CreateIndexResponse response = client.admin().indices()
                    .prepareCreate(getIndexName(collection, tenantId))
                    .setSettings(Settings.builder().loadFromSource(DEFAULT_INDEX_CONFIGURATION))
                    .addMapping(type, mapping).get();
                if (!response.isAcknowledged()) {
                    LOGGER.error(type + ":" + response.isAcknowledged());
                    return false;
                }
            } catch (final Exception e) {
                LOGGER.error("Error while set Mapping", e);
                return false;
            }
        }
        return true;
    }

    /**
     * refresh an index
     *
     * @param collection
     * @param tenantId
     */
    public final void refreshIndex(final MetadataCollections collection, Integer tenantId) {
        LOGGER.debug("refreshIndex: " + collection.getName().toLowerCase() + "_" + tenantId);
        client.admin().indices().prepareRefresh(getIndexName(collection, tenantId)).execute().actionGet();

    }

    /**
     * Add an entry in the ElasticSearch index
     *
     * @param collection
     * @param tenantId
     * @param id
     * @param json
     * @return True if ok
     */
    final boolean addEntryIndex(final MetadataCollections collection, final Integer tenantId, final String id,
        final String json) {
        final String type = collection == MetadataCollections.C_UNIT ? Unit.TYPEUNIQUE : ObjectGroup.TYPEUNIQUE;
        return client.prepareIndex(getIndexName(collection, tenantId), type, id).setSource(json).setOpType(OpType.INDEX)
            .get().getVersion() > 0;
    }

    /**
     * Add a set of entries in the ElasticSearch index. <br>
     * Used in reload from scratch.
     *
     * @param collection
     * @param mapIdJson
     * @return the listener on bulk insert
     */

    final ListenableActionFuture<BulkResponse> addEntryIndexes(final MetadataCollections collection,
        final Integer tenantId, final Map<String, String> mapIdJson) {
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        // either use client#prepare, or use Requests# to directly build
        // index/delete requests
        final String type = collection == MetadataCollections.C_UNIT ? Unit.TYPEUNIQUE : ObjectGroup.TYPEUNIQUE;
        for (final Entry<String, String> val : mapIdJson.entrySet()) {
            bulkRequest.add(client.prepareIndex(getIndexName(collection, tenantId), type, val.getKey())
                .setSource(val.getValue()));
        }
        return bulkRequest.execute(); // new thread
    }

    /**
     * Add a set of entries in the ElasticSearch index in blocking mode. <br>
     * Used in reload from scratch.
     *
     * @param collection
     * @param mapIdJson
     * @return True if ok
     */
    final boolean addEntryIndexesBlocking(final MetadataCollections collection, final Integer tenantId,
        final Map<String, String> mapIdJson) {
        final BulkResponse bulkResponse = addEntryIndexes(collection, tenantId, mapIdJson).actionGet();
        if (bulkResponse.hasFailures()) {
            LOGGER.error("ES previous insert in error: " + bulkResponse.buildFailureMessage());
        }
        return !bulkResponse.hasFailures();
        // Should process failures by iterating through each bulk response item
    }

    /**
     * Method to filter what should never be indexed
     *
     * @param unit
     * @return the new Unit without the unwanted fields
     */
    private static final Unit getFiltered(final Unit unit) {
        final Unit eunit = new Unit(unit);
        eunit.remove(VitamLinks.UNIT_TO_UNIT.field1to2);
        return eunit;
    }

    /**
     * Add one VitamDocument to indexation immediately
     *
     * @param document
     * @param tenantId
     * @return True if inserted in ES
     */
    public final boolean addEntryIndex(final MetadataDocument<?> document, Integer tenantId) {
        MetadataDocument<?> newdoc = document;
        MetadataCollections collection = MetadataCollections.C_OBJECTGROUP;
        if (newdoc instanceof Unit) {
            collection = MetadataCollections.C_UNIT;
            newdoc = getFiltered((Unit) newdoc);
        }
        final String id = newdoc.getId();
        newdoc.remove(VitamDocument.ID);
        final String mongoJson = newdoc.toJson(new JsonWriterSettings(JsonMode.STRICT));
        newdoc.clear();
        // TODO P1 test bson4jackson
        // ( https://github.com/michel-kraemer/bson4jackson)
        final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
        final String esJson = dbObject.toString();
        return addEntryIndex(collection, tenantId, id, esJson);
    }

    /**
     * Used for iterative reload in restore operation (using bulk).
     *
     * @param indexes
     * @param tenantId
     * @param document
     * @return the number of Unit incorporated (0 if none)
     */
    public final int addBulkEntryIndex(final Map<String, String> indexes, final Integer tenantId,
        final MetadataDocument<?> document) {
        MetadataDocument<?> newdoc = document;
        MetadataCollections collection = MetadataCollections.C_OBJECTGROUP;
        if (newdoc instanceof Unit) {
            collection = MetadataCollections.C_UNIT;
            newdoc = getFiltered((Unit) newdoc);
        }
        final String id = newdoc.getId();
        newdoc.remove(VitamDocument.ID);

        final String mongoJson = newdoc.toJson(new JsonWriterSettings(JsonMode.STRICT));
        final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
        indexes.put(id, dbObject.toString());
        int nb = 0;
        if (indexes.size() > GlobalDatasDb.LIMIT_ES_NEW_INDEX) {
            nb = indexes.size();
            addEntryIndexes(collection, tenantId, indexes);
            indexes.clear();
        }
        newdoc.clear();
        return nb;
    }

    /**
     * Update an entry in the ElasticSearch index
     *
     * @param collection
     * @param id
     * @param json
     * @return True if ok
     * @throws Exception
     */
    final boolean updateEntryIndex(final MetadataCollections collection, final Integer tenantId, final String id,
        final String json) throws Exception {
        final String type = collection == MetadataCollections.C_UNIT ? Unit.TYPEUNIQUE : ObjectGroup.TYPEUNIQUE;
        return client.prepareUpdate(getIndexName(collection, tenantId), type, id).setDoc(json).setRefresh(true)
            .execute().actionGet().getVersion() > 1;
    }

    /**
     * Insert in bulk mode from cursor<br>
     * <br>
     * Insert a set of entries in the ElasticSearch index based in Cursor Result. <br>
     *
     * @param cursor :containing all Units to be indexed
     * @throws MetaDataExecutionException if the bulk insert failed
     */
    final void insertBulkUnitsEntriesIndexes(MongoCursor<Unit> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES insert in error since no results to insert");
            throw new MetaDataExecutionException("No result to insert");
        }
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        while (cursor.hasNext()) {
            final Unit unit = getFiltered(cursor.next());
            final String id = unit.getId();
            unit.remove(VitamDocument.ID);

            final String mongoJson = unit.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toInsert = dbObject.toString().trim();
            if (toInsert.isEmpty()) {
                LOGGER.error("ES insert in error since result to insert is empty");
                throw new MetaDataExecutionException("Result to insert is empty");
            }
            bulkRequest.add(client.prepareIndex(getIndexName(MetadataCollections.C_UNIT, tenantId), Unit.TYPEUNIQUE, id)
                .setSource(toInsert));
        }
        final BulkResponse bulkResponse = bulkRequest.setRefresh(true).execute().actionGet(); // new
        // thread
        if (bulkResponse.hasFailures()) {
            int duplicates = 0;
            for (BulkItemResponse bulkItemResponse : bulkResponse) {
                if (bulkItemResponse.getVersion() > 1) {
                    duplicates++;
                }
            }
            LOGGER.error("ES insert in error with possible duplicates {}: {}", duplicates,
                bulkResponse.buildFailureMessage());
            throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
        }
    }

    /**
     * updateBulkUnitsEntriesIndexes
     * <p>
     * Update a set of entries in the ElasticSearch index based in Cursor Result. <br>
     *
     * @param cursor :containing all Units to be indexed
     * @throws MetaDataExecutionException if the bulk update failed
     */
    final void updateBulkUnitsEntriesIndexes(MongoCursor<Unit> cursor, Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES update in error since no results to update");
            throw new MetaDataExecutionException("No result to update");
        }
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        while (cursor.hasNext()) {
            final Unit unit = getFiltered(cursor.next());
            final String id = unit.getId();
            unit.remove(VitamDocument.ID);

            final String mongoJson = unit.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toUpdate = dbObject.toString().trim();
            if (toUpdate.isEmpty()) {
                LOGGER.error("ES update in error since result to update is empty");
                throw new MetaDataExecutionException("Result to update is empty");
            }

            bulkRequest
                .add(client.prepareUpdate(getIndexName(MetadataCollections.C_UNIT, tenantId), Unit.TYPEUNIQUE, id)
                    .setDoc(toUpdate));
        }
        final BulkResponse bulkResponse = bulkRequest.setRefresh(true).execute().actionGet(); // new
        // thread
        if (bulkResponse.hasFailures()) {
            LOGGER.error("ES update in error: " + bulkResponse.buildFailureMessage());
            throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
        }
    }

    /**
     * @param collection
     * @param currentNodes current parent nodes
     * @param subdepth     where subdepth >= 1
     * @param condition
     * @param tenantId
     * @param filterCond
     * @return the Result associated with this request. Note that the exact depth is not checked, so it must be checked
     * after (using MongoDB)
     * @throws MetaDataExecutionException
     */
    public final Result getSubDepth(final MetadataCollections collection, Integer tenantId,
        final Set<String> currentNodes, final int subdepth, final QueryBuilder condition,
        final QueryBuilder filterCond) throws MetaDataExecutionException {
        QueryBuilder query = null;
        QueryBuilder filter = null;
        if (GlobalDatasDb.USE_FILTER) {
            filter = getSubDepthFilter(filterCond, currentNodes, subdepth);
            query = condition;
        } else {
            /*
             * filter where _ud (currentNodes as (grand)parents, depth<=subdepth)
             */
            QueryBuilder domdepths = null;
            if (subdepth == 1) {
                domdepths = QueryBuilders.boolQuery()
                    .should(QueryBuilders.termsQuery(VitamLinks.UNIT_TO_UNIT.field2to1, currentNodes));
            } else {
                domdepths = QueryBuilders.termsQuery(Unit.UNITUPS, currentNodes);
            }
            /*
             * Condition query
             */
            query = QueryBuilders.boolQuery().must(domdepths).must(condition);
            filter = filterCond;
        }

        final String type = collection == MetadataCollections.C_UNIT ? Unit.TYPEUNIQUE : ObjectGroup.TYPEUNIQUE;
        return search(collection, tenantId, type, query, filter);
    }

    /**
     * Build the filter for subdepth and currentNodes
     *
     * @param filterCond
     * @param currentNodes
     * @param key
     * @param subdepth     where subdepth >= 1
     * @return the associated filter
     */
    private final QueryBuilder getSubDepthFilter(final QueryBuilder filterCond, final Set<String> currentNodes,
        final int subdepth) {
        /*
         * filter where domdepths (currentNodes as (grand)parents, depth<=subdepth)
         */
        QueryBuilder domdepths = null;
        QueryBuilder filter = null;
        if (subdepth == 1) {
            filter = QueryBuilders.boolQuery()
                .should(QueryBuilders.termsQuery(VitamLinks.UNIT_TO_UNIT.field2to1, currentNodes));
            if (GlobalDatasDb.PRINT_REQUEST) {
                LOGGER.debug("Filter: terms {} = {}", VitamLinks.UNIT_TO_OBJECTGROUP.field2to1, currentNodes);
            }
        } else {
            filter = QueryBuilders.termsQuery(Unit.UNITUPS, currentNodes);
            if (GlobalDatasDb.PRINT_REQUEST) {
                LOGGER.debug("ESReq: terms {} = {}", Unit.UNITUPS, currentNodes);
            }
        }
        if (filterCond != null) {
            domdepths = QueryBuilders.boolQuery().must(filter).must(filterCond);
        } else {
            domdepths = filter;
        }
        return domdepths;
    }

    /**
     * Build the filter for depth = 0
     *
     * @param collection
     * @param currentNodes current parent nodes
     * @param condition
     * @param filterCond
     * @param tenantId
     * @return the Result associated with this request. Note that the exact depth is not checked, so it must be checked
     * after (using MongoDb)
     * @throws MetaDataExecutionException
     */
    public final Result getStart(final MetadataCollections collection, final Integer tenantId,
        final Set<String> currentNodes, final QueryBuilder condition, final QueryBuilder filterCond)
        throws MetaDataExecutionException {
        QueryBuilder query = null;
        QueryBuilder filter = null;
        if (GlobalDatasDb.USE_FILTER) {
            filter = getFilterStart(filterCond, currentNodes);
            query = condition;
        } else {
            /*
             * filter where _id in (currentNodes as list of ids)
             */
            final QueryBuilder domdepths = QueryBuilders.idsQuery((String[]) currentNodes.toArray());
            /*
             * Condition query
             */
            query = QueryBuilders.boolQuery().must(domdepths).must(condition);
            filter = filterCond;
        }
        final String type = collection == MetadataCollections.C_UNIT ? Unit.TYPEUNIQUE : ObjectGroup.TYPEUNIQUE;
        return search(collection, tenantId, type, query, filter);
    }

    /**
     * Build the filter for depth = 0
     *
     * @param filterCond
     * @param currentNodes
     * @param key
     * @return the associated filter
     */
    private final QueryBuilder getFilterStart(final QueryBuilder filterCond, final Set<String> currentNodes) {
        /*
         * filter where _id in (currentNodes as list of ids)
         */
        QueryBuilder domdepths = null;
        final IdsQueryBuilder filter = QueryBuilders.idsQuery((String[]) currentNodes.toArray());
        if (filterCond != null) {
            domdepths = QueryBuilders.boolQuery().must(filter).must(filterCond);
        } else {
            domdepths = filter;
        }
        return domdepths;
    }

    /**
     * Build the filter for negative depth
     *
     * @param collection
     * @param tenantId
     * @param subset     subset of valid nodes in the negative depth
     * @param condition
     * @param filterCond
     * @return the Result associated with this request. The final result should be checked using MongoDb.
     * @throws MetaDataExecutionException
     */
    public final Result getNegativeSubDepth(final MetadataCollections collection, final Integer tenantId,
        final Set<String> subset, final QueryBuilder condition, final QueryBuilder filterCond)
        throws MetaDataExecutionException {
        QueryBuilder query = null;
        QueryBuilder filter = null;

        if (GlobalDatasDb.USE_FILTER) {
            /*
             * filter where id from subset
             */
            TermsQueryBuilder filterTerms = null;
            filterTerms = QueryBuilders.termsQuery(MetadataDocument.ID, subset);
            if (filterCond != null) {
                filter = QueryBuilders.boolQuery().must(filterTerms).must(filterCond);
            } else {
                filter = filterTerms;
            }
            query = condition;
        } else {
            /*
             * filter where id from subset
             */
            QueryBuilder domdepths = null;
            domdepths = QueryBuilders.termsQuery(MetadataDocument.ID, subset);
            /*
             * Condition query
             */
            query = QueryBuilders.boolQuery().must(domdepths).must(condition);
            filter = filterCond;
        }
        final String type = collection == MetadataCollections.C_UNIT ? Unit.TYPEUNIQUE : ObjectGroup.TYPEUNIQUE;
        return search(collection, tenantId, type, query, filter);
    }

    /**
     * @param collection
     * @param type
     * @param query      as in DSL mode "{ "fieldname" : "value" }" "{ "match" : { "fieldname" : "value" } }" "{ "ids" : { "
     *                   values" : [list of id] } }"
     * @param filter
     * @return a structure as ResultInterface
     * @throws MetaDataExecutionException
     */
    protected final Result search(final MetadataCollections collection, final Integer tenantId, final String type,
        final QueryBuilder query, final QueryBuilder filter) throws MetaDataExecutionException {
        // Note: Could change the code to allow multiple indexes and multiple
        // types
        final SearchRequestBuilder request = client.prepareSearch(getIndexName(collection, tenantId))
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setTypes(type).setExplain(false)
            .setSize(GlobalDatas.LIMIT_LOAD);
        if (filter != null) {
            if (GlobalDatasDb.USE_FILTERED_REQUEST) {
                final BoolQueryBuilder filteredQueryBuilder = QueryBuilders.boolQuery().must(query).filter(filter);
                request.setQuery(filteredQueryBuilder);
            } else {
                request.setQuery(query).setPostFilter(filter);
            }
        } else {
            request.setQuery(query);
        }
        if (GlobalDatasDb.PRINT_REQUEST) {
            LOGGER.warn("ESReq: {}", request);
        } else {
            LOGGER.debug("ESReq: {}", request);
        }
        final SearchResponse response;
        try {
            response = request.get();
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            throw new MetaDataExecutionException(e.getMessage(), e);
        }
        if (response.status() != RestStatus.OK) {
            LOGGER.error("Error " + response.status() + " from : " + request + ":" + query + " # " + filter);
            return null;
        }
        final SearchHits hits = response.getHits();
        if (hits.getTotalHits() > GlobalDatas.LIMIT_LOAD) {
            LOGGER.warn("Warning, more than " + GlobalDatas.LIMIT_LOAD + " hits: " + hits.getTotalHits());
        }
        if (hits.getTotalHits() == 0) {
            LOGGER.error("No result from : " + request);
            return null;
        }
        long nb = 0;
        final boolean isUnit = collection == MetadataCollections.C_UNIT;
        final Result resultRequest = isUnit ? MongoDbMetadataHelper.createOneResult(FILTERARGS.UNITS)
            : MongoDbMetadataHelper.createOneResult(FILTERARGS.OBJECTGROUPS);
        final Iterator<SearchHit> iterator = hits.iterator();
        while (iterator.hasNext()) {
            final SearchHit hit = iterator.next();
            final String id = hit.getId();
            final Map<String, Object> src = hit.getSource();
            if (src != null && isUnit) {
                final Object val = src.get(Unit.NBCHILD);
                if (val == null) {
                    LOGGER.error("Not found " + Unit.NBCHILD);
                } else if (val instanceof Integer) {
                    nb += (Integer) val;
                    if (GlobalDatasDb.PRINT_REQUEST) {
                        LOGGER.debug("Result: {} : {}", id, val);
                    }
                } else {
                    LOGGER.error("Not Integer: " + val.getClass().getName());
                }
            }
            resultRequest.addId(id);
        }
        resultRequest.setNbResult(nb);
        if (GlobalDatasDb.PRINT_REQUEST) {
            LOGGER.debug("FinalEsResult: {} : {}", resultRequest.getCurrentIds(), resultRequest.getNbResult());
        }
        return resultRequest;
    }

    /**
     * @param collections
     * @param tenantId
     * @param type
     * @param id
     * @throws MetaDataExecutionException
     * @throws MetaDataNotFoundException
     */
    public final void deleteEntryIndex(final MetadataCollections collections, Integer tenantId, final String type,
        final String id) throws MetaDataExecutionException, MetaDataNotFoundException {
        final DeleteRequestBuilder builder = client.prepareDelete(getIndexName(collections, tenantId), type, id);
        final DeleteResponse response;
        try {
            response = builder.setRefresh(true).get();
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            throw new MetaDataExecutionException(e.getMessage(), e);
        }
        if (!response.isFound()) {
            throw new MetaDataNotFoundException("Item not found when trying to delete");
        }
    }

    /**
     * create indexes during Object group insert
     *
     * @param cursor
     * @param tenantId
     * @throws MetaDataExecutionException
     */
    public void insertBulkOGEntriesIndexes(MongoCursor<ObjectGroup> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES insert in error since no results to insert");
            throw new MetaDataExecutionException("No result to insert");
        }
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        while (cursor.hasNext()) {
            final ObjectGroup og = cursor.next();
            final String id = og.getId();
            og.remove(VitamDocument.ID);

            final String mongoJson = og.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toInsert = dbObject.toString().trim();
            if (toInsert.isEmpty()) {
                LOGGER.error("ES insert in error since result to insert is empty");
                throw new MetaDataExecutionException("Result to insert is empty");
            }
            bulkRequest.add(client
                .prepareIndex(getIndexName(MetadataCollections.C_OBJECTGROUP, tenantId), ObjectGroup.TYPEUNIQUE, id)
                .setSource(toInsert));
        }
        final BulkResponse bulkResponse = bulkRequest.setRefresh(true).execute().actionGet(); // new
        // thread
        if (bulkResponse.hasFailures()) {
            int duplicates = 0;
            for (BulkItemResponse bulkItemResponse : bulkResponse) {
                if (bulkItemResponse.getVersion() > 1) {
                    duplicates++;
                }
            }
            LOGGER.error("ES insert in error with possible duplicates {}: {}", duplicates,
                bulkResponse.buildFailureMessage());
            throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
        }
    }

    /**
     * updateBulkOGEntriesIndexes
     * <p>
     * Update a set of entries in the ElasticSearch index based in Cursor Result. <br>
     *
     * @param cursor :containing all OG to be indexed
     * @throws MetaDataExecutionException if the bulk update failed
     */
    final boolean updateBulkOGEntriesIndexes(MongoCursor<ObjectGroup> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES update in error since no results to update");
            throw new MetaDataExecutionException("No result to update");
        }
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        while (cursor.hasNext()) {
            final ObjectGroup og = cursor.next();
            final String id = og.getId();
            og.remove(VitamDocument.ID);

            final String mongoJson = og.toJson(new JsonWriterSettings(JsonMode.STRICT));
            // TODO Empty variable (null) might be ignore here
            final DBObject dbObject = (DBObject) com.mongodb.util.JSON.parse(mongoJson);
            final String toUpdate = dbObject.toString().trim();
            if (toUpdate.isEmpty()) {
                LOGGER.error("ES update in error since result to update is empty");
                throw new MetaDataExecutionException("Result to update is empty");
            }

            bulkRequest.add(client.prepareUpdate(getIndexName(MetadataCollections.C_OBJECTGROUP, tenantId),
                ObjectGroup.TYPEUNIQUE, id).setDoc(toUpdate));
        }
        final BulkResponse bulkResponse = bulkRequest.setRefresh(true).execute().actionGet(); // new
        // thread
        if (bulkResponse.hasFailures()) {
            LOGGER.error("ES update in error: " + bulkResponse.buildFailureMessage());
            throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
        }
        return true;
    }

    /**
     * deleteBulkOGEntriesIndexes
     * <p>
     * Bulk to delete entry indexes
     *
     * @param cursor
     * @param tenantId
     * @throws MetaDataExecutionException
     */
    public boolean deleteBulkOGEntriesIndexes(MongoCursor<ObjectGroup> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES delete in error since no results to delete");
            throw new MetaDataExecutionException("No result to delete");
        }
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        while (cursor.hasNext()) {
            final ObjectGroup og = cursor.next();
            final String id = og.getId();
            og.remove(VitamDocument.ID);
            bulkRequest.add(client.prepareDelete(getIndexName(MetadataCollections.C_OBJECTGROUP, tenantId),
                ObjectGroup.TYPEUNIQUE, id));
        }
        final BulkResponse bulkResponse = bulkRequest.setRefresh(true).execute().actionGet(); // new
        // thread
        if (bulkResponse.hasFailures()) {
            LOGGER.error("ES delete in error: " + bulkResponse.buildFailureMessage());
            throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
        }
        return true;

    }

    /**
     * deleteBulkUnitEntriesIndexes
     * <p>
     * Bulk to delete entry indexes
     *
     * @param cursor
     * @param tenantId
     * @throws MetaDataExecutionException
     */
    public void deleteBulkUnitsEntriesIndexes(MongoCursor<Unit> cursor, final Integer tenantId)
        throws MetaDataExecutionException {
        if (!cursor.hasNext()) {
            LOGGER.error("ES delete in error since no results to delete");
            throw new MetaDataExecutionException("No result to delete");
        }
        final BulkRequestBuilder bulkRequest = client.prepareBulk();
        while (cursor.hasNext()) {
            final Unit unit = cursor.next();
            final String id = unit.getId();
            unit.remove(VitamDocument.ID);
            bulkRequest
                .add(client.prepareDelete(getIndexName(MetadataCollections.C_UNIT, tenantId), Unit.TYPEUNIQUE, id));
        }
        final BulkResponse bulkResponse = bulkRequest.setRefresh(true).execute().actionGet(); // new
        // thread
        if (bulkResponse.hasFailures()) {
            LOGGER.error("ES delete in error: " + bulkResponse.buildFailureMessage());
            throw new MetaDataExecutionException(bulkResponse.buildFailureMessage());
        }
    }

    private String getIndexName(final MetadataCollections collection, Integer tenantId) {
        return collection.getName().toLowerCase() + "_" + tenantId.toString();
    }
}
