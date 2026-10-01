package io.resttestgen.implementation.helper;

import com.google.gson.Gson;
import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.datatype.parameter.structured.ObjectParameter;

import java.util.HashMap;
import java.util.Map;

/**
 * A request body that is a single value (a JSON string, number or boolean), e.g., the identifier of the patient to
 * check out in Gestao Hospital ({"type": "string"}). RestTestGen only supports objects and arrays as request bodies:
 * it discarded these, so the requests were always sent without a body and rejected. The value is the only property of
 * this object, so that RestTestGen's fuzzers and mutators treat it as usual, but the body is the value alone.
 */
public class PrimitiveRequestBody extends ObjectParameter {

    private static final Gson gson = new Gson();

    public PrimitiveRequestBody(LeafParameter value) {
        super(schema(), "");
        setRequired(true);
        value.setRequired(true);
        addChild(value);
    }

    private static Map<String, Object> schema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("in", "request_body");
        return schema;
    }

    private PrimitiveRequestBody(PrimitiveRequestBody other) {
        // ObjectParameter's copy constructor that copies the properties is private
        super((Parameter) other);
        other.getProperties().forEach(p -> addChild(p.deepClone()));
    }

    @Override
    public PrimitiveRequestBody deepClone() {
        return new PrimitiveRequestBody(this);
    }

    /**
     * The value alone, as JSON: "abc", 5, true. Empty if the value was removed (e.g., by a mutation).
     */
    @Override
    public String getJsonString() {
        for (Parameter property : getProperties()) {
            if (property instanceof LeafParameter) {
                Object value = ((LeafParameter) property).getConcreteValue();
                if (value == null) {
                    return "";
                }
                if (value instanceof Double && (Double) value % 1 == 0) {
                    return Long.toString(((Double) value).longValue());
                }
                return gson.toJson(value);
            }
            // A mutation replaced the value with an object or an array: without its name
            return property.getJsonString().replaceFirst("^\"[^\"]*\": ", "");
        }
        return "";
    }
}
