package com.shake.ai.openapi.model;

/**
 * Routing metadata for one path or query parameter. Type information is not held
 * here — it lives in the operation's precomputed {@link OpenApiOperation#inputSchema()}.
 *
 * @param name     parameter name; the key the LLM sends and the executor routes on
 * @param in       where the parameter goes in the request
 * @param required whether the spec marks it mandatory
 */
public record OpenApiParameter(String name, ParameterLocation in, boolean required)
{
}
