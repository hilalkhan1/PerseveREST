package io.resttestgen.core.datatype.parameter.visitor;

// Modified for PerseveREST (REST League 2027): the visitor returned no combined schemas at all. See NOTICE.

import io.resttestgen.core.datatype.parameter.Parameter;
import io.resttestgen.core.datatype.parameter.combined.CombinedSchemaParameter;
import io.resttestgen.core.datatype.parameter.leaves.BooleanParameter;
import io.resttestgen.core.datatype.parameter.leaves.LeafParameter;
import io.resttestgen.core.datatype.parameter.leaves.NumberParameter;
import io.resttestgen.core.datatype.parameter.leaves.StringParameter;
import io.resttestgen.core.datatype.parameter.structured.ArrayParameter;
import io.resttestgen.core.datatype.parameter.structured.ObjectParameter;
import io.resttestgen.core.datatype.parameter.structured.StructuredParameter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * This visitor implementation helps to collect the combined schemas (allOf, anyOf, oneOf) of a parameter: those in
 * the properties of objects, and those in the elements and in the reference element of arrays. The schemas listed by
 * a combined schema are not visited: they are part of it.
 */
public class CombinedSchemasVisitor implements Visitor<Collection<CombinedSchemaParameter>> {

    @Override
    public Collection<CombinedSchemaParameter> visit(Parameter element) {
        return List.of();
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(LeafParameter element) {
        return List.of();
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(StructuredParameter element) {
        return List.of();
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(ArrayParameter element) {
        List<CombinedSchemaParameter> combinedSchemas = new LinkedList<>();
        if (element.getReferenceElement() != null) {
            combinedSchemas.addAll(element.getReferenceElement().accept(this));
        }
        element.getElements().forEach(e -> combinedSchemas.addAll(e.accept(this)));
        return combinedSchemas;
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(ObjectParameter element) {
        List<CombinedSchemaParameter> combinedSchemas = new LinkedList<>();
        element.getProperties().forEach(e -> combinedSchemas.addAll(e.accept(this)));
        return combinedSchemas;
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(StringParameter element) {
        return List.of();
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(NumberParameter element) {
        return List.of();
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(BooleanParameter element) {
        return List.of();
    }

    @Override
    public Collection<CombinedSchemaParameter> visit(CombinedSchemaParameter element) {
        return List.of(element);
    }
}
