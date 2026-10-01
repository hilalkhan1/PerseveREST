package io.resttestgen.implementation.strategy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class TestPerseveRESTStrategy {

    // Real server error bodies of Spring PetClinic REST, recorded by RESTgym
    private static final String PARSE_ERROR_OWNER_6 = "{\"type\":\"http://localhost:8080/petclinic/api/owners/6/pets\"," +
            "\"title\":\"HttpMessageNotReadableException\",\"status\":500,\"detail\":\"JSON parse error: Unexpected " +
            "character ('}' (code 125)): was expecting double-quote to start field name\"," +
            "\"instance\":\"/petclinic/api/owners/6/pets\",\"timestamp\":\"2026-09-27T21:08:19.531025933Z\"}";
    private static final String PARSE_ERROR_OWNER_99 = "{\"type\":\"http://localhost:8080/petclinic/api/owners/99/pets\"," +
            "\"title\":\"HttpMessageNotReadableException\",\"status\":500,\"detail\":\"JSON parse error: Unexpected " +
            "character ('}' (code 125)): was expecting double-quote to start field name\"," +
            "\"instance\":\"/petclinic/api/owners/99/pets\",\"timestamp\":\"2026-09-27T21:08:19.560851264Z\"}";
    private static final String LOCKING_ERROR = "{\"type\":\"http://localhost:8080/petclinic/api/specialties\"," +
            "\"title\":\"ObjectOptimisticLockingFailureException\",\"status\":500,\"detail\":\"Row was updated or " +
            "deleted by another transaction (or unsaved-value mapping was incorrect): " +
            "[org.springframework.samples.petclinic.model.Specialty#27]\",\"instance\":\"/petclinic/api/specialties\"," +
            "\"timestamp\":\"2026-09-27T21:08:16.873453674Z\"}";

    @Test
    public void testSameFaultWithDifferentInputsAndTimestamps() {
        Assertions.assertEquals(PerseveRESTStrategy.faultWords(PARSE_ERROR_OWNER_6),
                PerseveRESTStrategy.faultWords(PARSE_ERROR_OWNER_99));
    }

    @Test
    public void testDifferentFaults() {
        Set<String> parseError = PerseveRESTStrategy.faultWords(PARSE_ERROR_OWNER_6);
        Set<String> lockingError = PerseveRESTStrategy.faultWords(LOCKING_ERROR);
        Assertions.assertTrue(parseError.contains("httpmessagenotreadableexception"));
        Assertions.assertTrue(lockingError.contains("objectoptimisticlockingfailureexception"));
        Assertions.assertFalse(parseError.contains("26") || parseError.contains("pets") || parseError.contains("petclinic"));
        Assertions.assertNotEquals(parseError, lockingError);
    }

    @Test
    public void testPlainTextAndEmptyBodies() {
        Assertions.assertEquals(Set.of("internal", "server", "error"),
                PerseveRESTStrategy.faultWords("Internal Server Error 42"));
        Assertions.assertEquals(Set.of("500"), PerseveRESTStrategy.faultWords(""));
        Assertions.assertEquals(Set.of("500"), PerseveRESTStrategy.faultWords(null));
    }
}
