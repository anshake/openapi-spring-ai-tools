package com.shake.openapi.ai;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Applies authentication to a request before it is sent. Optional per bundle —
 * if not set, requests are made without authentication.
 *
 * <p>Credentials are pulled via {@link Supplier} at request time, supporting
 * rotation, vault lookups, or any dynamic resolution.
 *
 * <p>Implementations may mutate the request's headers and must return the request
 * to proceed with — typically the same instance, or a wrapper when the URI must
 * change (see {@link #apiKeyQuery}).
 */
@FunctionalInterface
public interface RequestAuthCustomizer
{

    HttpRequest apply(HttpRequest request) throws IOException;

    static RequestAuthCustomizer bearer(Supplier<String> token)
    {
        Objects.requireNonNull(token, "token supplier must not be null");
        return request ->
        {
            request.getHeaders().setBearerAuth(token.get());
            return request;
        };
    }

    static RequestAuthCustomizer apiKey(String headerName, Supplier<String> key)
    {
        Objects.requireNonNull(headerName, "headerName must not be null");
        Objects.requireNonNull(key, "key supplier must not be null");
        return request ->
        {
            request.getHeaders().set(headerName, key.get());
            return request;
        };
    }

    static RequestAuthCustomizer apiKeyQuery(String paramName, Supplier<String> key)
    {
        Objects.requireNonNull(paramName, "paramName must not be null");
        Objects.requireNonNull(key, "key supplier must not be null");
        return request ->
        {
            var resolvedKey = UriUtils.encode(key.get(), StandardCharsets.UTF_8);
            return new HttpRequestWrapper(request)
            {
                @Override
                public URI getURI()
                {
                    return UriComponentsBuilder.fromUri(super.getURI())
                                               .queryParam(paramName, resolvedKey)
                                               .build(true)
                                               .toUri();
                }
            };
        };
    }
}
