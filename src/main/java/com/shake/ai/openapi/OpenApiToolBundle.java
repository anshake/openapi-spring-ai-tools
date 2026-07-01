package com.shake.ai.openapi;

import com.shake.ai.openapi.callback.OpenApiToolCallback;
import com.shake.ai.openapi.http.OperationExecutor;
import com.shake.ai.openapi.parser.OpenApiSpecParser;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Objects;

/**
 * Public entry point: reads an OpenAPI 3 spec and exposes each operation as a
 * Spring AI {@link ToolCallback}, grouped into a {@link ToolCallbackProvider}.
 *
 * <pre>{@code
 * ToolCallbackProvider tools = OpenApiToolBundle
 *         .from("classpath:petstore.yaml")
 *         .baseUrl("https://api.petstore.com")
 *         .build();
 *
 * ChatClient.builder(chatModel).defaultTools(tools).build();
 * }</pre>
 */
public final class OpenApiToolBundle
{

    private final String specLocation;
    private String overlayLocation;
    private String baseUrl;
    private RequestAuthCustomizer auth;

    private OpenApiToolBundle(String specLocation)
    {
        this.specLocation = specLocation;
    }

    /**
     * @param specLocation a Spring resource location: {@code classpath:}, {@code file:},
     *                     or {@code http(s):}
     */
    public static OpenApiToolBundle from(String specLocation)
    {
        return new OpenApiToolBundle(Objects.requireNonNull(specLocation, "specLocation must not be null"));
    }

    /**
     * An overlay document (same resource location semantics as {@link #from}) whose
     * endpoint and parameter {@code summary}/{@code description} fields override the
     * spec's. Optional.
     */
    public OpenApiToolBundle overlay(String overlayLocation)
    {
        this.overlayLocation = Objects.requireNonNull(overlayLocation, "overlayLocation must not be null");
        return this;
    }

    /**
     * Base URL the operation paths are resolved against.
     */
    public OpenApiToolBundle baseUrl(String baseUrl)
    {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        return this;
    }

    /**
     * Authentication applied before every request. Optional; last-one-wins.
     */
    public OpenApiToolBundle auth(RequestAuthCustomizer auth)
    {
        this.auth = Objects.requireNonNull(auth, "auth must not be null");
        return this;
    }

    public ToolCallbackProvider build()
    {
        var operations = new OpenApiSpecParser().parse(specLocation, overlayLocation);
        var executor = new OperationExecutor(baseUrl, auth);
        var callbacks = operations.stream()
                                  .map(operation -> new OpenApiToolCallback(operation, executor))
                                  .toList();
        return ToolCallbackProvider.from(callbacks);
    }
}
