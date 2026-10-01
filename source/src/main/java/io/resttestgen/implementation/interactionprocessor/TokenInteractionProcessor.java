package io.resttestgen.implementation.interactionprocessor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.resttestgen.boot.AuthenticationInfo;
import io.resttestgen.core.Environment;
import io.resttestgen.core.testing.InteractionProcessor;
import io.resttestgen.core.testing.TestInteraction;
import io.resttestgen.core.testing.TestRunner;
import io.resttestgen.core.testing.TestStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;

/**
 * Authenticates the requests that follow a successful login: when a successful response contains an access token
 * (e.g., {"response": {"accessToken": "eyJ..."}} from a login operation), the token is sent as
 * "Authorization: Bearer ..." in all the following requests, until another token replaces it. Without this, all
 * the operations that require authentication are rejected with 401/403 for the whole testing session.
 * A token rejected with 401 (e.g., after a logout) is dropped, since sending an invalid token makes many APIs reject
 * even the login requests. Note that RESTgym may add its own token to the requests through its proxy (as for Flight
 * Search, whose auth.py logs in as a fixed user and sets the Authorization header of all but the authentication
 * endpoints): in that case this token only matters for the authentication endpoints.
 * Enabled only if the specification declares bearer, OAuth2 or OpenID Connect authentication, so that fields that
 * are not access tokens are not misused, and only if no authentication is configured for the API.
 */
public class TokenInteractionProcessor extends InteractionProcessor {

    private static final Logger logger = LogManager.getLogger(TokenInteractionProcessor.class);

    // Names of fields holding access tokens (compared in lower case and without separators); refresh tokens and
    // token types are deliberately excluded
    private static final Set<String> TOKEN_FIELDS = Set.of("accesstoken", "token", "jwt", "idtoken", "authtoken",
            "authenticationtoken", "bearertoken", "sessiontoken");

    private static final int MIN_TOKEN_LENGTH = 16;

    private final boolean enabled;
    private String currentToken;

    public TokenInteractionProcessor() {
        enabled = Environment.getInstance().getApiUnderTest().getDefaultAuthenticationInfo() == null
                && declaresTokenAuthentication(Environment.getInstance().getApiUnderTest().getComputedJsonSpecificationPath());
    }

    @Override
    public boolean canProcess(TestInteraction testInteraction) {
        return enabled && testInteraction.getTestStatus() == TestStatus.EXECUTED;
    }

    @Override
    public void process(TestInteraction testInteraction) {
        // An invalid token (e.g., after a logout) makes many APIs reject every request with 401, even logging in
        // again: stop sending it, until the next login.
        if (testInteraction.getResponseStatusCode().getCode() == 401) {
            if (currentToken != null) {
                currentToken = null;
                TestRunner.getInstance().setAuthenticationInfo(null);
                logger.info("The access token was rejected by {}: not sending it until the next login.", testInteraction.getFuzzedOperation());
            }
            return;
        }
        if (!testInteraction.getResponseStatusCode().isSuccessful()) {
            return;
        }

        String token;
        try {
            token = findToken(JsonParser.parseString(testInteraction.getResponseBody()));
        } catch (RuntimeException e) {
            return; // Not JSON
        }
        if (token != null && !token.equals(currentToken)) {
            currentToken = token;
            TestRunner.getInstance().setAuthenticationInfo(
                    AuthenticationInfo.fixedHeader("token from " + testInteraction.getFuzzedOperation(),
                            "Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token));
            logger.info("Using the access token returned by {} in the following requests.", testInteraction.getFuzzedOperation());
        }
    }

    /**
     * Searches the response for an access token, also in nested objects.
     */
    static String findToken(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
            JsonElement value = field.getValue();
            if (TOKEN_FIELDS.contains(field.getKey().toLowerCase().replaceAll("[^a-z]", "")) &&
                    value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String token = value.getAsString().trim();
                String bareToken = token.startsWith("Bearer ") ? token.substring("Bearer ".length()) : token;
                if (bareToken.length() >= MIN_TOKEN_LENGTH && !bareToken.contains(" ")) {
                    return token;
                }
            }
            String nestedToken = findToken(value);
            if (nestedToken != null) {
                return nestedToken;
            }
        }
        return null;
    }

    /**
     * Whether the OpenAPI specification declares a security scheme based on access tokens.
     */
    static boolean declaresTokenAuthentication(String specificationPath) {
        try (Reader reader = new FileReader(specificationPath)) {
            JsonObject specification = JsonParser.parseReader(reader).getAsJsonObject();
            if (!specification.has("components") || !specification.getAsJsonObject("components").has("securitySchemes")) {
                return false;
            }
            for (Map.Entry<String, JsonElement> scheme :
                    specification.getAsJsonObject("components").getAsJsonObject("securitySchemes").entrySet()) {
                JsonObject definition = scheme.getValue().getAsJsonObject();
                String type = definition.has("type") ? definition.get("type").getAsString() : "";
                String httpScheme = definition.has("scheme") ? definition.get("scheme").getAsString() : "";
                String name = definition.has("name") ? definition.get("name").getAsString() : "";
                if ((type.equalsIgnoreCase("http") && httpScheme.equalsIgnoreCase("bearer")) ||
                        type.equalsIgnoreCase("oauth2") || type.equalsIgnoreCase("openIdConnect") ||
                        (type.equalsIgnoreCase("apiKey") && name.equalsIgnoreCase("Authorization"))) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read the security schemes of the specification.");
        }
        return false;
    }
}
