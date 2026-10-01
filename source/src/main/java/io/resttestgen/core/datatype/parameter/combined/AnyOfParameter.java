package io.resttestgen.core.datatype.parameter.combined;

import io.resttestgen.core.datatype.parameter.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class AnyOfParameter extends CombinedSchemaParameter {

    private static final Logger logger = LogManager.getLogger(AnyOfParameter.class);

    public AnyOfParameter(Map<String, Object> parameterMap, String name) {
        super(parameterMap, name);
    }

    protected AnyOfParameter(Parameter other) {
        super(other);
    }

    // Modified for PerseveREST (REST League 2027): deepClone() used the constructor above, which drops the schemas
    // (Java chooses the constructor by the static type of the argument), so clones had no schemas. See NOTICE.
    protected AnyOfParameter(AnyOfParameter other) {
        super((CombinedSchemaParameter) other);
    }

    @Override
    public Parameter merge() {
        // TODO: pick randomly if needed
        Parameter merged = this.parametersSchemas.stream().findFirst().get();

        return merged;
    }

    @Override
    protected String getKeyFiledName() {
        return "anyOf";
    }

    @Override
    // TODO: implement
    public boolean isObjectTypeCompliant(Object o) {
        return false;
    }

    @Override
    public AnyOfParameter deepClone() {
        return new AnyOfParameter(this);
    }
}
