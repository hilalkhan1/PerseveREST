package io.resttestgen.implementation.helper;

import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.ParameterFactory;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.datatype.parameter.combined.AllOfParameter;
import io.resttestgen.core.datatype.parameter.combined.CombinedSchemaParameter;
import io.resttestgen.core.datatype.parameter.exceptions.ParameterCreationException;
import io.resttestgen.core.datatype.parameter.structured.ArrayParameter;
import io.resttestgen.core.datatype.parameter.structured.ObjectParameter;
import io.resttestgen.core.datatype.parameter.structured.StructuredParameter;
import io.resttestgen.core.openapi.Operation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces the combined schemas (allOf, anyOf, oneOf) of an operation with concrete schemas, so that values are
 * generated for all their fields and request bodies are valid JSON: the schemas of an allOf are merged, and one
 * schema is chosen at random for anyOf and oneOf. RestTestGen 25.12 left them unresolved (NominalFuzzer's
 * resolveCombinedSchemas() was empty), so the bodies built from them had missing or malformed parts: for example,
 * {"name": "Leo", "birthDate": "2010-09-07", "type": } for the pets of Spring PetClinic. Request bodies whose root
 * schema is combined were even discarded by the parser (e.g., the vets of Spring PetClinic, allOf [VetFields, {id}]),
 * so the requests were sent without a body.
 */
public class CombinedSchemaResolver {

    private static final Logger logger = LogManager.getLogger(CombinedSchemaResolver.class);

    // Combined schemas nested in resolved ones (e.g., an allOf property of an allOf) are resolved in the following
    // passes; the limits protect from recursive schemas
    private static final int MAX_PASSES = 10;
    private static final int MAX_DEPTH = 10;

    // The alternative request bodies of the operations whose root schema is a oneOf or anyOf (by operation)
    private static final Map<String, List<StructuredParameter>> requestBodyAlternatives = new ConcurrentHashMap<>();

    /**
     * The request body of an operation from its schema (with "in": "request_body"). Structured schemas are kept,
     * combined ones are resolved: allOf schemas are merged, and for oneOf and anyOf the first structured schema is used,
     * while the others are remembered, so that resolveAll can use any of them. A single value (string, number, boolean)
     * becomes a PrimitiveRequestBody, whose value is named after the description of the request body when it is an
     * identifier (Spring's springdoc gives the name of the Java parameter, e.g., "idPatient"), otherwise "body".
     * @throws ParameterCreationException if the schema cannot be the root of a request body.
     */
    public static StructuredParameter resolveRequestBody(Operation operation, Map<String, Object> schema, String description) {
        Parameter body = ParameterFactory.getParameter(schema, "");
        if (body instanceof LeafParameter) {
            String name = description != null && description.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") ? description : "body";
            return new PrimitiveRequestBody((LeafParameter) ParameterFactory.getParameter(schema, name));
        }
        return resolveRequestBody(operation, body);
    }

    static StructuredParameter resolveRequestBody(Operation operation, Parameter body) {
        if (body instanceof StructuredParameter) {
            return (StructuredParameter) body;
        }
        List<StructuredParameter> alternatives = new ArrayList<>();
        if (body instanceof AllOfParameter) {
            addIfStructured(alternatives, resolve((CombinedSchemaParameter) body, new Random(0)));
        } else if (body instanceof CombinedSchemaParameter) {
            CombinedSchemaParameter combinedSchema = (CombinedSchemaParameter) body;
            for (Parameter schema : combinedSchema.getParametersSchemas()) {
                Parameter alternative = schema instanceof CombinedSchemaParameter ?
                        resolve((CombinedSchemaParameter) schema, new Random(0)) : schema.deepClone();
                alternative.setName(combinedSchema.getName());
                alternative.setRequired(combinedSchema.isRequired());
                addIfStructured(alternatives, alternative);
            }
        }
        if (alternatives.isEmpty()) {
            throw new ParameterCreationException("Cannot cast to structured parameter");
        }
        if (alternatives.size() > 1) {
            requestBodyAlternatives.put(operation.toString(), alternatives);
        }
        return alternatives.get(0);
    }

    private static void addIfStructured(List<StructuredParameter> alternatives, Parameter parameter) {
        if (parameter instanceof StructuredParameter) {
            alternatives.add((StructuredParameter) parameter);
        }
    }

    /**
     * Resolves all the combined schemas of an (editable) operation, choosing at random among the alternatives.
     */
    public static void resolveAll(Operation operation, Random random) {
        List<StructuredParameter> alternatives = requestBodyAlternatives.get(operation.toString());
        if (alternatives != null) {
            operation.setRequestBody(alternatives.get(random.nextInt(alternatives.size())).deepClone());
        }

        for (int pass = 0; pass < MAX_PASSES; pass++) {
            Collection<CombinedSchemaParameter> combinedSchemas = operation.getCombinedSchemas();
            if (combinedSchemas.isEmpty()) {
                return;
            }
            for (CombinedSchemaParameter combinedSchema : combinedSchemas) {
                try {
                    replace(combinedSchema, resolve(combinedSchema, random));
                } catch (RuntimeException e) {
                    // A combined schema that cannot be resolved is left out of the request, as RestTestGen could not
                    // generate a value for it anyway
                    logger.warn("Could not resolve combined schema {} of {}: {}", combinedSchema.getName(), operation, e.toString());
                    if (combinedSchema.getParent() instanceof ObjectParameter) {
                        combinedSchema.getParent().removeChild(combinedSchema);
                    }
                }
            }
        }
    }

    private static void replace(CombinedSchemaParameter combinedSchema, Parameter resolved) {
        Parameter parent = combinedSchema.getParent();
        if (parent instanceof ArrayParameter && ((ArrayParameter) parent).getReferenceElement() == combinedSchema) {
            resolved.setOperation(combinedSchema.getOperation());
            resolved.setLocation(combinedSchema.getLocation());
            ((ArrayParameter) parent).setReferenceElement(resolved);
        } else {
            combinedSchema.replace(resolved);
        }
    }

    /**
     * A concrete schema for the combined schema, with its name and required flag.
     */
    public static Parameter resolve(CombinedSchemaParameter combinedSchema, Random random) {
        return resolve(combinedSchema, random, 0);
    }

    private static Parameter resolve(CombinedSchemaParameter combinedSchema, Random random, int depth) {
        List<Parameter> schemas = new ArrayList<>();
        for (Parameter schema : combinedSchema.getParametersSchemas()) {
            schemas.add(schema instanceof CombinedSchemaParameter && depth < MAX_DEPTH ?
                    resolve((CombinedSchemaParameter) schema, random, depth + 1) : schema.deepClone());
        }
        if (schemas.isEmpty()) {
            throw new IllegalStateException("combined schema without schemas");
        }

        Parameter resolved;
        if (combinedSchema instanceof AllOfParameter) {
            // The first schema, with the properties of the other object schemas that it does not have yet
            resolved = schemas.get(0);
            if (resolved instanceof ObjectParameter) {
                ObjectParameter object = (ObjectParameter) resolved;
                for (Parameter schema : schemas.subList(1, schemas.size())) {
                    if (schema instanceof ObjectParameter) {
                        for (Parameter property : ((ObjectParameter) schema).getProperties()) {
                            if (object.getProperties().stream().noneMatch(p -> p.getName().equals(property.getName()))) {
                                object.addChild(property.deepClone());
                            }
                        }
                    }
                }
            }
        } else {
            resolved = schemas.get(random.nextInt(schemas.size()));
        }
        resolved.setName(combinedSchema.getName());
        resolved.setRequired(combinedSchema.isRequired());
        return resolved;
    }
}
