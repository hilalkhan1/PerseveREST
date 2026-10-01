package io.resttestgen.implementation.helper;

import io.resttestgen.core.testing.TestInteraction;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sends a few malformed variants of a request that was already executed: without its body, with an empty body, with
 * truncated or invalid JSON, with other content types, and with or without a trailing slash in the path. They test
 * how the API handles requests that do not reach its business logic, which RestTestGen's mutators never produce, since
 * they only change the values of the parameters (the same standard robustness tests are done by fuzzers like CATS).
 * The variants are sent with their own HTTP client to the same address, so RESTgym's proxy records them.
 */
public class MalformedRequestSender {

    private static final Logger logger = LogManager.getLogger(MalformedRequestSender.class);

    // The same timeouts as PerseveREST's TestRunner
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON = MediaType.parse("application/json");

    // Headers that OkHttp sets by itself, or that describe the original body
    private static final Set<String> SKIPPED_HEADERS = Set.of("content-type", "content-length", "host", "connection",
            "accept-encoding", "user-agent", "transfer-encoding");

    /**
     * Sends the malformed variants of an executed interaction.
     * @return the bodies of the responses with a server error status.
     */
    public static List<String> sendVariants(TestInteraction interaction) {
        List<String> serverErrorBodies = new ArrayList<>();
        HttpUrl url = HttpUrl.parse(interaction.getRequestURL());
        if (url == null || interaction.getRequestMethod() == null) {
            return serverErrorBodies;
        }
        String method = interaction.getRequestMethod().toString().toUpperCase();
        Headers headers = headersOf(interaction.getRequestHeaders());
        String body = interaction.getRequestBody();
        boolean hasBody = body != null && !body.isEmpty() && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"));

        List<Request> variants = new ArrayList<>();
        if (hasBody) {
            variants.add(request(url, method, headers, RequestBody.create(new byte[0], null)));
            for (String malformedBody : malformedJsonBodies(body)) {
                variants.add(request(url, method, headers, RequestBody.create(malformedBody, JSON)));
            }
            variants.add(request(url, method, headers, RequestBody.create(body, MediaType.parse("text/plain"))));
            variants.add(request(url, method, headers, RequestBody.create(body, MediaType.parse("application/xml"))));
        }
        String path = url.encodedPath();
        HttpUrl otherPath = url.newBuilder().encodedPath(path.endsWith("/") && path.length() > 1 ?
                path.substring(0, path.length() - 1) : path + "/").build();
        variants.add(request(otherPath, method, headers, hasBody ? RequestBody.create(body, JSON) : null));

        for (Request request : variants) {
            try (Response response = client.newCall(request).execute()) {
                if (response.code() >= 500) {
                    ResponseBody responseBody = response.body();
                    serverErrorBodies.add(responseBody != null ? responseBody.string() : "");
                }
            } catch (IOException | RuntimeException e) {
                logger.debug("Malformed variant of {} not executed: {}", interaction.getFuzzedOperation(), e.getMessage());
            }
        }
        return serverErrorBodies;
    }

    /**
     * Invalid JSON bodies derived from a valid one: empty, truncated, with a trailing comma, and as an array.
     */
    static List<String> malformedJsonBodies(String body) {
        List<String> bodies = new ArrayList<>();
        bodies.add("");
        bodies.add(body.substring(0, body.length() / 2));
        String trimmed = body.trim();
        if (trimmed.endsWith("}") && trimmed.length() > 2) {
            bodies.add(trimmed.substring(0, trimmed.length() - 1) + ",}");
        }
        bodies.add("[" + body + "]");
        return bodies;
    }

    private static Request request(HttpUrl url, String method, Headers headers, RequestBody body) {
        // Methods like POST require a body in OkHttp: an empty one stands for "no body"
        if (body == null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
            body = RequestBody.create(new byte[0], null);
        }
        return new Request.Builder().url(url).headers(headers).method(method, body).build();
    }

    /**
     * The headers of the original request (in the "Name: value" lines of the interaction), except those describing the
     * original body or set by the HTTP client.
     */
    static Headers headersOf(String requestHeaders) {
        Headers.Builder builder = new Headers.Builder();
        if (requestHeaders != null) {
            for (String line : requestHeaders.split("\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && !SKIPPED_HEADERS.contains(line.substring(0, colon).trim().toLowerCase())) {
                    builder.addUnsafeNonAscii(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
        }
        return builder.build();
    }
}
