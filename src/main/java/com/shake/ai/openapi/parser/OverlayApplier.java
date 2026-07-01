package com.shake.ai.openapi.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Merges an overlay document's endpoint/parameter {@code summary} and
 * {@code description} fields onto an already-parsed {@link OpenAPI}, mutating it
 * in place. The overlay only needs the subset of the OpenAPI 3 shape it overrides
 * ({@code paths.<path>.<method>.summary/description/parameters[].description}); it
 * is not validated as a full spec.
 *
 * <p>Every path, method, and parameter referenced by the overlay must exist in the
 * target spec — this is a maintenance signal for a drifted overlay, so a mismatch
 * throws rather than being silently ignored.
 *
 * <p>The model only ever sees one operation-level text field: {@code summary} if
 * present, otherwise {@code description} (see {@code OpenApiSpecParser}). This
 * precedence applies regardless of whether the value came from the spec or an
 * overlay, so overlaying only {@code description} on an operation that already has
 * a {@code summary} has no visible effect — override {@code summary} instead.
 */
class OverlayApplier
{

    private static final String PATHS = "paths";
    private static final String SUMMARY = "summary";
    private static final String DESCRIPTION = "description";
    private static final String PARAMETERS = "parameters";
    private static final String NAME = "name";
    private static final String IN = "in";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    void apply(OpenAPI openAPI, String overlayLocation)
    {
        var overlay = readOverlay(overlayLocation);
        if (!overlay.has(PATHS))
        {
            throw new IllegalArgumentException(
                    "Overlay at " + overlayLocation + " is missing a top-level '" + PATHS + "' field");
        }
        overlay.get(PATHS).properties()
               .forEach(pathEntry -> applyPath(openAPI, pathEntry.getKey(), pathEntry.getValue()));
    }

    private void applyPath(OpenAPI openAPI, String pathTemplate, JsonNode methods)
    {
        var pathItem = openAPI.getPaths() != null ? openAPI.getPaths().get(pathTemplate) : null;
        if (pathItem == null)
        {
            throw new IllegalArgumentException("Overlay references unknown path: " + pathTemplate);
        }
        methods.properties().forEach(methodEntry -> applyOperation(pathItem, pathTemplate, methodEntry.getKey(),
                                                                    methodEntry.getValue()));
    }

    private void applyOperation(PathItem pathItem, String pathTemplate, String methodName, JsonNode overlayOperation)
    {
        PathItem.HttpMethod method;
        try
        {
            method = PathItem.HttpMethod.valueOf(methodName.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(
                    "Overlay references unknown HTTP method '" + methodName + "' on path " + pathTemplate);
        }
        var operation = pathItem.readOperationsMap().get(method);
        if (operation == null)
        {
            throw new IllegalArgumentException(
                    "Overlay references unknown method " + methodName + " on path " + pathTemplate);
        }

        if (overlayOperation.has(SUMMARY))
        {
            operation.setSummary(overlayOperation.get(SUMMARY).asText());
        }
        if (overlayOperation.has(DESCRIPTION))
        {
            operation.setDescription(overlayOperation.get(DESCRIPTION).asText());
        }

        if (overlayOperation.has(PARAMETERS))
        {
            var parameters = OpenApiSpecParser.mergedParameters(pathItem, operation);
            for (var overlayParameter : overlayOperation.get(PARAMETERS))
            {
                applyParameter(parameters, pathTemplate, methodName, overlayParameter);
            }
        }
    }

    private void applyParameter(Map<String, Parameter> parameters, String pathTemplate, String methodName,
                                JsonNode overlayParameter)
    {
        if (!overlayParameter.has(NAME))
        {
            throw new IllegalArgumentException(
                    "Overlay parameter entry on " + methodName + " " + pathTemplate + " is missing required field '"
                            + NAME + "'");
        }
        if (!overlayParameter.has(IN))
        {
            throw new IllegalArgumentException(
                    "Overlay parameter entry on " + methodName + " " + pathTemplate + " is missing required field '"
                            + IN + "'");
        }

        var name = overlayParameter.get(NAME).asText();
        var in = overlayParameter.get(IN).asText();
        var parameter = parameters.get(in + ":" + name);
        if (parameter == null)
        {
            throw new IllegalArgumentException(
                    "Overlay references unknown parameter '" + name + "' (in: " + in + ") on " + methodName + " "
                            + pathTemplate);
        }
        if (overlayParameter.has(DESCRIPTION))
        {
            parameter.setDescription(overlayParameter.get(DESCRIPTION).asText());
        }
    }

    private JsonNode readOverlay(String overlayLocation)
    {
        var resource = resourceLoader.getResource(overlayLocation);
        String contents;
        try
        {
            contents = resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to read overlay from " + overlayLocation, e);
        }

        try
        {
            var mapper = isJson(contents) ? JSON_MAPPER : YAML_MAPPER;
            return mapper.readTree(contents);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to parse overlay from " + overlayLocation, e);
        }
    }

    private boolean isJson(String contents)
    {
        var trimmed = contents.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
