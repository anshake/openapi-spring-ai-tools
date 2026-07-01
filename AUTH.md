## Authentication

### Design

One `RequestAuthCustomizer` per bundle. Optional. If not set, requests are made without authentication.

`RequestAuthCustomizer` is a `@FunctionalInterface` receiving an `HttpRequest`
(`org.springframework.http.HttpRequest`) and returning the request to proceed with.
It is wired as a `ClientHttpRequestInterceptor`, so it runs at the HTTP layer where the
request can be both mutated (headers) and swapped (URI). Implementations mutate the
headers and return the same request, or return a wrapper when the URI must change (as
`apiKeyQuery` does). Credentials are pulled via `Supplier<String>` — called at request
time, supporting rotation, vault lookups, or any dynamic resolution.

Setting `auth(...)` twice on the builder is last-one-wins. Multiple/stacked customizers are not supported.

`ToolContext` is not exposed. `RestClient` is not exposed.

### Public API

```java
package com.shake.ai.openapi;

@FunctionalInterface
public interface RequestAuthCustomizer {

    HttpRequest apply(HttpRequest request) throws IOException;

    static RequestAuthCustomizer bearer(Supplier<String> token) {
        return request -> {
            request.getHeaders().setBearerAuth(token.get());
            return request;
        };
    }

    static RequestAuthCustomizer apiKey(String headerName, Supplier<String> key) {
        return request -> {
            request.getHeaders().set(headerName, key.get());
            return request;
        };
    }

    // API key carried in the URL as a query parameter (e.g. ?api_key=xxx)
    static RequestAuthCustomizer apiKeyQuery(String paramName, Supplier<String> key) {
        return request -> new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return UriComponentsBuilder.fromUri(super.getURI())
                    .queryParam(paramName, key.get())
                    .build(true)
                    .toUri();
            }
        };
    }
}
```

### Builder

```java
OpenApiToolBundle.from(spec)
    .baseUrl("https://api.example.com")
    .auth(RequestAuthCustomizer.bearer(() -> vault.getToken()))
    .build();

// or
    .auth(RequestAuthCustomizer.apiKey("X-Api-Key", () -> config.getApiKey()))

// or
    .auth(RequestAuthCustomizer.apiKeyQuery("api_key", () -> config.getApiKey()))

// or custom:
    .auth(request -> {
        request.getHeaders().set("X-Custom", mySupplier.get());
        return request;
    })
```
