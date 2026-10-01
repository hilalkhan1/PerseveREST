package io.resttestgen.implementation.interactionprocessor;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestTokenInteractionProcessor {

    // Login response of the Flight Search API (token shortened)
    private static final String LOGIN_RESPONSE = "{\"time\":\"2026-09-27T21:36:15.853554727\",\"httpStatus\":\"OK\"," +
            "\"isSuccess\":true,\"response\":{\"accessToken\":\"eyJ0eXAiOiJCZWFyZXIiLCJhbGciOiJSUzI1NiJ9.eyJqdGkiOiI3ZGNm\"," +
            "\"accessTokenExpiresAt\":1790548575,\"refreshToken\":\"eyJ0eXAiOiJCZWFyZXIiLCJhbGciOiJSUzI1NiJ9.refresh\"}}";

    @Test
    public void testFindsNestedAccessToken() {
        Assertions.assertEquals("eyJ0eXAiOiJCZWFyZXIiLCJhbGciOiJSUzI1NiJ9.eyJqdGkiOiI3ZGNm",
                TokenInteractionProcessor.findToken(JsonParser.parseString(LOGIN_RESPONSE)));
    }

    @Test
    public void testIgnoresRefreshTokensAndShortValues() {
        Assertions.assertNull(TokenInteractionProcessor.findToken(JsonParser.parseString(
                "{\"refreshToken\": \"eyJ0eXAiOiJCZWFyZXIiLCJhbGciOiJSUzI1NiJ9\", \"token\": \"abc\"}")));
        Assertions.assertNull(TokenInteractionProcessor.findToken(JsonParser.parseString("[{\"id\": 1}]")));
    }

    @Test
    public void testDeclaresTokenAuthentication() throws IOException {
        Path bearer = Files.createTempFile("bearer", ".json");
        Files.writeString(bearer, "{\"openapi\": \"3.0.1\", \"components\": {\"securitySchemes\": {\"bearerAuth\": " +
                "{\"type\": \"http\", \"scheme\": \"bearer\", \"bearerFormat\": \"JWT\"}}}}");
        Path basic = Files.createTempFile("basic", ".json");
        Files.writeString(basic, "{\"openapi\": \"3.0.1\", \"components\": {\"securitySchemes\": {\"basicAuth\": " +
                "{\"type\": \"http\", \"scheme\": \"basic\"}}}}");
        Path none = Files.createTempFile("none", ".json");
        Files.writeString(none, "{\"openapi\": \"3.0.1\", \"paths\": {}}");

        Assertions.assertTrue(TokenInteractionProcessor.declaresTokenAuthentication(bearer.toString()));
        Assertions.assertFalse(TokenInteractionProcessor.declaresTokenAuthentication(basic.toString()));
        Assertions.assertFalse(TokenInteractionProcessor.declaresTokenAuthentication(none.toString()));
    }
}
