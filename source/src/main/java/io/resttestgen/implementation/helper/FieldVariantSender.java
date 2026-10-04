package io.resttestgen.implementation.helper;

import com.google.gson.*;
import io.resttestgen.core.testing.TestInteraction;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sends systematic invalid variants of an executed request, field by field: each property of the JSON body (up to
 * MAX_FIELDS), each query parameter and each path parameter gets invalid values for its kind: null, empty and very long
 * strings, values of another JSON type, numbers too large for any numeric type, negative and decimal numbers.
 * RestTestGen's mutators change one random parameter at a time, so the code that reads and validates many fields is
 * never reached with invalid values; trying every field reaches the faults of each of them. The variants are sent with
 * MalformedRequestSender's client.
 * <p>
 * Numbers of parameters that describe amounts of resources (see ResourceCountLimiter) never get huge values.
 */
public class FieldVariantSender {

    static final int MAX_FIELDS = 20;
    static final int MAX_DEPTH = 4;
    static final int LONG_STRING_LENGTH = 10000;
    static final int LONG_URL_VALUE_LENGTH = 1000;
    static final String HUGE_NUMBER = "99999999999999999999999";
    private static final MediaType JSON = MediaType.parse("application/json");

    /**
     * Sends the field variants of an executed interaction.
     * @return the bodies of the responses with a server error status.
     */
    public static List<String> sendVariants(TestInteraction interaction) {
        HttpUrl url = HttpUrl.parse(interaction.getRequestURL());
        if (url == null || interaction.getRequestMethod() == null) {
            return List.of();
        }
        String method = interaction.getRequestMethod().toString().toUpperCase();
        Headers headers = MalformedRequestSender.headersOf(interaction.getRequestHeaders());
        String body = interaction.getRequestBody();
        boolean hasBody = body != null && !body.isEmpty() &&
                (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"));
        RequestBody originalBody = hasBody ? RequestBody.create(body, JSON) : null;

        List<Request> variants = new ArrayList<>();
        if (hasBody) {
            for (String variant : bodyVariants(body)) {
                variants.add(MalformedRequestSender.request(url, method, headers, RequestBody.create(variant, JSON)));
            }
        }
        for (HttpUrl variant : queryVariants(url)) {
            variants.add(MalformedRequestSender.request(variant, method, headers, originalBody));
        }
        for (HttpUrl variant : pathVariants(url, interaction.getFuzzedOperation().getEndpoint())) {
            variants.add(MalformedRequestSender.request(variant, method, headers, originalBody));
        }
        return MalformedRequestSender.executeAll(variants, interaction.getFuzzedOperation());
    }

    // ---------------------------------------- JSON body ----------------------------------------

    /**
     * The variants of a JSON body: for each field (up to MAX_FIELDS, in document order), the body with that field
     * replaced by each invalid value for its kind. Objects and arrays are fields too, and their content is visited
     * (the first element of arrays). A body that is a single value is replaced as a whole.
     */
    static List<String> bodyVariants(String body) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return List.of();
        }
        List<List<Object>> paths = new ArrayList<>();
        if (root.isJsonPrimitive()) {
            paths.add(List.of());
        } else {
            collectFields(root, new ArrayList<>(), paths, 0);
        }
        List<String> variants = new ArrayList<>();
        for (List<Object> path : paths.subList(0, Math.min(paths.size(), MAX_FIELDS))) {
            String name = path.stream().filter(p -> p instanceof String).map(Object::toString)
                    .reduce((first, second) -> second).orElse("");
            for (JsonElement replacement : replacementsFor(get(root, path), name)) {
                JsonElement copy = root.deepCopy();
                if (path.isEmpty()) {
                    copy = replacement;
                } else {
                    set(copy, path, replacement);
                }
                variants.add(copy.toString());
            }
        }
        return variants;
    }

    private static void collectFields(JsonElement element, List<Object> path, List<List<Object>> paths, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        if (element.isJsonObject()) {
            for (String key : element.getAsJsonObject().keySet()) {
                List<Object> fieldPath = new ArrayList<>(path);
                fieldPath.add(key);
                paths.add(fieldPath);
                collectFields(element.getAsJsonObject().get(key), fieldPath, paths, depth + 1);
            }
        } else if (element.isJsonArray() && element.getAsJsonArray().size() > 0) {
            List<Object> elementPath = new ArrayList<>(path);
            elementPath.add(0);
            JsonElement first = element.getAsJsonArray().get(0);
            if (first.isJsonObject() || first.isJsonArray()) {
                collectFields(first, elementPath, paths, depth + 1);
            } else {
                paths.add(elementPath);
            }
        }
    }

    private static JsonElement get(JsonElement root, List<Object> path) {
        JsonElement element = root;
        for (Object step : path) {
            element = step instanceof Integer ? element.getAsJsonArray().get((Integer) step) :
                    element.getAsJsonObject().get((String) step);
        }
        return element;
    }

    private static void set(JsonElement root, List<Object> path, JsonElement value) {
        JsonElement parent = get(root, path.subList(0, path.size() - 1));
        Object last = path.get(path.size() - 1);
        if (last instanceof Integer) {
            parent.getAsJsonArray().set((Integer) last, value);
        } else {
            parent.getAsJsonObject().add((String) last, value);
        }
    }

    /**
     * The invalid values for a JSON value, by kind: always null; for strings, empty and very long strings, a number
     * and an object; for numbers, a string, a boolean, a number too large for any numeric type (not for amounts of
     * resources), a negative and a decimal number, and an array; for booleans, a string, a number and an object; for
     * objects, a string, an array and a number; for arrays, an object, a string and an array with null.
     */
    static List<JsonElement> replacementsFor(JsonElement original, String name) {
        List<JsonElement> values = new ArrayList<>();
        values.add(JsonNull.INSTANCE);
        if (original == null || original.isJsonNull()) {
            values.add(new JsonPrimitive("x"));
            values.add(new JsonPrimitive(1));
        } else if (original.isJsonPrimitive() && original.getAsJsonPrimitive().isString()) {
            values.add(new JsonPrimitive(""));
            values.add(new JsonPrimitive("a".repeat(LONG_STRING_LENGTH)));
            values.add(new JsonPrimitive(123456789));
            values.add(new JsonObject());
        } else if (original.isJsonPrimitive() && original.getAsJsonPrimitive().isNumber()) {
            values.add(new JsonPrimitive("abc"));
            values.add(new JsonPrimitive(true));
            if (!ResourceCountLimiter.isResourceCount(name)) {
                values.add(new JsonPrimitive(new BigInteger(HUGE_NUMBER)));
            }
            values.add(new JsonPrimitive(-1));
            values.add(new JsonPrimitive(0.5));
            values.add(new JsonArray());
        } else if (original.isJsonPrimitive()) {
            values.add(new JsonPrimitive("maybe"));
            values.add(new JsonPrimitive(2));
            values.add(new JsonObject());
        } else if (original.isJsonObject()) {
            values.add(new JsonPrimitive("x"));
            values.add(new JsonArray());
            values.add(new JsonPrimitive(1));
        } else {
            values.add(new JsonObject());
            values.add(new JsonPrimitive("x"));
            JsonArray withNull = new JsonArray();
            withNull.add(JsonNull.INSTANCE);
            values.add(withNull);
        }
        return values;
    }

    // ---------------------------------------- URL ----------------------------------------

    /**
     * The invalid values for a value in the URL: empty and very long strings; for numbers, also a string, a number too
     * large for any numeric type (not for amounts of resources), a decimal and a negative number; for other values, a
     * number.
     */
    static List<String> urlValues(String original, String name) {
        List<String> values = new ArrayList<>(Arrays.asList("", "a".repeat(LONG_URL_VALUE_LENGTH)));
        if (original != null && original.matches("-?\\d+(\\.\\d+)?")) {
            values.add("abc");
            if (!ResourceCountLimiter.isResourceCount(name)) {
                values.add(HUGE_NUMBER);
            }
            values.add("0.5");
            values.add("-1");
        } else {
            values.add("123456789");
        }
        return values;
    }

    /**
     * The URL with each query parameter (up to MAX_FIELDS) set to each invalid value.
     */
    static List<HttpUrl> queryVariants(HttpUrl url) {
        List<HttpUrl> variants = new ArrayList<>();
        List<String> names = new ArrayList<>(url.queryParameterNames());
        for (String name : names.subList(0, Math.min(names.size(), MAX_FIELDS))) {
            for (String value : urlValues(url.queryParameter(name), name)) {
                variants.add(url.newBuilder().setQueryParameter(name, value).build());
            }
        }
        return variants;
    }

    /**
     * The URL with each path parameter of the endpoint set to each invalid value (except the empty one, which would
     * change the endpoint). Path parameters are the segments of the endpoint template written as {name}.
     */
    static List<HttpUrl> pathVariants(HttpUrl url, String endpoint) {
        List<HttpUrl> variants = new ArrayList<>();
        List<String> template = Arrays.stream(endpoint.split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        List<String> segments = url.pathSegments().stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (template.size() != segments.size()) {
            return variants;
        }
        for (int i = 0; i < template.size(); i++) {
            if (template.get(i).matches("\\{.+}")) {
                String name = template.get(i).substring(1, template.get(i).length() - 1);
                for (String value : urlValues(segments.get(i), name)) {
                    if (!value.isEmpty()) {
                        variants.add(url.newBuilder().setPathSegment(indexOf(url, i), value).build());
                    }
                }
            }
        }
        return variants;
    }

    // Index in url.pathSegments() (which may contain empty segments) of the i-th non-empty segment
    private static int indexOf(HttpUrl url, int nonEmptyIndex) {
        int count = -1;
        for (int i = 0; i < url.pathSize(); i++) {
            if (!url.pathSegments().get(i).isEmpty() && ++count == nonEmptyIndex) {
                return i;
            }
        }
        return nonEmptyIndex;
    }
}
