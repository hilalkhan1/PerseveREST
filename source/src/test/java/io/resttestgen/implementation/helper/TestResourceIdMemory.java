package io.resttestgen.implementation.helper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestResourceIdMemory {

    @Test
    public void testIdNames() {
        Assertions.assertTrue(ResourceIdMemory.isIdName("id"));
        Assertions.assertTrue(ResourceIdMemory.isIdName("hospital_id"));
        Assertions.assertTrue(ResourceIdMemory.isIdName("produto-id"));
        Assertions.assertTrue(ResourceIdMemory.isIdName("petTypeId"));
        Assertions.assertTrue(ResourceIdMemory.isIdName("uuid"));
        Assertions.assertFalse(ResourceIdMemory.isIdName("name"));
        Assertions.assertFalse(ResourceIdMemory.isIdName("paid"));
    }

    @Test
    public void testCollections() {
        Assertions.assertEquals("/v1/hospitais", ResourceIdMemory.collectionOf("/v1/hospitais/"));
        Assertions.assertEquals("/v1/hospitais", ResourceIdMemory.collectionOf("/v1/hospitais/{hospital_id}"));
        Assertions.assertEquals("/v1/hospitais/{hospital_id}/estoque",
                ResourceIdMemory.collectionOf("/v1/hospitais/{hospital_id}/estoque/{produto_id}"));
    }

    @Test
    public void testIdsInResponses() {
        // Gestao Hospital: a created hospital, and the list of hospitals
        Assertions.assertEquals(List.of("6ab9ae141a2685004140c994"), ResourceIdMemory.idsIn(
                "{\"id\":\"6ab9ae141a2685004140c994\",\"name\":\"Maria\",\"location\":{\"id\":\"x\"}}"));
        Assertions.assertEquals(List.of("1", "2"), ResourceIdMemory.idsIn("[{\"id\":\"1\"},{\"id\":\"2\"}]"));
        // Wrapped responses
        Assertions.assertEquals(List.of("7"), ResourceIdMemory.idsIn("{\"content\": [{\"id\": 7}], \"page\": 0}"));
        Assertions.assertEquals(List.of("a1"), ResourceIdMemory.idsIn("{\"isSuccess\": true, \"response\": {\"id\": \"a1\"}}"));
        // No identifiers
        Assertions.assertEquals(List.of(), ResourceIdMemory.idsIn("{\"name\": \"x\"}"));
        Assertions.assertEquals(List.of(), ResourceIdMemory.idsIn("not json"));
    }

    @Test
    public void testValuesByName() {
        // Kafka REST Proxy: the cluster listed by GET /v3/clusters, for the {cluster_id} of the other endpoints
        ResourceIdMemory.record("/v3/clusters", "{\"kind\":\"KafkaClusterList\",\"data\":[{\"kind\":\"KafkaCluster\"," +
                "\"cluster_id\":\"MkU3OEVBNTcwNTJENDM2Qg\",\"controller\":{\"related\":\"http://localhost:8082/v3/x\"}}]}");
        Assertions.assertEquals(List.of("MkU3OEVBNTcwNTJENDM2Qg"),
                ResourceIdMemory.candidatesFor("/v3/clusters/{cluster_id}/topics", "cluster_id"));
        Assertions.assertEquals(List.of("MkU3OEVBNTcwNTJENDM2Qg"),
                ResourceIdMemory.candidatesFor("/clusters/{clusterId}", "clusterId"));
        // Nested values (broker configs), not identifiers
        ResourceIdMemory.record("/v3/clusters/{cluster_id}/brokers/{broker_id}/configs", "{\"data\":[" +
                "{\"broker_id\":1,\"name\":\"compression.type\",\"value\":\"producer\"}," +
                "{\"broker_id\":1,\"name\":\"log.retention.ms\",\"value\":null}]}");
        Assertions.assertEquals(List.of("compression.type", "log.retention.ms"),
                ResourceIdMemory.candidatesFor("/v3/clusters/{cluster_id}/broker-configs/{name}", "name"));
        Assertions.assertEquals(List.of("1"),
                ResourceIdMemory.candidatesFor("/v3/clusters/{cluster_id}/brokers/{broker_id}", "broker_id"));
        // Generic identifiers only from the collection
        ResourceIdMemory.record("/owners", "{\"id\": 3, \"pets\": [{\"id\": 8}]}");
        Assertions.assertEquals(List.of("3"), ResourceIdMemory.candidatesFor("/owners/{id}", "id"));
        Assertions.assertEquals(List.of(), ResourceIdMemory.candidatesFor("/visits/{id}", "id"));
    }
}
