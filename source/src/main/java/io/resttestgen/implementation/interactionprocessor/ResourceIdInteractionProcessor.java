package io.resttestgen.implementation.interactionprocessor;

import io.resttestgen.core.testing.InteractionProcessor;
import io.resttestgen.core.testing.TestInteraction;
import io.resttestgen.core.testing.TestStatus;
import io.resttestgen.implementation.helper.ResourceIdMemory;

/**
 * Remembers the identifiers of the resources in successful responses, to reuse them in the path parameters that refer
 * to them (see ResourceIdMemory).
 */
public class ResourceIdInteractionProcessor extends InteractionProcessor {

    @Override
    public boolean canProcess(TestInteraction testInteraction) {
        return testInteraction.getTestStatus() == TestStatus.EXECUTED &&
                testInteraction.getResponseStatusCode().isSuccessful() && testInteraction.getResponseBody() != null;
    }

    @Override
    public void process(TestInteraction testInteraction) {
        ResourceIdMemory.record(testInteraction.getFuzzedOperation().getEndpoint(), testInteraction.getResponseBody());
    }
}
