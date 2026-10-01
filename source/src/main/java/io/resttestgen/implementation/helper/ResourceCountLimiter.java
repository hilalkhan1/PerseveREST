package io.resttestgen.implementation.helper;

import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.datatype.parameter.leaves.NumberParameter;
import io.resttestgen.core.helper.ObjectHelper;
import io.resttestgen.core.openapi.Operation;

import java.util.Set;

/**
 * Keeps requests from asking the API under test for huge amounts of resources. For example, the request body
 * {"partitions_count": 1655500739} makes Kafka REST Proxy try to create over a billion partitions, after which it
 * stops responding and the rest of the testing session is lost. Numbers of parameters whose names describe an
 * amount of resources to allocate (partitions, replicas, threads...) are capped: negative and small values are
 * still tested, huge ones are not.
 */
public class ResourceCountLimiter {

    // Small: the API keeps what it allocates for the rest of the session (e.g., every topic of Kafka keeps its
    // partitions), and hundreds of successful requests can accumulate
    public static final long MAX_RESOURCE_COUNT = 10;

    // Words that, in a parameter name, describe an amount of resources the API would allocate
    private static final Set<String> RESOURCE_WORDS = Set.of("count", "partition", "partitions", "replica",
            "replicas", "replication", "thread", "threads", "worker", "workers", "capacity", "pool");

    public static void limit(Operation operation) {
        for (LeafParameter leaf : operation.getLeaves()) {
            if (leaf instanceof NumberParameter && isResourceCount(leaf.getName().toString())) {
                try {
                    if (ObjectHelper.castToNumber(leaf.getConcreteValue()).doubleValue() > MAX_RESOURCE_COUNT) {
                        leaf.setValueManually(MAX_RESOURCE_COUNT);
                    }
                } catch (ClassCastException | NullPointerException ignored) {
                    // Not a number (e.g., a wrong-type mutation): nothing to limit
                }
            }
        }
    }

    /**
     * Whether a parameter name (camelCase, snake_case, kebab-case or dotted) contains a resource word.
     * Whole words only: "partitions_count" matches, "accountId" does not.
     */
    static boolean isResourceCount(String name) {
        for (String word : name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase().split("[^a-z]+")) {
            if (RESOURCE_WORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
