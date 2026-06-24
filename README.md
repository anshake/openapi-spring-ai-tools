# openapi-spring-ai-tools

A small Java **library** that reads an OpenAPI 3 spec at runtime and exposes each
operation as a [Spring AI](https://docs.spring.io/spring-ai/reference/) `ToolCallback`.
Hand the result to `ChatClient.defaultTools(...)` and the LLM can call the real API
endpoints described by the spec.

## How it works

```
OpenAPI spec (classpath / file / URL)
        │  
        ▼
List<OpenApiOperation>
        │  ParameterSchemaBuilder  → JSON Schema per operation
        ▼
OpenApiToolCallback  (one ToolCallback per operation)
        │  
        ▼
ChatClient.defaultTools(provider)
```

Each operation's `operationId` becomes the tool name and its `summary` the tool
description. Path, query, and request-body parameters are merged into a single JSON
Schema that the LLM fills in; `OperationExecutor` then performs the HTTP call via
`RestClient` and returns the response body to the model.

## Write good descriptions

Because these tools are selected and invoked by an LLM — not a programmer — the spec's
descriptions are what the model reads to decide *when* to call an operation and *how* to
fill in its parameters. They are part of the prompt, not just documentation.

- Give every operation a clear `summary`/`description` that states what it does and when
  to use it.
- Describe every parameter: what it means, its expected format/units, and any constraints
  (e.g. "latitude in decimal degrees, -90 to 90").
- Mark genuinely required parameters as `required` so the model doesn't omit them.

Vague or missing descriptions are the most common reason the LLM picks the wrong tool or
sends malformed arguments.

## Requirements

- Java 25
- Spring AI 2.0

## Maven coordinates

```xml
<dependency>
    <groupId>com.shake.openapi.ai</groupId>
    <artifactId>openapi-spring-ai-tools</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Usage

`OpenApiToolBundle` is the only type you touch. Point it at a spec, set the base URL,
and `build()` a `ToolCallbackProvider`:

```java
ToolCallbackProvider tools = OpenApiToolBundle
        .from("classpath:open-meteo.yaml")   // also accepts file: or http(s): locations
        .baseUrl("https://api.open-meteo.com")
        .build();

ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(tools)
        .build();

String answer = chatClient.prompt("What is the current temperature in Amsterdam right now?").call().content();
```

## Scope (Phase 1)

Currently supported: GET/POST/PUT/PATCH/DELETE operations, path + query + JSON request
bodies, specs from classpath/file/URL.

Not yet supported: Spring Boot autoconfiguration, authentication / header injection,
operation filtering, and request bodies beyond `application/json`.
HTTP errors are propagated as runtime exceptions.

Planned for the next phase: [OpenAPI Overlay](https://spec.openapis.org/overlay/latest.html)
support, so you can enrich operation and parameter descriptions for the LLM without editing
the original spec.
