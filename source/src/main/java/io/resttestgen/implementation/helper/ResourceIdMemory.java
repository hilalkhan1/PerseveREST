package io.resttestgen.implementation.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.openapi.Operation;

import java.util.*;

/**
 * Reuses the identifiers of the resources that the API created or listed in the path parameters that refer to them.
 * <p>
 * RestTestGen links values to parameters by name, so the "id" returned by POST /hospitals is not used for
 * /hospitals/{hospital_id}. Here the identifiers in the successful responses of an operation are remembered for its
 * collection, i.e., its endpoint without a final path parameter (/hospitals for both POST /hospitals and
 * GET /hospitals/{hospital_id}), and a path parameter whose name ends with "id" can get one of the identifiers
 * remembered for the collection that precedes it in the endpoint.
 * <p>
 * The values of the other fields of the responses are remembered by name, for the path parameters with the same name.
 * RestTestGen does link them, but it prefers the examples of the specification (in about 90% of the requests when
 * there is one): for example, the cluster of Kafka REST Proxy was "cluster-1", the example, instead of the identifier
 * listed by GET /v3/clusters in the "cluster_id" field, so most requests failed with "Cluster cluster-1 could not be
 * found".
 * <p>
 * Before a request is sent, each path parameter with remembered values usually gets one of them.
 */
public class ResourceIdMemory {

    private static final int REUSE_PERCENTAGE = 70;
    private static final int MAX_IDS_PER_COLLECTION = 50;
    private static final int MAX_VALUES_PER_NAME = 50;
    // Responses can use values as field names (e.g., maps keyed by the inputs): new names are ignored beyond this
    private static final int MAX_NAMES = 5000;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_ARRAY_ITEMS = 200;
    private static final int MAX_VALUE_LENGTH = 200;
    private static final Set<String> ID_FIELDS = Set.of("id", "_id", "uuid");

    private static final Map<String, List<String>> idsByCollection = new HashMap<>();
    private static final Map<String, List<String>> valuesByName = new HashMap<>();
    private static final Random random = new Random();

    /**
     * Remembers the identifiers and the other values in the body of a successful response to an operation with the
     * given endpoint.
     */
    public static synchronized void record(String endpoint, String responseBody) {
        JsonElement body = parse(responseBody);
        if (body == null) {
            return;
        }
        for (String id : idsIn(body)) {
            remember(idsByCollection, collectionOf(endpoint), id, MAX_IDS_PER_COLLECTION);
        }
        recordValuesByName(body, 0);
    }

    /**
     * Usually sets the path parameters of the operation to identifiers of existing resources, or to values that the
     * responses contained in fields with the same name.
     */
    public static synchronized void apply(Operation operation) {
        for (Parameter parameter : operation.getPathParameters()) {
            if (!(parameter instanceof LeafParameter)) {
                continue;
            }
            List<String> candidates = candidatesFor(operation.getEndpoint(), parameter.getName().toString());
            if (!candidates.isEmpty() && random.nextInt(100) < REUSE_PERCENTAGE) {
                String value = candidates.get(random.nextInt(candidates.size()));
                LeafParameter leaf = (LeafParameter) parameter;
                if (leaf.isObjectTypeCompliant(value)) {
                    leaf.setValueManually(value);
                }
            }
        }
    }

    /**
     * The remembered values for a path parameter: the identifiers of the collection that precedes it in the endpoint,
     * if its name designates an identifier, and the values of the response fields with the same (normalized) name.
     */
    static synchronized List<String> candidatesFor(String endpoint, String parameterName) {
        List<String> candidates = new ArrayList<>();
        if (isIdName(parameterName)) {
            List<String> segments = Arrays.asList(endpoint.split("/"));
            int index = segments.indexOf("{" + parameterName + "}");
            if (index >= 0) {
                candidates.addAll(idsByCollection.getOrDefault(String.join("/", segments.subList(0, index)), List.of()));
            }
        }
        // Generic identifier names ("id") would mix the identifiers of all collections
        if (!ID_FIELDS.contains(parameterName.toLowerCase())) {
            candidates.addAll(valuesByName.getOrDefault(normalizedName(parameterName), List.of()));
        }
        return candidates;
    }

