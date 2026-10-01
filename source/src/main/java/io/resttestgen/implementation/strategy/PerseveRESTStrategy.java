package io.resttestgen.implementation.strategy;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.helper.ExtendedRandom;
import io.resttestgen.core.openapi.Operation;
import io.resttestgen.core.testing.Strategy;
import io.resttestgen.core.testing.TestInteraction;
import io.resttestgen.core.testing.TestRunner;
import io.resttestgen.core.testing.TestSequence;
import io.resttestgen.core.testing.TestStatus;
import io.resttestgen.implementation.fuzzer.IntensificationFuzzer;
import io.resttestgen.implementation.fuzzer.NominalFuzzer;
import io.resttestgen.implementation.helper.MalformedRequestSender;
import io.resttestgen.implementation.operationssorter.GraphBasedOperationsSorter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Pattern;

/**
 * PerseveREST's testing strategy (REST League 2027). It repeats RestTestGen's nominal and mutation-based
 * testing in rounds until the container is stopped, instead of exiting after a single pass, so that the
 * values learned from earlier responses are kept for the whole time budget.
 * Each round tests first the operations that never returned a successful response, in the dependency order
 * of the operation dependency graph, and then all operations in random order. Mutated variants are sent for
 * the first successful request of each operation, and for every server error whose message is new, because
 * server errors with different messages count as different faults; once per operation, malformed variants of a request
 * are also sent (see MalformedRequestSender). After every successful registration of a user,
 * it logs in with the same credentials, so that the access token can be used (see TokenInteractionProcessor);
 * registrations usually ask for a privileged role, when one is offered. No exception can stop the testing loop.
 */
@SuppressWarnings("unused")
public class PerseveRESTStrategy extends Strategy {

    private static final Logger logger = LogManager.getLogger(PerseveRESTStrategy.class);

    // Nominal requests per operation in a round: more for operations without a successful response yet
    private static final int NOMINAL_SEQUENCES_NOT_SUCCESSFUL = 20;
    private static final int NOMINAL_SEQUENCES_SUCCESSFUL = 5;

    // How many requests of the same operation are explored with mutated variants in a round
    private static final int MAX_EXPLORATIONS_PER_OPERATION = 5;

    // Two server errors are the same fault if the word sets of their messages are at least this similar
    // (Jaccard similarity), which is RESTgym's default when counting unique faults
    private static final double SAME_FAULT_SIMILARITY = 0.7;

    // Fields of JSON error bodies that change at every response and do not describe the fault (names compared
    // in lower case and without separators, so "trace_id" matches "traceid")
    private static final Set<String> VOLATILE_FIELDS = Set.of("timestamp", "time", "date", "datetime", "path",
            "instance", "type", "url", "uri", "href", "trace", "traceid", "requestid", "correlationid", "id");

    // Operations that log in: their path or id mentions logging in, and they receive a password
    private static final Pattern LOGIN_WORDS = Pattern.compile("log-?in|sign-?in|authenticat|token", Pattern.CASE_INSENSITIVE);

    // Roles that usually give access to the administration operations, and how often they are preferred
    private static final Pattern PRIVILEGED_ROLES = Pattern.compile("admin|root|super", Pattern.CASE_INSENSITIVE);
    private static final int PRIVILEGED_ROLE_PERCENTAGE = 75;

    private final Set<Operation> successfulOperations = new HashSet<>();
    private final List<Set<String>> knownFaults = new ArrayList<>();
    private final Map<Operation, Integer> explorationsInRound = new HashMap<>();
    private final List<Operation> loginOperations = new ArrayList<>();
    private final Set<Operation> malformedVariantsSent = new HashSet<>();
    private int round;

    @Override
    public void start() {
        // RestTestGen keeps every executed interaction (with its parsed response) for debugging: over one hour, this
        // alone fills the memory
        TestRunner.keepInteractionsForDebug = false;
        List<Operation> allOperations = new ArrayList<>(Environment.getInstance().getOpenAPI().getOperations());
        if (allOperations.isEmpty()) {
            logger.error("PerseveREST: the specification contains no operations to test.");
            return;
        }
        for (Operation operation : allOperations) {
            if (isLogin(operation)) {
                loginOperations.add(operation);
            }
        }

        for (round = 1; ; round++) {
            logger.info("PerseveREST: round {} started ({} successful operations, {} unique faults).",
                    round, successfulOperations.size(), knownFaults.size());
            explorationsInRound.clear();
            try {
                testNotYetSuccessfulOperations();
                Collections.shuffle(allOperations, Environment.getInstance().getRandom());
                for (Operation operation : allOperations) {
                    testOperation(operation, successfulOperations.contains(operation) ?
                            NOMINAL_SEQUENCES_SUCCESSFUL : NOMINAL_SEQUENCES_NOT_SUCCESSFUL);
                }
            } catch (Throwable t) {
                logger.warn("PerseveREST: round {} interrupted by an unexpected error.", round, t);
            }

            // Nothing to clear: the executed interactions are not kept for debugging (see start()), and the
            // dictionaries are bounded by themselves (see Dictionary).
        }
    }

