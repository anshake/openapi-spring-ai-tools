package com.shake.openapi.ai.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.List;

/**
 * Merges an operation's path params, query params, and JSON request body into a
 * single flat JSON Schema {@code object}, returned as a string for
 * {@code ToolDefinition.inputSchema(...)}.
 *
 * <p>Each individual schema is serialized faithfully by swagger-core's own mapper
 * ({@code Json}/{@code Json31}, passed in by the parser to match the spec version),
 * so the full schema vocabulary — {@code allOf}/{@code oneOf}, nested objects,
 * arrays, numeric bounds, {@code nullable}, etc. — is preserved. This builder only
 * owns the operation-level merge, which has no off-the-shelf equivalent.
 *
 * <p>This is one of the two internal packages (with {@code parser/}) allowed to
 * touch swagger types; it emits a plain string, so swagger never leaks downstream.
 *
 * <p>If the request body is an object schema, its properties are flattened to the
 * top level; otherwise it is exposed under a single {@code "body"} property.
 */
public final class ParameterSchemaBuilder {

    private static final String TYPE = "type";
    private static final String OBJECT = "object";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String DESCRIPTION = "description";
    private static final String BODY_PROPERTY = "body";

    private ParameterSchemaBuilder() {
    }

    /**
     * @param parameters          path and query parameters (already filtered)
     * @param requestBodySchema   the {@code application/json} body schema, or {@code null}
     * @param requestBodyRequired whether the body is required
     * @param schemaMapper        swagger-core mapper matching the spec version
     */
    public static String build(List<Parameter> parameters, Schema<?> requestBodySchema,
                               boolean requestBodyRequired, ObjectMapper schemaMapper) {
        var root = schemaMapper.createObjectNode();
        root.put(TYPE, OBJECT);

        var properties = root.putObject(PROPERTIES);
        var required = schemaMapper.createArrayNode();

        for (var parameter : parameters) {
            properties.set(parameter.getName(), parameterSchema(parameter, schemaMapper));
            if (Boolean.TRUE.equals(parameter.getRequired())) {
                required.add(parameter.getName());
            }
        }

        addRequestBody(requestBodySchema, requestBodyRequired, properties, required, schemaMapper);

        if (!required.isEmpty()) {
            root.set(REQUIRED, required);
        }
        return root.toString();
    }

    /**
     * In OpenAPI a parameter's {@code description} lives on the parameter, not its schema.
     * Merge it into the emitted schema node (without overwriting a schema-level description)
     * so the model sees what the parameter is for.
     */
    private static JsonNode parameterSchema(Parameter parameter, ObjectMapper schemaMapper) {
        JsonNode schemaNode = schemaMapper.valueToTree(parameter.getSchema());
        if (parameter.getDescription() != null
                && schemaNode instanceof ObjectNode objectNode
                && !objectNode.has(DESCRIPTION)) {
            objectNode.put(DESCRIPTION, parameter.getDescription());
        }
        return schemaNode;
    }

    private static void addRequestBody(Schema<?> schema, boolean required, ObjectNode properties,
                                       ArrayNode requiredNode, ObjectMapper schemaMapper) {
        if (schema == null) {
            return;
        }
        var isObjectBody = schema.getProperties() != null && !schema.getProperties().isEmpty();
        JsonNode bodyNode = schemaMapper.valueToTree(schema);

        if (isObjectBody && bodyNode.get(PROPERTIES) instanceof ObjectNode bodyProperties) {
            bodyProperties.properties().forEach(entry -> properties.set(entry.getKey(), entry.getValue()));
            if (required && bodyNode.has(REQUIRED)) {
                bodyNode.get(REQUIRED).forEach(item -> requiredNode.add(item.asText()));
            }
        } else {
            properties.set(BODY_PROPERTY, bodyNode);
            if (required) {
                requiredNode.add(BODY_PROPERTY);
            }
        }
    }
}