    private static void remember(Map<String, List<String>> memory, String key, String value, int max) {
        List<String> known = memory.computeIfAbsent(key, k -> new LinkedList<>());
        known.remove(value);
        known.add(value);
        while (known.size() > max) {
            known.remove(0);
        }
    }

    private static void recordValuesByName(JsonElement element, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        if (element.isJsonArray()) {
            int items = 0;
            for (JsonElement item : element.getAsJsonArray()) {
                if (items++ >= MAX_ARRAY_ITEMS) {
                    break;
                }
                recordValuesByName(item, depth + 1);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                JsonElement value = field.getValue();
                if (value.isJsonPrimitive()) {
                    String string = value.getAsString();
                    String name = normalizedName(field.getKey());
                    if (!string.isBlank() && string.length() <= MAX_VALUE_LENGTH &&
                            (valuesByName.size() < MAX_NAMES || valuesByName.containsKey(name))) {
                        remember(valuesByName, name, string, MAX_VALUES_PER_NAME);
                    }
                } else {
                    recordValuesByName(value, depth + 1);
                }
            }
        }
    }

    /**
     * The name without case and separators: "cluster_id", "cluster-id" and "clusterId" are the same.
     */
    static String normalizedName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * The collection of an endpoint: the endpoint without the trailing slash and without a final path parameter.
     */
    static String collectionOf(String endpoint) {
        String collection = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (collection.endsWith("}") && collection.contains("/")) {
            collection = collection.substring(0, collection.lastIndexOf('/'));
        }
        return collection;
    }

    /**
     * Whether a parameter name designates an identifier: "id", "hospital_id", "hospital-id", "hospitalId", "uuid".
     */
    static boolean isIdName(String name) {
        String lowerCase = name.toLowerCase();
        return lowerCase.equals("id") || lowerCase.equals("uuid") || lowerCase.endsWith("_id") ||
                lowerCase.endsWith("-id") || name.matches(".*[a-z0-9]Id");
    }

    private static JsonElement parse(String responseBody) {
        try {
            return responseBody == null ? null : JsonParser.parseString(responseBody);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static List<String> idsIn(String responseBody) {
        JsonElement body = parse(responseBody);
        return body == null ? new ArrayList<>() : idsIn(body);
    }

    /**
     * The identifiers in a response body: the identifier of an object, or of the objects in an array, also when the
     * array or the object is wrapped in another object (e.g., {"content": [...]} or {"response": {...}}). Nested
     * objects are not considered, since their identifiers belong to other collections.
     */
    private static List<String> idsIn(JsonElement body) {
        List<String> ids = new ArrayList<>();
        if (body.isJsonObject() && !addDirectId(body, ids)) {
            // A wrapper: look for the identifiers in its objects and arrays
            for (Map.Entry<String, JsonElement> field : body.getAsJsonObject().entrySet()) {
                addIdsOfObjectOrArray(field.getValue(), ids);
            }
        } else if (body.isJsonArray()) {
            addIdsOfObjectOrArray(body, ids);
        }
        return ids;
    }

    private static void addIdsOfObjectOrArray(JsonElement element, List<String> ids) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> addDirectId(item, ids));
        } else {
            addDirectId(element, ids);
        }
    }

    private static boolean addDirectId(JsonElement element, List<String> ids) {
        if (!element.isJsonObject()) {
            return false;
        }
        for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
            if (ID_FIELDS.contains(field.getKey().toLowerCase()) && field.getValue().isJsonPrimitive()) {
                ids.add(field.getValue().getAsString());
                return true;
            }
        }
        return false;
    }
}
