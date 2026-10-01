package io.resttestgen.core.dictionary;

import io.resttestgen.boot.ApiUnderTest;
import io.resttestgen.boot.Starter;
import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.HttpMethod;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.openapi.Operation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The dictionary modified by PerseveREST: constant-time de-duplication, bounded size, and entries that do not keep the
 * operations of their source leaves.
 */
public class TestBoundedDictionary {

    private static Environment environment;

    @BeforeAll
    public static void setUp() throws Exception {
        environment = Starter.initEnvironment(ApiUnderTest.loadTestApiFromFile("restgym-erc20"));
    }

    // The "to" leaf (a string) of the body of an editable copy of POST /{contractAddress}/transfer, with a value
    private static LeafParameter leafWithValue(String value) {
        Operation operation = environment.getOpenAPI().getOperations().stream()
                .filter(o -> o.getMethod() == HttpMethod.POST && o.getEndpoint().equals("/{contractAddress}/transfer"))
                .findFirst().orElseThrow().deepClone();
        LeafParameter to = operation.getLeaves().stream().filter(l -> l.getName().toString().equals("to"))
                .findFirst().orElseThrow();
        to.setValueManually(value);
        return to;
    }

    @Test
    public void testDuplicatesAreMerged() {
        Dictionary dictionary = new Dictionary();
        LeafParameter leaf = leafWithValue("0x90F8bf6A479f320ead074411a4B0e7944Ea8c9C1");
        dictionary.addEntry(new DictionaryEntry(leaf));
        dictionary.addEntry(new DictionaryEntry(leafWithValue("0x90F8bf6A479f320ead074411a4B0e7944Ea8c9C1")));
        dictionary.addEntry(new DictionaryEntry(leafWithValue("other")));
        Assertions.assertEquals(2, dictionary.size());
        List<DictionaryEntry> entries = dictionary.getEntriesByParameterName(leaf.getName(), leaf.getType());
        Assertions.assertEquals(2, entries.size());
        Assertions.assertEquals(2, dictionary.getEntriesByNormalizedParameterName(leaf.getNormalizedName(), leaf.getType()).size());
        Assertions.assertEquals(1, dictionary.getEntriesByValueLength(5).size());
    }

    @Test
    public void testSizeIsBounded() {
        Dictionary dictionary = new Dictionary();
        LeafParameter leaf = null;
        for (int i = 0; i < Dictionary.MAX_ENTRIES_PER_NAME + 50; i++) {
            leaf = leafWithValue("value" + i);
            dictionary.addEntry(new DictionaryEntry(leaf));
        }
        List<DictionaryEntry> entries = dictionary.getEntriesByNormalizedParameterName(leaf.getNormalizedName(), leaf.getType());
        Assertions.assertEquals(Dictionary.MAX_ENTRIES_PER_NAME, entries.size());
        Assertions.assertEquals(Dictionary.MAX_ENTRIES_PER_NAME, dictionary.size());
        // The most recent values are kept
        Assertions.assertEquals("value" + (Dictionary.MAX_ENTRIES_PER_NAME + 49), entries.get(entries.size() - 1).getValue());
        Assertions.assertEquals("value50", entries.get(0).getValue());
    }

    @Test
    public void testEntriesAreDetached() {
        LeafParameter source = leafWithValue("abc");
        LeafParameter linked = leafWithValue("placeholder");
        // A value taken from a dictionary is a link to the source leaf, as in RestTestGen's value providers
        linked.setValueManually(source);
        DictionaryEntry entry = new DictionaryEntry(linked);
        Assertions.assertEquals("abc", entry.getValue());
        // Stored entries do not keep the operation of their leaf, nor the leaves their value came from
        new Dictionary().addEntry(entry);
        Assertions.assertNull(entry.getSource().getOperation());
        Assertions.assertNull(entry.getSource().getParent());
        Assertions.assertEquals("abc", entry.getSource().getValue());
    }

    @Test
    public void testRefillWithoutProvider() {
        // A value set manually (e.g., a reused identifier) has no provider; RestTestGen's RefillValueOperationMutator
        // refills values with their own provider, which failed with a NullPointerException
        LeafParameter leaf = leafWithValue("manual");
        Assertions.assertNull(leaf.getValueSource());
        leaf.setValueWithProvider(leaf.getValueSource());
        Assertions.assertNotNull(leaf.getValue());
        Assertions.assertNotNull(leaf.getValueSource());
    }

    @Test
    public void testDuplicatesKeepTheirDetachedSource() {
        Dictionary dictionary = new Dictionary();
        DictionaryEntry first = new DictionaryEntry(leafWithValue("same"));
        dictionary.addEntry(first);
        LeafParameter detached = first.getSource();
        dictionary.addEntry(new DictionaryEntry(leafWithValue("same")));
        Assertions.assertSame(detached, first.getSource());
        Assertions.assertNull(first.getSource().getOperation());
    }
}
