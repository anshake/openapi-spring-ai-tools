package com.shake.openapi.ai.model;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * One parsed OpenAPI operation, free of any swagger types. Holds everything the
 * tool callback and executor need: identity, the HTTP method/path to call, the
 * parameters to route input on, and the finished JSON Schema string for the LLM.
 *
 * @param operationId  becomes the tool name
 * @param summary      becomes the tool description
 * @param httpMethod   the HTTP method to call with
 * @param pathTemplate path with placeholders, e.g. {@code /pets/{id}}
 * @param parameters   path and query parameters (routing metadata)
 * @param inputSchema  JSON Schema string for {@code ToolDefinition.inputSchema(...)}
 */
public record OpenApiOperation(
        String operationId,
        String summary,
        HttpMethod httpMethod,
        String pathTemplate,
        List<OpenApiParameter> parameters,
        String inputSchema)
{
}
