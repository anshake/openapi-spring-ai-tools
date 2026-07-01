package com.shake.ai.openapi.model;

/**
 * Where a parameter is carried in the HTTP request. Phase 1 supports only the
 * two locations the executor routes on; header and cookie parameters are filtered
 * out by the parser.
 */
public enum ParameterLocation
{

    PATH,
    QUERY;

    /**
     * Maps an OpenAPI {@code in} value to a location.
     *
     * @throws IllegalArgumentException for anything other than {@code "path"} or {@code "query"}
     */
    public static ParameterLocation from(String in)
    {
        return switch (in)
        {
            case "path" -> PATH;
            case "query" -> QUERY;
            default -> throw new IllegalArgumentException("Unsupported parameter location: " + in);
        };
    }
}
