package io.resttestgen.implementation.helper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestResourceCountLimiter {

    @Test
    public void testResourceCountNames() {
        Assertions.assertTrue(ResourceCountLimiter.isResourceCount("partitions_count"));
        Assertions.assertTrue(ResourceCountLimiter.isResourceCount("replication_factor"));
        Assertions.assertTrue(ResourceCountLimiter.isResourceCount("numThreads"));
        Assertions.assertTrue(ResourceCountLimiter.isResourceCount("num.network.threads"));
        Assertions.assertTrue(ResourceCountLimiter.isResourceCount("worker-count"));
    }

    @Test
    public void testOtherNames() {
        Assertions.assertFalse(ResourceCountLimiter.isResourceCount("accountId"));
        Assertions.assertFalse(ResourceCountLimiter.isResourceCount("discount"));
        Assertions.assertFalse(ResourceCountLimiter.isResourceCount("petId"));
        Assertions.assertFalse(ResourceCountLimiter.isResourceCount("limit"));
    }
}
