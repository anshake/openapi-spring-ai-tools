package com.shake.openapi.ai.parser;

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
import java.util.List;

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
 */
class OverlayApplier
{

    private static final String SUMMARY = "summary";
    private static final String DESCRIPTION = "description";
    private static final String PARAMETERS = "parameters";
    private static final String NAME = "name";
    private static final String IN = "in";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    void apply(OpenAPI openAPI, String overlayLocation)
    {
        var overlay = readOverlay(overlayLocation);
        var paths = overlay.path("paths");
        paths.properties().forEach(pathEntry -> applyPath(openAPI, pathEntry.getKey(), pathEntry.getValue()));
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
        var method = PathItem.HttpMethod.valueOf(methodName.toUpperCase());
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
            for (var overlayParameter : overlayOperation.get(PARAMETERS))
            {
                applyParameter(operation.getParameters(), pathTemplate, methodName, overlayParameter);
            }
        }
    }

    private void applyParameter(List<Parameter> parameters, String pathTemplate, String methodName,
                                JsonNode overlayParameter)
    {
        var name = overlayParameter.path(NAME).asText(null);
        var in = overlayParameter.path(IN).asText(null);
        var parameter = parameters == null
                ? null
                : parameters.stream()
                            .filter(p -> p.getName().equals(name) && p.getIn().equals(in))
                            .findFirst()
                            .orElse(null);
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
            ObjectMapper mapper = isJson(contents) ? new ObjectMapper() : new YAMLMapper();
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
