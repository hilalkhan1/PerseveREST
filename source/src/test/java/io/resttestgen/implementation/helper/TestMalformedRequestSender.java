package io.resttestgen.implementation.helper;

import okhttp3.Headers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestMalformedRequestSender {

    @Test
    public void testMalformedJsonBodies() {
        List<String> bodies = MalformedRequestSender.malformedJsonBodies("{\"name\": \"Leo\"}");
        Assertions.assertEquals(List.of("", "{\"name\"", "{\"name\": \"Leo\",}", "[{\"name\": \"Leo\"}]"), bodies);
    }

    @Test
    public void testHeadersKeepAuthenticationAndDropBodyHeaders() {
        Headers headers = MalformedRequestSender.headersOf("Accept: application/json\nContent-Type: application/json\n" +
                "Content-Length: 15\nAuthorization: Bearer abc.def.ghi\nHost: localhost:8080\n");
        Assertions.assertEquals("Bearer abc.def.ghi", headers.get("Authorization"));
        Assertions.assertEquals("application/json", headers.get("Accept"));
        Assertions.assertNull(headers.get("Content-Type"));
        Assertions.assertNull(headers.get("Content-Length"));
        Assertions.assertNull(headers.get("Host"));
    }
}
