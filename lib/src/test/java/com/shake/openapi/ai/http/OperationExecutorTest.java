package com.shake.openapi.ai.http;

import com.shake.openapi.ai.model.OpenApiOperation;
import com.shake.openapi.ai.model.OpenApiParameter;
import com.shake.openapi.ai.model.ParameterLocation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OperationExecutorTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OperationExecutor executor = new OperationExecutor(builder.build());

    @Test
    void substitutesPathParameter() {
        server.expect(requestTo("http://localhost/pet/42"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        var operation = new OpenApiOperation(
                "getPetById", "Find pet by ID", GET, "/pet/{petId}",
                List.of(new OpenApiParameter("petId", ParameterLocation.PATH, true)), "{}");

        var result = executor.execute(operation, "{\"petId\":42}");

        assertThat(result).contains("42");
        server.verify();
    }

    @Test
    void appendsArrayQueryParameter() {
        server.expect(requestTo("http://localhost/pet/findByStatus?status=available&status=pending"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var operation = new OpenApiOperation(
                "findPetsByStatus", "Finds pets by status", GET, "/pet/findByStatus",
                List.of(new OpenApiParameter("status", ParameterLocation.QUERY, false)), "{}");

        executor.execute(operation, "{\"status\":[\"available\",\"pending\"]}");

        server.verify();
    }

    @Test
    void sendsRemainingFieldsAsJsonBodyForPost() {
        server.expect(requestTo("http://localhost/pet"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Rex"))
                .andExpect(jsonPath("$.status").value("available"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        var operation = new OpenApiOperation(
                "addPet", "Add a new pet", HttpMethod.POST, "/pet", List.of(), "{}");

        executor.execute(operation, "{\"name\":\"Rex\",\"status\":\"available\"}");

        server.verify();
    }
}
