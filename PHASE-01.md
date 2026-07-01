# openapi-spring-ai-tools — Phase 1 Implementation Brief

## What this is

A Java **library** (not an application) that reads an OpenAPI 3 spec at runtime and exposes each operation as a Spring AI `ToolCallback`. The consumer passes the result to `ChatClient.defaultTools()` and the LLM can invoke real API endpoints as tools.

No code generation. No annotation processing. Pure runtime.

---

## Stack

- Java 25
- Spring Boot 4.1
- Spring AI 2.0
- Swagger Parser `io.swagger.parser.v3:swagger-parser:2.1.43`
- `RestClient` (from `spring-web`) for HTTP execution

---

## Maven coordinates

```xml
<groupId>io.github.anshake</groupId>
<artifactId>openapi-spring-ai-tools</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

---

## Target developer experience

```java
ToolCallbackProvider tools = OpenApiToolBundle
    .from("classpath:petstore.yaml")   // or a URL string
    .baseUrl("https://api.petstore.com")
    .build();

ChatClient.builder(chatModel)
    .defaultTools(tools)
    .build();
```

`build()` returns a `ToolCallbackProvider`. That is the only type the consumer ever touches.

---

## Project structure

```
openapi-spring-ai-tools/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/shake/openapi/ai/
    │           ├── OpenApiToolBundle.java               ← public API, the builder
    │           ├── model/
    │           │   └── OpenApiOperation.java            ← internal parsed representation of one operation
    │           ├── parser/
    │           │   └── OpenApiSpecParser.java           ← wraps swagger-parser; returns List<OpenApiOperation>
    │           ├── schema/
    │           │   └── ParameterSchemaBuilder.java      ← merges path/query/body params into one JSON Schema string
    │           ├── callback/
    │           │   └── OpenApiToolCallback.java         ← implements org.springframework.ai.tool.ToolCallback; one instance per operation
    │           │                                          (no custom provider class needed — builder returns ToolCallbackProvider.from(list))
    │           └── http/
    │               └── OperationExecutor.java           ← executes an operation via RestClient given parsed JSON input
    └── test/
        └── java/
            └── com/shake/openapi/ai/
                ├── OpenApiToolBundleTest.java
                └── parser/
                    └── OpenApiSpecParserTest.java
```

Everything under `parser/`, `schema/`, `callback/`, and `http/` is internal. Only `OpenApiToolBundle` is public API.

---

## Key Spring AI types

### `org.springframework.ai.tool.ToolCallback`

The interface `OpenApiToolCallback` must implement:

```java
package org.springframework.ai.tool;

public interface ToolCallback {

    // Required: return the tool definition the LLM uses to decide when/how to call this tool
    ToolDefinition getToolDefinition();

    // Required: execute the tool and return the result as a String back to the LLM
    String call(String toolInput);

}
```

`ToolDefinition` is built like this:

```java
// org.springframework.ai.tool.definition.ToolDefinition
ToolDefinition def = ToolDefinition.builder()
    .name("getPetById")              // operationId from the spec
    .description("Find pet by ID")   // operation summary from the spec
    .inputSchema(jsonSchemaString)   // raw JSON Schema string — see ParameterSchemaBuilder
    .build();
```

---

### `org.springframework.ai.tool.ToolCallbackProvider`

The interface that groups multiple `ToolCallback` instances:

```java
package org.springframework.ai.tool;

public interface ToolCallbackProvider {

    // Required: return all ToolCallback instances this provider exposes
    ToolCallback[] getToolCallbacks();

    // Static factory — wraps a List<ToolCallback> into a ToolCallbackProvider
    static ToolCallbackProvider from(List<? extends ToolCallback> toolCallbacks) { ... }
}
```

**Important:** Because `ToolCallbackProvider.from(List<ToolCallback>)` exists as a static factory, `OpenApiToolCallbackProvider` does **not** need to be a custom class. The builder can simply return:

```java
return ToolCallbackProvider.from(callbacks); // callbacks is List<OpenApiToolCallback>
```

---

## Data flow

```
OpenAPI spec (file or URL)
        │
        ▼
OpenApiSpecParser              → List<OpenApiOperation>
        │
        ▼
ParameterSchemaBuilder         → JSON Schema string per operation
        │
        ▼
OpenApiToolCallback            → ToolCallback per operation
        │
        ▼
