package io.resttestgen.implementation.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.resttestgen.boot.ApiUnderTest;
import io.resttestgen.boot.Starter;
import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.HttpMethod;
import io.resttestgen.core.datatype.parameter.ParameterUtils;
import io.resttestgen.core.helper.RequestManager;
import io.resttestgen.core.openapi.Operation;
import io.resttestgen.core.testing.TestInteraction;
import io.resttestgen.implementation.fuzzer.NominalFuzzer;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Request bodies that are a single value, in the specification of Gestao Hospital (RESTgym): the patient to check out
 * is a JSON string, the quantity of a product to transfer is an integer.
 */
public class TestPrimitiveRequestBody {

    private static Environment environment;

    @BeforeAll
    public static void setUp() throws Exception {
        environment = Starter.initEnvironment(ApiUnderTest.loadTestApiFromFile("restgym-gestao-hospital"));
    }

    private static JsonElement nominalBody(String endpoint) throws IOException {
        Operation operation = environment.getOpenAPI().getOperations().stream()
                .filter(o -> o.getMethod() == HttpMethod.POST && o.getEndpoint().equals(endpoint))
                .findFirst().orElseThrow();
        Assertions.assertTrue(operation.getRequestBody() instanceof PrimitiveRequestBody, endpoint);
        TestInteraction interaction = new NominalFuzzer(operation).generateTestSequences(1).get(0).getFirst();
        Request request = new RequestManager(interaction.getFuzzedOperation()).buildRequest();
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        String body = buffer.readUtf8();
        Assertions.assertEquals("application/json; charset=utf-8", String.valueOf(request.body().contentType()));
        return JsonParser.parseString(body);
    }

    @Test
    public void testStringBody() throws IOException {
        for (int i = 0; i < 20; i++) {
            JsonElement body = nominalBody("/v1/hospitais/{hospital_id}/pacientes/checkout");
            Assertions.assertTrue(body.isJsonPrimitive() && body.getAsJsonPrimitive().isString(), body.toString());
        }
    }

    @Test
    public void testIntegerBody() throws IOException {
        for (int i = 0; i < 20; i++) {
            JsonElement body = nominalBody("/v1/hospitais/{id}/transferencia/{productId}");
            Assertions.assertTrue(body.isJsonPrimitive() && body.getAsJsonPrimitive().isNumber(), body.toString());
            Assertions.assertFalse(body.toString().contains("."), body.toString());
        }
    }

    @Test
    public void testValueNamedAfterDescription() {
        Operation checkout = environment.getOpenAPI().getOperations().stream()
                .filter(o -> o.getEndpoint().equals("/v1/hospitais/{hospital_id}/pacientes/checkout"))
                .findFirst().orElseThrow();
        Assertions.assertEquals("idPatient",
                ParameterUtils.getLeaves(checkout.getRequestBody()).iterator().next().getName().toString());
        // A copy is still a primitive body
        Assertions.assertTrue(checkout.deepClone().getRequestBody() instanceof PrimitiveRequestBody);
    }
}
