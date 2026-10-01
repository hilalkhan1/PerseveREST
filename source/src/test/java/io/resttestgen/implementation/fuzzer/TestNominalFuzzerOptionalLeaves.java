package io.resttestgen.implementation.fuzzer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.resttestgen.boot.ApiUnderTest;
import io.resttestgen.boot.Starter;
import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.HttpMethod;
import io.resttestgen.core.helper.RequestManager;
import io.resttestgen.core.openapi.Operation;
import io.resttestgen.core.testing.TestInteraction;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Non-required leaves in nominal requests, on the specification of the ERC-20 API of RESTgym, where no property of the
 * request bodies is declared as required: POST /{contractAddress}/transfer needs {"to": ..., "value": ...}.
 */
public class TestNominalFuzzerOptionalLeaves {

    private static Environment environment;

    @BeforeAll
    public static void setUp() throws Exception {
        environment = Starter.initEnvironment(ApiUnderTest.loadTestApiFromFile("restgym-erc20"));
    }

    @Test
    public void testProbabilities() {
        Assertions.assertEquals(10, NominalFuzzer.probabilityToKeepNonRequiredLeaves(0));
        Assertions.assertEquals(10, NominalFuzzer.probabilityToKeepNonRequiredLeaves(39));
        Assertions.assertEquals(50, NominalFuzzer.probabilityToKeepNonRequiredLeaves(40));
        Assertions.assertEquals(50, NominalFuzzer.probabilityToKeepNonRequiredLeaves(69));
        Assertions.assertEquals(90, NominalFuzzer.probabilityToKeepNonRequiredLeaves(70));
        Assertions.assertEquals(90, NominalFuzzer.probabilityToKeepNonRequiredLeaves(99));
    }

    @Test
    public void testBodiesAreOftenCompleteAndHeadersRare() throws IOException {
        Operation transfer = environment.getOpenAPI().getOperations().stream()
                .filter(o -> o.getMethod() == HttpMethod.POST && o.getEndpoint().equals("/{contractAddress}/transfer"))
                .findFirst().orElseThrow();
        int requests = 400, complete = 0, empty = 0, withHeader = 0;
        for (int i = 0; i < requests; i++) {
            TestInteraction interaction = new NominalFuzzer(transfer).generateTestSequences(1).get(0).getFirst();
            Request request = new RequestManager(interaction.getFuzzedOperation()).buildRequest();
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            JsonObject body = JsonParser.parseString(buffer.readUtf8()).getAsJsonObject();
            if (body.has("to") && body.has("value")) {
                complete++;
            }
            if (body.size() == 0) {
                empty++;
            }
            if (request.header("privateFor") != null) {
                withHeader++;
            }
        }
        // Expected: complete in 0.4*0.01 + 0.3*0.25 + 0.3*0.81 = 32% of the requests (RestTestGen: 1%), empty in
        // 0.4*0.81 + 0.3*0.25 + 0.3*0.01 = 40%, optional header in 10% of the requests
        Assertions.assertTrue(complete > requests * 0.2, "complete bodies: " + complete);
        Assertions.assertTrue(empty > requests * 0.25, "empty bodies: " + empty);
        Assertions.assertTrue(withHeader < requests * 0.2, "requests with the optional header: " + withHeader);
    }
}
