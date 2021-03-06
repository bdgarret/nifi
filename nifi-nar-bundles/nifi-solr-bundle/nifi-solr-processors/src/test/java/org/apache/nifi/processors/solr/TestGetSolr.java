/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.nifi.processors.solr;

import org.apache.nifi.components.state.Scope;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.schema.access.SchemaAccessUtils;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TestGetSolr {

    static final String DEFAULT_SOLR_CORE = "testCollection";

    private SolrClient solrClient;

    @Before
    public void setup() {

        try {

            // create an EmbeddedSolrServer for the processor to use
            String relPath = getClass().getProtectionDomain().getCodeSource()
                    .getLocation().getFile() + "../../target";

            solrClient = EmbeddedSolrServerFactory.create(EmbeddedSolrServerFactory.DEFAULT_SOLR_HOME,
                    DEFAULT_SOLR_CORE, relPath);

            for (int i = 0; i < 10; i++) {
                SolrInputDocument doc = new SolrInputDocument();
                doc.addField("id", "doc" + i);
                doc.addField("created", new Date());
                doc.addField("string_single", "single" + i + ".1");
                doc.addField("string_multi", "multi" + i + ".1");
                doc.addField("string_multi", "multi" + i + ".2");
                doc.addField("integer_single", i);
                doc.addField("integer_multi", 1);
                doc.addField("integer_multi", 2);
                doc.addField("integer_multi", 3);
                doc.addField("double_single", 0.5 + i);
                solrClient.add(doc);

            }
            solrClient.commit();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @After
    public void teardown() {
        try {
            solrClient.close();
        } catch (Exception e) {
        }
    }

    @Test
    public void testLessThanBatchSizeShouldProduceOneFlowFile() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "20");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        runner.run();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 1);
    }

    @Test
    public void testNoResultsShouldProduceNoOutput() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.SOLR_QUERY, "integer_single:1000");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "1");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        runner.run();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 0);
    }

    @Test
    public void testSolrModes() throws IOException, SolrServerException {

    }

    @Test(expected = java.lang.AssertionError.class)
    public void testValidation() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "2");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_REC.getValue());

        runner.run(1);
    }

    @Test
    public void testCompletenessDespiteUpdates() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "1");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        runner.run(1,false, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 10);
        runner.clearTransferState();

        SolrInputDocument doc0 = new SolrInputDocument();
        doc0.addField("id", "doc0");
        doc0.addField("created", new Date());
        SolrInputDocument doc1 = new SolrInputDocument();
        doc1.addField("id", "doc1");
        doc1.addField("created", new Date());

        solrClient.add(doc0);
        solrClient.add(doc1);
        solrClient.commit();

        runner.run(1,true, false);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 2);
        runner.assertAllFlowFilesContainAttribute(CoreAttributes.MIME_TYPE.key());
    }

    @Test
    public void testCompletenessDespiteDeletions() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "1");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        runner.run(1,false, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 10);
        runner.clearTransferState();

        SolrInputDocument doc10 = new SolrInputDocument();
        doc10.addField("id", "doc10");
        doc10.addField("created", new Date());
        SolrInputDocument doc11 = new SolrInputDocument();
        doc11.addField("id", "doc11");
        doc11.addField("created", new Date());

        solrClient.add(doc10);
        solrClient.add(doc11);
        solrClient.deleteById("doc0");
        solrClient.deleteById("doc1");
        solrClient.deleteById("doc2");
        solrClient.commit();

        runner.run(1,true, false);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 2);
        runner.assertAllFlowFilesContainAttribute(CoreAttributes.MIME_TYPE.key());
    }

    @Test
    public void testInitialDateFilter() throws IOException, SolrServerException {
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        final Date dateToFilter = new Date();

        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.DATE_FILTER, df.format(dateToFilter));
        runner.setProperty(GetSolr.BATCH_SIZE, "1");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        SolrInputDocument doc10 = new SolrInputDocument();
        doc10.addField("id", "doc10");
        doc10.addField("created", new Date());
        SolrInputDocument doc11 = new SolrInputDocument();
        doc11.addField("id", "doc11");
        doc11.addField("created", new Date());

        solrClient.add(doc10);
        solrClient.add(doc11);
        solrClient.commit();

        runner.run(1,true, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 2);
        runner.assertAllFlowFilesContainAttribute(CoreAttributes.MIME_TYPE.key());
    }

    @Test
    public void testPropertyModified() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "1");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        runner.run(1,false, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 10);
        runner.clearTransferState();

        // Change property contained in propertyNamesForActivatingClearState
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created,string_multi");
        runner.run(1, false, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 10);
        runner.clearTransferState();

        // Change property not contained in propertyNamesForActivatingClearState
        runner.setProperty(GetSolr.BATCH_SIZE, "2");
        runner.run(1, true, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 0);
        runner.clearTransferState();
    }

    @Test
    public void testStateCleared() throws IOException, SolrServerException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.RETURN_TYPE, GetSolr.MODE_XML.getValue());
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "1");
        runner.setProperty(GetSolr.RETURN_FIELDS, "id,created");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        runner.run(1,false, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 10);
        runner.clearTransferState();

        // run without clearing statemanager
        runner.run(1,false, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 0);
        runner.clearTransferState();

        // run with cleared statemanager
        runner.getStateManager().clear(Scope.CLUSTER);
        runner.run(1, true, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 10);
        runner.clearTransferState();


    }

    @Test
    public void testRecordWriter() throws IOException, SolrServerException, InitializationException {
        final org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor proc = new org.apache.nifi.processors.solr.TestGetSolr.TestableProcessor(solrClient);

        TestRunner runner = TestRunners.newTestRunner(proc);
        runner.setProperty(GetSolr.SOLR_TYPE, PutSolrContentStream.SOLR_TYPE_CLOUD.getValue());
        runner.setProperty(GetSolr.SOLR_LOCATION, "http://localhost:8443/solr");
        runner.setProperty(GetSolr.DATE_FIELD, "created");
        runner.setProperty(GetSolr.BATCH_SIZE, "2");
        runner.setProperty(GetSolr.COLLECTION, "testCollection");

        final String outputSchemaText = new String(Files.readAllBytes(Paths.get("src/test/resources/test-schema.avsc")));

        final JsonRecordSetWriter jsonWriter = new JsonRecordSetWriter();
        runner.addControllerService("writer", jsonWriter);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_ACCESS_STRATEGY, SchemaAccessUtils.SCHEMA_TEXT_PROPERTY);
        runner.setProperty(jsonWriter, SchemaAccessUtils.SCHEMA_TEXT, outputSchemaText);
        runner.setProperty(jsonWriter, "Pretty Print JSON", "true");
        runner.setProperty(jsonWriter, "Schema Write Strategy", "full-schema-attribute");
        runner.enableControllerService(jsonWriter);
        runner.setProperty(GetSolr.RECORD_WRITER, "writer");

        runner.run(1,true, true);
        runner.assertQueueEmpty();
        runner.assertAllFlowFilesTransferred(GetSolr.REL_SUCCESS, 5);
        runner.assertAllFlowFilesContainAttribute(CoreAttributes.MIME_TYPE.key());
    }

    // Override createSolrClient and return the passed in SolrClient
    private class TestableProcessor extends GetSolr {
        private SolrClient solrClient;

        public TestableProcessor(SolrClient solrClient) {
            this.solrClient = solrClient;
        }
        @Override
        protected SolrClient createSolrClient(ProcessContext context, String solrLocation) {
            return solrClient;
        }
    }
}
