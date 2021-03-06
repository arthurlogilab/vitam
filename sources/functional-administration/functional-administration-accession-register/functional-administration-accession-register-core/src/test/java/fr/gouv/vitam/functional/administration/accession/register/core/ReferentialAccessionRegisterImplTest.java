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
package fr.gouv.vitam.functional.administration.accession.register.core;

import static fr.gouv.vitam.common.database.builder.query.QueryHelper.eq;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.server.application.configuration.DbConfigurationImpl;
import fr.gouv.vitam.common.server.application.configuration.MongoDbNode;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.common.AccessionRegisterDetail;
import fr.gouv.vitam.functional.administration.common.AccessionRegisterSummary;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.server.MongoDbAccessAdminFactory;

public class ReferentialAccessionRegisterImplTest {
    static String FILE_TO_TEST_OK = "accession-register.json";
    File pronomFile = null;
    private static final Integer TENANT_ID = 0;

    @Rule
    public RunWithCustomExecutorRule runInThread =
        new RunWithCustomExecutorRule(VitamThreadPoolExecutor.getDefaultExecutor());

    static MongodExecutable mongodExecutable;
    static MongodProcess mongod;
    static MongoClient mongoClient;
    static JunitHelper junitHelper;
    static final String DATABASE_HOST = "localhost";
    static final String DATABASE_NAME = "vitam-test";
    static final String COLLECTION_NAME = "AccessionRegisterSummary";
    static int port;
    static ReferentialAccessionRegisterImpl accessionRegisterImpl;
    static AccessionRegisterDetail register;
    static MongoClient client;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        final MongodStarter starter = MongodStarter.getDefaultInstance();
        junitHelper = JunitHelper.getInstance();
        port = junitHelper.findAvailablePort();
        mongodExecutable = starter.prepare(new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net(port, Network.localhostIsIPv6()))
            .build());
        mongod = mongodExecutable.start();
        client = new MongoClient(new ServerAddress(DATABASE_HOST, port));

        final List<MongoDbNode> nodes = new ArrayList<>();
        nodes.add(new MongoDbNode(DATABASE_HOST, port));
        accessionRegisterImpl = new ReferentialAccessionRegisterImpl(
            MongoDbAccessAdminFactory.create(new DbConfigurationImpl(nodes, DATABASE_NAME)));
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        mongod.stop();
        mongodExecutable.stop();
        junitHelper.releasePort(port);
        client.close();
    }

    @After
    public void afterTest() {
        final MongoCollection<Document> collection = client.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
        collection.deleteMany(new Document());
    }

    @Test
    @RunWithCustomExecutor
    public void testcreateAccessionRegister() throws Exception {

    	VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        register = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(FILE_TO_TEST_OK),
                AccessionRegisterDetail.class);
            ReferentialAccessionRegisterImpl.resetIndexAfterImport();
            
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        final MongoCollection<Document> collection = client.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
        assertEquals(1, collection.count());
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        assertEquals(1, collection.count());
        final JsonNode totalUnit = JsonHandler.toJsonNode(collection.find().first().get("TotalUnits"));
        assertEquals(2, totalUnit.get("total").asInt());
        register.setOriginatingAgency("newOriginalAgency");
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        assertEquals(2, collection.count());
        
        VitamThreadUtils.getVitamSession().setTenantId(1);
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        assertEquals(3, collection.count());
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        assertEquals(3, collection.count());
    }

    @Test
    @RunWithCustomExecutor
    public void testFindAccessionRegisterDetail()
        throws ReferentialException, InvalidParseOperationException, InvalidCreateOperationException, FileNotFoundException {

    	VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        register = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(FILE_TO_TEST_OK),
            AccessionRegisterDetail.class);
        ReferentialAccessionRegisterImpl.resetIndexAfterImport();
        
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        final MongoCollection<Document> collection = client.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
        assertEquals(1, collection.count());

        final Select select = new Select();
        select.setQuery(eq("OriginatingAgency", "OriginatingAgency"));
        final List<AccessionRegisterDetail> detail = accessionRegisterImpl.findDetail(select.getFinalSelect());
        assertEquals(2, detail.size());
        final AccessionRegisterDetail item = detail.get(0);
        assertEquals("OriginatingAgency", item.getOriginatingAgency());
    }

    @Test
    @RunWithCustomExecutor
    public void testFindAccessionRegisterSummary()
        throws ReferentialException, InvalidParseOperationException, InvalidCreateOperationException, FileNotFoundException {

    	VitamThreadUtils.getVitamSession().setTenantId(TENANT_ID);
        register = JsonHandler.getFromInputStream(PropertiesUtils.getResourceAsStream(FILE_TO_TEST_OK),
            AccessionRegisterDetail.class);
        ReferentialAccessionRegisterImpl.resetIndexAfterImport();
        
        accessionRegisterImpl.createOrUpdateAccessionRegister(register);
        final MongoCollection<Document> collection = client.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME);
        assertEquals(1, collection.count());
        final Select select = new Select();
        select.setQuery(eq("OriginatingAgency", "OriginatingAgency"));
        final List<AccessionRegisterSummary> summary = accessionRegisterImpl.findDocuments(select.getFinalSelect());
        assertEquals(1, summary.size());
        final AccessionRegisterSummary item = summary.get(0);
        assertEquals("OriginatingAgency", item.getOriginatingAgency());
    }
}