    /**
     * Tests the operations that never returned a successful response, in RestTestGen's dependency order:
     * operations producing values needed by others come first. The sorter only returns operations that
     * were tested less than 10 times, so after the first rounds this is usually empty.
     */
    private void testNotYetSuccessfulOperations() {
        GraphBasedOperationsSorter sorter = new GraphBasedOperationsSorter();
        while (!sorter.isEmpty()) {
            testOperation(sorter.getFirst(), NOMINAL_SEQUENCES_NOT_SUCCESSFUL);
            sorter.removeFirst();
        }
    }

    private void testOperation(Operation operation, int numberOfSequences) {
        try {
            boolean exploredSuccess = false;
            boolean registersUsers = hasPassword(operation) && !loginOperations.contains(operation);
            for (TestSequence testSequence : new NominalFuzzer(operation).generateTestSequences(numberOfSequences)) {
                if (registersUsers) {
                    preferPrivilegedRoles(testSequence);
                }
                TestRunner.getInstance().run(testSequence);
                sendMalformedVariantsOnce(operation, testSequence);
                if (isSuccessful(testSequence)) {
                    successfulOperations.add(operation);
                    if (registersUsers) {
                        logInAs(testSequence);
                    }
                    if (!exploredSuccess) {
                        exploredSuccess = true;
                        explore(operation, testSequence);
                    }
                } else if (isNewFault(testSequence)) {
                    explore(operation, testSequence);
                }
            }
        } catch (Throwable t) {
            logger.warn("PerseveREST: testing {} failed with an unexpected error.", operation, t);
        }
    }

    /**
     * Sends mutated variants of the request (about 60, from RestTestGen's intensification fuzzer: invalid,
     * missing and extra parameters, boundary values, other HTTP methods...). Variants that cause a new fault
     * are explored in turn, within the exploration budget of the operation for this round.
     */
    private void explore(Operation operation, TestSequence testSequence) {
        Deque<TestSequence> toExplore = new ArrayDeque<>();
        toExplore.add(testSequence);
        while (!toExplore.isEmpty() && explorationsInRound.getOrDefault(operation, 0) < MAX_EXPLORATIONS_PER_OPERATION) {
            explorationsInRound.merge(operation, 1, Integer::sum);
            try {
                for (TestSequence variant : new IntensificationFuzzer(toExplore.poll()).generateTestSequences(0)) {
                    if (isNewFault(variant)) {
                        toExplore.add(variant);
                    }
                }
            } catch (RuntimeException e) {
                // A failing mutator must not stop the testing of the operation
                logger.warn("PerseveREST: exploring {} failed with an unexpected error.", operation, e);
            }
        }
    }

    /**
     * Once per operation, sends malformed variants of one of its requests (see MalformedRequestSender): of its first
     * successful request, whose path parameters are valid, or of any executed request after the first round.
     */
    private void sendMalformedVariantsOnce(Operation operation, TestSequence testSequence) {
        TestInteraction interaction = testSequence.getLast();
        if (interaction.getTestStatus() != TestStatus.EXECUTED || malformedVariantsSent.contains(operation) ||
                !(isSuccessful(testSequence) || round > 1)) {
            return;
        }
        malformedVariantsSent.add(operation);
        for (String serverErrorBody : MalformedRequestSender.sendVariants(interaction)) {
            registerFault(serverErrorBody, operation + " (malformed request)");
        }
    }

    /**
     * In requests that register users, usually chooses a privileged role (e.g., "ADMIN") where a field offers one,
     * so that the access token obtained by logging in also allows the administration operations.
     */
    private void preferPrivilegedRoles(TestSequence registration) {
        ExtendedRandom random = Environment.getInstance().getRandom();
        for (LeafParameter leaf : registration.getFirst().getFuzzedOperation().getLeaves()) {
            if (!leaf.getEnumValues().isEmpty() && random.nextInt(100) < PRIVILEGED_ROLE_PERCENTAGE) {
                leaf.getEnumValues().stream().filter(value -> PRIVILEGED_ROLES.matcher(String.valueOf(value)).find())
                        .findFirst().ifPresent(leaf::setValueManually);
            }
        }
    }

