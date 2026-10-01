package io.resttestgen.implementation.helper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.resttestgen.boot.ApiUnderTest;
import io.resttestgen.boot.Starter;
import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.HttpMethod;
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
 * Nominal request bodies for the specification of Spring PetClinic used in REST League (RESTgym's pet-clinic), whose
 * schemas are combined with allOf both at the root of request bodies (Vet, Visit, Pet, PetType) and in properties
 * (the "type" of PetFields).
 */
public class TestCombinedSchemasPetClinic {

    private static Environment environment;

    @BeforeAll
    public static void setUp() throws Exception {
        environment = Starter.initEnvironment(ApiUnderTest.loadTestApiFromFile("restgym-pet-clinic"));
    }

    private static Operation operation(HttpMethod method, String endpoint) {
        return environment.getOpenAPI().getOperations().stream()
                .filter(o -> o.getMethod() == method && o.getEndpoint().equals(endpoint))
                .findFirst().orElseThrow();
    }

    private static JsonObject nominalBody(HttpMethod method, String endpoint) throws IOException {
        for (int attempt = 0; attempt < 20; attempt++) {
            TestInteraction interaction = new NominalFuzzer(operation(method, endpoint))
                    .generateTestSequences(1).get(0).getFirst();
            Request request = new RequestManager(interaction.getFuzzedOperation()).buildRequest();
            Assertions.assertNotNull(request.body(), method + " " + endpoint + " has no body");
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            String body = buffer.readUtf8();
            Assertions.assertFalse(body.isEmpty(), method + " " + endpoint + " has an empty body");
            Assertions.assertEquals("application/json; charset=utf-8", String.valueOf(request.body().contentType()));
            JsonElement json = JsonParser.parseString(body);
            if (json.isJsonObject()) {
                return json.getAsJsonObject();
            }
        }
        throw new AssertionError(method + " " + endpoint + " never had a JSON object body");
    }

    @Test
    public void testRootAllOfBodies() throws IOException {
        // Vet = allOf [VetFields, {id}]
        JsonObject vet = nominalBody(HttpMethod.POST, "/petclinic/api/vets");
        Assertions.assertTrue(vet.has("firstName") && vet.has("lastName"), vet.toString());
        // Visit = allOf [VisitFields, {id, petId}]
        JsonObject visit = nominalBody(HttpMethod.POST, "/petclinic/api/visits");
        Assertions.assertTrue(visit.has("description"), visit.toString());
        // PetType = allOf [PetTypeFields, {id}]
        JsonObject petType = nominalBody(HttpMethod.PUT, "/petclinic/api/pettypes/{petTypeId}");
        Assertions.assertTrue(petType.has("name"), petType.toString());
    }

    @Test
    public void testAllOfProperty() throws IOException {
        // PetFields = {name, birthDate, type: PetType}, with PetType = allOf [PetTypeFields, {id}]
        JsonObject pet = nominalBody(HttpMethod.POST, "/petclinic/api/owners/{ownerId}/pets");
        Assertions.assertTrue(pet.has("name") && pet.has("birthDate"), pet.toString());
        Assertions.assertTrue(pet.get("type").isJsonObject(), pet.toString());
        Assertions.assertTrue(pet.getAsJsonObject("type").has("name"), pet.toString());
    }

    @Test
    public void testAllRequestBodiesAreGenerated() throws IOException {
        for (Operation operation : environment.getOpenAPI().getOperations()) {
            if (operation.getMethod() == HttpMethod.POST || operation.getMethod() == HttpMethod.PUT) {
                Assertions.assertNotNull(operation.getRequestBody(), operation + " lost its request body");
                nominalBody(operation.getMethod(), operation.getEndpoint());
            }
        }
    }
}
