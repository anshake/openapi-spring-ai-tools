package com.shake.openapi.ai.http;

import com.shake.openapi.ai.RequestAuthCustomizer;
import com.shake.openapi.ai.model.OpenApiOperation;
import com.shake.openapi.ai.model.ParameterLocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Executes an operation over HTTP given the LLM's JSON input.
 *
 * <p>Routing, per the parameter metadata on {@link OpenApiOperation}:
 * <ul>
 *     <li>path params are substituted into the URL template;</li>
 *     <li>query params are appended to the query string;</li>
 *     <li>any remaining input key is treated as a body field — sent as a JSON body
 *     for POST/PUT/PATCH, or appended as a query param for GET/DELETE.</li>
 * </ul>
 *
 * <p>HTTP errors propagate as {@code RestClientResponseException} (a RuntimeException).
 */
public class OperationExecutor
{

    private static final Log log = LogFactory.getLog(OperationExecutor.class);

    private final RestClient restClient;
    private final JsonMapper json = new JsonMapper();

    public OperationExecutor(String baseUrl, RequestAuthCustomizer auth)
    {
        this(applyAuth(RestClient.builder().baseUrl(baseUrl), auth).build());
    }

    static RestClient.Builder applyAuth(RestClient.Builder builder, RequestAuthCustomizer auth)
    {
        if (auth != null)
        {
            builder.requestInterceptor((request, body, execution) ->
                                               execution.execute(auth.apply(request), body));
        }
        return builder;
    }

    OperationExecutor(RestClient restClient)
    {
        this.restClient = restClient;
    }

    public String execute(OpenApiOperation operation, String toolInput)
    {
        var input = readInput(toolInput);
        var method = operation.httpMethod();
        var sendsBody = sendsBody(method);

        var pathVariables = new HashMap<String, String>();
        var queryParams = new HashMap<String, JsonNode>();
        var routed = new HashSet<String>();

        for (var parameter : operation.parameters())
        {
            var name = parameter.name();
            if (!input.has(name))
            {
                continue;
            }
            var value = input.get(name);
            if (parameter.in() == ParameterLocation.PATH)
            {
                pathVariables.put(name, value.asString());
            }
            else
            {
                queryParams.put(name, value);
            }
            routed.add(name);
        }

        var body = json.createObjectNode();
        input.propertyStream()
             .filter(entry -> !routed.contains(entry.getKey()))
             .forEach(entry -> {
                 if (sendsBody)
                 {
                     body.set(entry.getKey(), entry.getValue());
                 }
                 else
                 {
                     queryParams.put(entry.getKey(), entry.getValue());
                 }
             });

        var request = restClient.method(method)
                                .uri(uriBuilder -> {
                                    uriBuilder.path(operation.pathTemplate());
                                    queryParams.forEach((name, value) -> appendQueryParam(uriBuilder, name, value));
                                    var uri = uriBuilder.build(pathVariables);
                                    log.debug("[" + operation.operationId() + "] Calling " + method + " " + uri);
                                    return uri;
                                });

        if (sendsBody && !body.isEmpty())
        {
            request = request.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
        }


        return request.retrieve().body(String.class);
    }

    private ObjectNode readInput(String toolInput)
    {
        if (toolInput == null || toolInput.isBlank())
        {
            return json.createObjectNode();
        }
        var node = json.readTree(toolInput);
        if (!node.isObject())
        {
            throw new IllegalArgumentException("Tool input must be a JSON object, was: " + toolInput);
        }
        return (ObjectNode) node;
    }

    private void appendQueryParam(org.springframework.web.util.UriBuilder uriBuilder, String name, JsonNode value)
    {
        if (value.isArray())
        {
            value.forEach(element -> uriBuilder.queryParam(name, element.asString()));
        }
        else
        {
            uriBuilder.queryParam(name, value.asString());
        }
    }

    private boolean sendsBody(HttpMethod method)
    {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }
}