    /**
     * After a successful registration, logs in with the same credentials (the fields with the same names, e.g.,
     * email and password): the access token in the response is then used for the following requests (see
     * TokenInteractionProcessor). Otherwise, logging in succeeds only when random values happen to match.
     */
    private void logInAs(TestSequence registration) {
        Collection<LeafParameter> credentials = registration.getFirst().getFuzzedOperation().getLeaves();
        for (Operation login : loginOperations) {
            TestSequence attempt = new NominalFuzzer(login).generateTestSequences(1).get(0);
            for (LeafParameter leaf : attempt.getFirst().getFuzzedOperation().getLeaves()) {
                credentials.stream()
                        .filter(credential -> credential.getName().equals(leaf.getName()) && credential.getConcreteValue() != null)
                        .findFirst().ifPresent(credential -> leaf.setValueManually(credential.getConcreteValue()));
            }
            TestRunner.getInstance().run(attempt);
        }
    }

    private static boolean hasPassword(Operation operation) {
        return operation.getLeaves().stream().anyMatch(leaf -> leaf.getName().toString().toLowerCase().contains("password"));
    }

    private static boolean isLogin(Operation operation) {
        String description = operation.getEndpoint() + " " + operation.getOperationId();
        return LOGIN_WORDS.matcher(description).find() && !description.toLowerCase().contains("refresh") && hasPassword(operation);
    }

    private boolean isSuccessful(TestSequence testSequence) {
        TestInteraction interaction = testSequence.getLast();
        return interaction.getTestStatus() == TestStatus.EXECUTED && interaction.getResponseStatusCode().isSuccessful();
    }

    /**
     * Whether the request caused a server error whose message is not similar to any fault seen before.
     */
    private boolean isNewFault(TestSequence testSequence) {
        TestInteraction interaction = testSequence.getLast();
        if (interaction.getTestStatus() != TestStatus.EXECUTED || !interaction.getResponseStatusCode().isServerError()) {
            return false;
        }
        return registerFault(interaction.getResponseBody(), interaction.getFuzzedOperation().toString());
    }

    /**
     * Whether a server error message is not similar to any fault seen before; new faults are remembered.
     */
    private boolean registerFault(String responseBody, String source) {
        Set<String> words = faultWords(responseBody);
        for (Set<String> knownFault : knownFaults) {
            if (similarity(words, knownFault) >= SAME_FAULT_SIMILARITY) {
                return false;
            }
        }
        knownFaults.add(words);
        logger.info("PerseveREST: new fault found on {} ({} so far).", source, knownFaults.size());
        return true;
    }

    /**
     * The words identifying the fault in a server error response. Error bodies often contain parts that change at
     * every response (e.g., {"title": ..., "detail": ..., "instance": "/api/owners/6", "timestamp": ...}), so for
     * JSON bodies only the text of the other fields is used; then quoted values, numbers and identifiers, which
     * usually repeat the inputs, are dropped. The same fault triggered with different inputs gives the same words.
     */
    static Set<String> faultWords(String body) {
        String text = body == null ? "" : body.trim();
        try {
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonObject() || json.isJsonArray()) {
                StringBuilder fieldsText = new StringBuilder();
                collectFieldsText(json, fieldsText);
                text = fieldsText.toString();
            }
        } catch (RuntimeException ignored) {
            // Not JSON: use the whole body
        }
        text = text.replaceAll("<[^>]*>", " ")             // HTML tags
                .replaceAll("'[^']*'|\"[^\"]*\"", " ")      // quoted values
                .toLowerCase();
        Set<String> words = new HashSet<>();
        for (String word : text.split("[^a-z]+")) {         // no numbers; ids and UUIDs break into single letters
            if (word.length() > 1) {
                words.add(word);
            }
        }
        if (words.isEmpty()) {
            words.add("500");
        }
        return words;
    }

    private static void collectFieldsText(JsonElement element, StringBuilder text) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                if (!VOLATILE_FIELDS.contains(field.getKey().toLowerCase().replaceAll("[^a-z]", ""))) {
                    collectFieldsText(field.getValue(), text);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectFieldsText(item, text);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            text.append(element.getAsString()).append(' ');
        }
    }

    private static double similarity(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / (a.size() + b.size() - intersection.size());
    }
}
