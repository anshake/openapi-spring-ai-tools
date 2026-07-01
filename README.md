# openapi-spring-ai-tools

Give an LLM an OpenAPI 3 spec and let it call the real API.

This library reads an **[OpenAPI 3](https://spec.openapis.org/oas/latest.html)** spec at
runtime and turns each operation into a **[Spring AI](https://docs.spring.io/spring-ai/reference/)**
`ToolCallback`. Pass the result to `ChatClient.defaultTools(...)` and the model can call the
endpoints the spec describes. You write no per-endpoint code.

## Why

To let a model call an API, you normally hand-write a `ToolCallback` for every endpoint:
name it, describe it, declare its parameters, make the HTTP call. That is a lot of
boilerplate, and all of it just repeats what the OpenAPI spec already says.

The spec is the source of truth. This library derives the tools from it, so you don't
maintain the same contract twice.

## How It Works

A tool definition the model sees is built from three things, and the spec provides all
three:

| The model needs | Comes from the spec |
| --- | --- |
| A tool **name** | `operationId`, or a name generated from the method and path if omitted |
| A **description** of when to use it | `summary` / `description`, optionally overridden by an [overlay](#overlay) |
| A **JSON Schema** of parameters | path, query, and request-body parameters |

When the model calls a tool, `OperationExecutor` makes the HTTP request with `RestClient`
and returns the response body to the model.

```
OpenAPI spec (classpath / file / URL)   overlay (classpath / file / URL), optional
        │                                       │
        └───────────────────┬───────────────────┘
                             │  OverlayApplier  → overrides summary/description in place
                             ▼
                    List<OpenApiOperation>
                             │  ParameterSchemaBuilder  → JSON Schema per operation
                             ▼
                    OpenApiToolCallback  (one ToolCallback per operation)
                             │
                             ▼
                    ChatClient.defaultTools(provider)
```

## Usage

**Requirements:** Java 17+, Spring AI 2.0.

To get started, add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.anshake</groupId>
    <artifactId>openapi-spring-ai-tools</artifactId>
    <version>0.4.1</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'io.github.anshake:openapi-spring-ai-tools:0.4.1'
```

Everything runs through a single type, `OpenApiToolBundle`:

```java
ToolCallbackProvider tools = OpenApiToolBundle
        .from("classpath:open-meteo.yaml")   // also accepts file: or http(s): locations
        .baseUrl("https://api.open-meteo.com")
        .build();

ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(tools)
        .build();

String answer = chatClient
        .prompt("What is the current temperature in Amsterdam right now?")
        .call()
        .content();
```

### Authentication

For secured APIs, set a `RequestAuthCustomizer` with `.auth(...)`. It is applied to every
request, and credentials are read through a `Supplier` at request time, so rotated keys or
refreshed tokens are picked up automatically:

```java
OpenApiToolBundle
        .from("classpath:open-meteo.yaml")
        .baseUrl("https://api.example.com")
        .auth(RequestAuthCustomizer.bearer(() -> tokenStore.current()))
        .build();
```

Predefined customizers:

```java
RequestAuthCustomizer.bearer(() -> token)                  // Authorization: Bearer <token>
RequestAuthCustomizer.apiKey("X-Api-Key", () -> key)       // header
RequestAuthCustomizer.apiKeyQuery("api_key", () -> key)    // ?api_key=<key> in the URL
```

For anything else, implement the interface — mutate the request's headers (or URI) and
return it:

```java
.auth(request -> {
    request.getHeaders().set("X-Custom", customSupplier.get());
    return request;
})
```

### Overlay

Some specs, especially third-party ones, have poor or missing `summary`/`description`
fields, which makes the model pick the wrong tool or the wrong parameter. If you can't fix
the spec at the source, layer an overlay on top of it with `.overlay(...)`:

```java
OpenApiToolBundle
        .from("classpath:open-meteo.yaml")
        .overlay("classpath:open-meteo-overlay.yaml")   // also accepts file: or http(s): locations
        .baseUrl("https://api.open-meteo.com")
        .build();
```

The overlay is optional and only needs the part of the OpenAPI shape it overrides —
matching `paths.<path>.<method>.summary` / `.description`, and `.parameters[]` matched by
`name` + `in`:

```yaml
paths:
  /v1/forecast:
    get:
      summary: Get the hourly and daily weather forecast for a location
      parameters:
        - name: latitude
          in: query
          description: Latitude in decimal degrees, -90 to 90
```

Every path, method, and parameter the overlay references must exist in the target spec —
a typo throws rather than being silently ignored.

The model only ever sees one operation-level text field: `summary` if present, otherwise
`description`. This is true with or without an overlay, so overlaying only `description` on
an operation that already has a `summary` has no visible effect — override `summary`
instead.

## Write Good Descriptions

The model decides which tool to call. It decides from the descriptions in
the spec, so those descriptions are part of the prompt, not just documentation.

- Give every operation a `summary` that says what it does and when to use it.
- Describe every parameter: what it means, its format or units, and any limits
  (e.g. *"latitude in decimal degrees, -90 to 90"*).
- Mark required parameters as `required` so the model doesn't skip them.

Vague descriptions are the most common reason a model picks the wrong tool or sends bad
arguments. Spend your time here first.

## References

- [Spring AI reference documentation](https://docs.spring.io/spring-ai/reference/)
- [OpenAPI 3 specification](https://spec.openapis.org/oas/latest.html)
