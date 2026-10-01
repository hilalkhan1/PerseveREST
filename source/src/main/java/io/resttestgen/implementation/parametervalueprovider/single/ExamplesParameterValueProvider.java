package io.resttestgen.implementation.parametervalueprovider.single;

// Modified for PerseveREST (REST League 2027): examples that are date/time patterns are replaced. See NOTICE.

import io.resttestgen.core.Environment;
import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.testing.parametervalueprovider.CountableParameterValueProvider;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ExamplesParameterValueProvider extends CountableParameterValueProvider {

    public ExamplesParameterValueProvider() {
        setSelfValueSourceClass();
    }

    @Override
    protected Collection<Object> collectValuesFor(LeafParameter leafParameter) {
        Set<Object> values;
        switch (getValueSourceClass()) {
            case SAME_NAME:
                values = collectParametersWithSameName(leafParameter.getName()).stream()
                        .map(Parameter::getExamples)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
                break;
            case SAME_NORMALIZED_NAME:
                values = collectParametersWithSameNormalizedName(leafParameter.getNormalizedName()).stream()
                        .map(Parameter::getExamples)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
                break;
            default:
                values = leafParameter.getExamples();
        }
        values = values.stream().map(ExamplesParameterValueProvider::replaceDatePattern).collect(Collectors.toSet());
        return strict ? filterNonCompliantValues(values, leafParameter) : values;
    }

    /**
     * Specifications often give the pattern of a date as its example (e.g., "dd-MM-yyyy"): a date in that pattern is
     * used instead, as the API rejects the pattern itself.
     */
    private static Object replaceDatePattern(Object example) {
        if (example instanceof String) {
            String date = Environment.getInstance().getRandom().nextDateForPattern((String) example);
            if (date != null) {
                return date;
            }
        }
        return example;
    }
}
