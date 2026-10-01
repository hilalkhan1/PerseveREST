package io.resttestgen.core.dictionary;

// Modified for PerseveREST (REST League 2027): entries are indexed and bounded. See NOTICE.

import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.NormalizedParameterName;
import io.resttestgen.core.datatype.ParameterName;
import io.resttestgen.core.datatype.parameter.attributes.ParameterType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dictionary that stores parameter values to be reused. Values are loaded from a default dictionary (file) and taken
 * from output data observed during testing. Each value is associated with a source that tells where that particular
 * value was observed
 * <p>
 * Modified for PerseveREST: RestTestGen kept every distinct value in a list, and scanned the whole list to add each
 * value, so over a long session the dictionaries (especially the one of the random values in the requests) grew
 * without limit, and the time to process each response grew with them: after 10 minutes on some APIs of REST League,
 * RestTestGen's memory was full and the tool stopped sending requests. Entries are now indexed (constant time to add
 * and to look up), and only the most recent values are kept: at most MAX_ENTRIES_PER_NAME for each parameter name and
 * type, and MAX_ENTRIES in total.
 */
public class Dictionary {

    private static final Logger logger = LogManager.getLogger(Dictionary.class);

    public static final int MAX_ENTRIES_PER_NAME = 100;
    public static final int MAX_ENTRIES = 20000;

    // All entries, from the least to the most recently discovered, by name, normalized name, type and value
    private final LinkedHashMap<List<Object>, DictionaryEntry> dictionary = new LinkedHashMap<>();
    private final Map<List<Object>, LinkedHashSet<DictionaryEntry>> byParameterName = new HashMap<>();
    private final Map<List<Object>, LinkedHashSet<DictionaryEntry>> byNormalizedParameterName = new HashMap<>();

    private static List<Object> keyOf(DictionaryEntry entry) {
        return List.of(entry.getParameterName(), entry.getNormalizedParameterName(), entry.getParameterType(),
                entry.getValue());
    }

    /**
     * Add an entry to the dictionary. If a similar entry already exists, it just updates the discovery time.
     * @param dictionaryEntry the entry to add.
     */
    public void addEntry(DictionaryEntry dictionaryEntry) {
        List<Object> key = keyOf(dictionaryEntry);
        DictionaryEntry existing = dictionary.remove(key);

        // If there are no similar entries, add entry to the dictionary
        if (existing == null) {
            dictionaryEntry.detachSource();
            dictionary.put(key, dictionaryEntry);
            byParameterName.computeIfAbsent(List.of(dictionaryEntry.getParameterName(), dictionaryEntry.getParameterType()),
                    k -> new LinkedHashSet<>()).add(dictionaryEntry);
            LinkedHashSet<DictionaryEntry> sameName = byNormalizedParameterName.computeIfAbsent(
                    List.of(dictionaryEntry.getNormalizedParameterName(), dictionaryEntry.getParameterType()),
                    k -> new LinkedHashSet<>());
            sameName.add(dictionaryEntry);
            if (sameName.size() > MAX_ENTRIES_PER_NAME) {
                removeEntry(sameName.iterator().next());
            }
            if (dictionary.size() > MAX_ENTRIES) {
                removeEntry(dictionary.values().iterator().next());
            }
        }

        // Otherwise, update similar entry with new discovery time (it becomes the most recent entry). Its source keeps
        // the same value: RestTestGen replaced it with the new leaf, which would keep the new operation in memory
        else {
            existing.setDiscoveryTime(dictionaryEntry.getDiscoveryTime());
            dictionary.put(key, existing);
        }
    }

    private void removeEntry(DictionaryEntry entry) {
        dictionary.remove(keyOf(entry));
        removeFromIndex(byParameterName, List.of(entry.getParameterName(), entry.getParameterType()), entry);
        removeFromIndex(byNormalizedParameterName, List.of(entry.getNormalizedParameterName(), entry.getParameterType()), entry);
    }

    private static void removeFromIndex(Map<List<Object>, LinkedHashSet<DictionaryEntry>> index, List<Object> key,
                                        DictionaryEntry entry) {
        LinkedHashSet<DictionaryEntry> entries = index.get(key);
        if (entries != null) {
            entries.remove(entry);
            if (entries.isEmpty()) {
                index.remove(key);
            }
        }
    }

    public List<DictionaryEntry> getEntriesByNormalizedParameterName(NormalizedParameterName normalizedParameterName,
                                                                     ParameterType parameterType) {
        return new ArrayList<>(byNormalizedParameterName.getOrDefault(List.of(normalizedParameterName, parameterType),
                new LinkedHashSet<>()));
    }

    public List<DictionaryEntry> getEntriesByParameterName(ParameterName parameterName, ParameterType parameterType) {
        return new ArrayList<>(byParameterName.getOrDefault(List.of(parameterName, parameterType), new LinkedHashSet<>()));
    }

    public List<DictionaryEntry> getEntriesByValueLength(int length) {
        return dictionary.values().stream().filter(e -> e.getValue().toString().length() == length).collect(Collectors.toList());
    }

    public int size() {
        return dictionary.size();
    }

    public void clear() {
        if (this == Environment.getInstance().getGlobalResponseDictionary() || this == Environment.getInstance().getGlobalRequestDictionary()) {
            logger.warn("You are not allowed to clear the global dictionary. Please use the partial or a local dictionary.");
        } else {
            dictionary.clear();
            byParameterName.clear();
            byNormalizedParameterName.clear();
        }
    }
}
