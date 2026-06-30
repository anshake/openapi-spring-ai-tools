package com.shake.openapi.ai.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shake.openapi.ai.model.OpenApiOperation;
import com.shake.openapi.ai.model.OpenApiParameter;
import com.shake.openapi.ai.model.ParameterLocation;
import com.shake.openapi.ai.schema.ParameterSchemaBuilder;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an OpenAPI 3 spec from a Spring resource location ({@code classpath:},
 * {@code file:}, {@code http(s):}) and flattens it into {@link OpenApiOperation}s.
 *
 * <p>This and {@code schema/} are the only packages that touch swagger types:
 * swagger's parsed model is consumed here and converted into swagger-free records,
 * with each operation's input schema precomputed via {@link ParameterSchemaBuilder}.
 * The spec's internal {@code $ref}s are resolved so schemas are inlined.
 */
public class OpenApiSpecParser
{

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    public List<OpenApiOperation> parse(String specLocation)
    {
        var openAPI = readSpec(specLocation);
        if (openAPI.getPaths() == null)
        {
            return List.of();
        }

        var schemaMapper = openAPI.getSpecVersion() == SpecVersion.V31 ? Json31.mapper() : Json.mapper();
        var operations = new ArrayList<OpenApiOperation>();
        openAPI.getPaths().forEach((pathTemplate, pathItem) ->
                                           pathItem.readOperationsMap().forEach((method, operation) ->
                                                                                        operations.add(
                                                                                                toOperation(method,
                                                                                                            pathTemplate,
                                                                                                            operation,
                                                                                                            schemaMapper))));
        return operations;
    }

    private OpenAPI readSpec(String specLocation)
    {
        var resource = resourceLoader.getResource(specLocation);
        String contents;
        try
        {
            contents = resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to read OpenAPI spec from " + specLocation, e);
        }

        var options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        var result = new OpenAPIV3Parser().readContents(contents, null, options);
        if (result.getOpenAPI() == null)
        {
            throw new IllegalArgumentException(
                    "Could not parse OpenAPI spec from " + specLocation + ": " + result.getMessages());
        }
        return result.getOpenAPI();
    }

    private OpenApiOperation toOperation(PathItem.HttpMethod method, String pathTemplate, Operation operation,
                                         ObjectMapper schemaMapper)
    {
        var swaggerParameters = pathAndQueryParameters(operation);
        var bodySchema = jsonBodySchema(operation.getRequestBody());
        var bodyRequired = operation.getRequestBody() != null
                && Boolean.TRUE.equals(operation.getRequestBody().getRequired());

        var inputSchema = ParameterSchemaBuilder.build(swaggerParameters, bodySchema, bodyRequired, schemaMapper);

        var parameters = swaggerParameters.stream()
                                          .map(p -> new OpenApiParameter(
                                                  p.getName(),
                                                  ParameterLocation.from(p.getIn()),
                                                  Boolean.TRUE.equals(p.getRequired())))
                                          .toList();

        var operationId = operation.getOperationId() != null
                ? operation.getOperationId()
                : fallbackOperationId(method, pathTemplate);
        var summary = operation.getSummary() != null ? operation.getSummary() : operation.getDescription();

        return new OpenApiOperation(
                operationId, summary, HttpMethod.valueOf(method.name()), pathTemplate, parameters, inputSchema);
    }

    private List<Parameter> pathAndQueryParameters(Operation operation)
    {
        if (operation.getParameters() == null)
        {
            return List.of();
        }
        return operation.getParameters().stream()
                        .filter(p -> "path".equals(p.getIn()) || "query".equals(p.getIn()))
                        .toList();
    }

    private Schema<?> jsonBodySchema(RequestBody requestBody)
    {
        if (requestBody == null || requestBody.getContent() == null)
        {
            return null;
        }
        var jsonContent = requestBody.getContent().get(JSON_CONTENT_TYPE);
        return jsonContent != null ? jsonContent.getSchema() : null;
    }

    private String fallbackOperationId(PathItem.HttpMethod method, String pathTemplate)
    {
        return method.name().toLowerCase() + pathTemplate.replaceAll("[^a-zA-Z0-9]+", "_");
    }
}
