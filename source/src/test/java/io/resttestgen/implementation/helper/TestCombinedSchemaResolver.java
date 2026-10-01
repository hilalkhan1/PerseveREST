package io.resttestgen.implementation.helper;

import com.google.gson.Gson;
import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.ParameterFactory;
import io.resttestgen.core.datatype.parameter.combined.CombinedSchemaParameter;
import io.resttestgen.core.datatype.parameter.structured.ObjectParameter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class TestCombinedSchemaResolver {

    // Schemas are parsed from JSON, as RestTestGen does with specifications
    @SuppressWarnings("unchecked")
    private static CombinedSchemaParameter combinedSchema(String json, String name) {
        return (CombinedSchemaParameter) ParameterFactory.getParameter(new Gson().fromJson(json, Map.class), name);
    }

    private static Set<String> propertyNames(Parameter parameter) {
        return ((ObjectParameter) parameter).getProperties().stream().map(p -> p.getName().toString())
                .collect(Collectors.toSet());
    }

    @Test
    public void testAllOfMergesTheProperties() {
        // Like PetClinic's PetType: allOf [PetTypeFields {name}, {id}]
        CombinedSchemaParameter petType = combinedSchema("{\"in\": \"request_body\", \"allOf\": [" +
                "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}}," +
                "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}}]}", "type");
        Parameter resolved = CombinedSchemaResolver.resolve(petType, new Random(1));

        Assertions.assertTrue(resolved instanceof ObjectParameter);
        Assertions.assertEquals("type", resolved.getName().toString());
        Assertions.assertEquals(Set.of("name", "id"), propertyNames(resolved));
    }

    @Test
    public void testOneOfChoosesOneSchema() {
        CombinedSchemaParameter pet = combinedSchema("{\"in\": \"request_body\", \"oneOf\": [" +
                "{\"type\": \"object\", \"properties\": {\"cat\": {\"type\": \"string\"}}}," +
                "{\"type\": \"object\", \"properties\": {\"dog\": {\"type\": \"string\"}}}]}", "pet");
        Parameter resolved = CombinedSchemaResolver.resolve(pet, new Random(1));

        Assertions.assertTrue(resolved instanceof ObjectParameter);
        Assertions.assertEquals("pet", resolved.getName().toString());
        Assertions.assertTrue(Set.of(Set.of("cat"), Set.of("dog")).contains(propertyNames(resolved)));
    }
}