ToolCallbackProvider.from(list) → ToolCallbackProvider (returned by builder)
        │
        ▼
ChatClient.defaultTools(...)
```

---

## OpenApiOperation (internal model)

Represents one parsed OpenAPI operation. Holds everything needed to build a `ToolCallback` and execute the HTTP call:

```java
record OpenApiOperation(
    String operationId,      // becomes tool name
    String summary,          // becomes tool description
    String httpMethod,       // GET, POST, PUT, DELETE, etc.
    String pathTemplate,     // e.g. /pets/{id}
    List<OpenApiParameter> parameters,   // path + query params
    OpenApiRequestBody requestBody       // nullable
) {}
```

---

## ParameterSchemaBuilder

Merges path parameters, query parameters, and the request body schema into a single flat JSON Schema object. This string is passed directly to `ToolDefinition.builder().inputSchema(...)`.

All three sources become properties on a single JSON Schema `object`. Required fields are preserved from the spec.

---

## OpenApiToolCallback — call() contract

`call(String toolInput)` receives a JSON object string from the LLM. It must:

1. Parse the JSON
2. Substitute path parameters into the URL template (e.g. `/pets/{id}` → `/pets/42`)
3. Append remaining scalar values as query parameters (for GET/DELETE)
4. Send the remaining object as the request body (for POST/PUT/PATCH)
5. Return the HTTP response body as a String

Use `OperationExecutor` for this logic. `call()` itself should stay thin.

---

## pom.xml dependencies (essential)

```xml
<!-- Spring AI core: ToolCallback, ToolDefinition, ToolCallbackProvider -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-core</artifactId>
</dependency>

<!-- RestClient -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>

<!-- OpenAPI 3 parser -->
<dependency>
    <groupId>io.swagger.parser.v3</groupId>
    <artifactId>swagger-parser</artifactId>
    <version>2.1.43</version>
</dependency>

<!-- Test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Use Spring Boot 4.1 and Spring AI 2.0 BOMs for version management.

---

## Out of scope for Phase 1

- Spring Boot autoconfiguration (`@EnableAutoConfiguration`, `application.yaml` wiring)
- Authentication / header injection
- Operation filtering (include/exclude by tag or operationId)
- Async / reactive execution
- Request body schema beyond `application/json`
- Error handling beyond propagating HTTP errors as RuntimeException

---

## Where to start

1. Create the `pom.xml` with the structure above
2. Implement `OpenApiOperation` and `OpenApiParameter` records
3. Implement `OpenApiSpecParser` — parse a spec from classpath or URL into `List<OpenApiOperation>`
4. Implement `ParameterSchemaBuilder` — produce a JSON Schema string from an `OpenApiOperation`
5. Implement `OpenApiToolCallback` — construct `ToolDefinition`, delegate `call()` to `OperationExecutor`
6. Implement `OperationExecutor` — execute the HTTP call via `RestClient`
7. Call `ToolCallbackProvider.from(callbacks)` in the builder — no separate class needed
8. Implement `OpenApiToolBundle` — the public builder that wires everything together
9. Write tests against the Petstore spec (`https://petstore3.swagger.io/api/v3/openapi.json`)

---

## References

### Spring AI

| Resource | URL |
|---|---|
| Tool Calling reference docs | https://docs.spring.io/spring-ai/reference/api/tools.html |
| `ToolCallback` Javadoc | https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tool/ToolCallback.html |
| `ToolCallbackProvider` Javadoc | https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tool/ToolCallbackProvider.html |
| `ToolDefinition` Javadoc | https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tool/definition/ToolDefinition.html |
| Spring AI source code | https://github.com/spring-projects/spring-ai |
| `ToolCallback.java` source | https://github.com/spring-projects/spring-ai/blob/main/spring-ai-core/src/main/java/org/springframework/ai/tool/ToolCallback.java |
| `ToolCallbackProvider.java` source | https://github.com/spring-projects/spring-ai/blob/main/spring-ai-core/src/main/java/org/springframework/ai/tool/ToolCallbackProvider.java |


### Swagger Parser

| Resource | URL |
|---|---|
| GitHub repository | https://github.com/swagger-api/swagger-parser |
| Maven Central | https://central.sonatype.com/artifact/io.swagger.parser.v3/swagger-parser |

### Test fixture

| Resource | URL |
|---|---|
| Petstore OpenAPI 3 spec | https://petstore3.swagger.io/api/v3/openapi.json |