package com.shake.openapi.ai.parser;

import com.shake.openapi.ai.model.OpenApiOperation;
import com.shake.openapi.ai.model.ParameterLocation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSpecParserTest {

    private final OpenApiSpecParser parser = new OpenApiSpecParser();

    @Test
    void parsesAllOperationsFromClasspath() {
        var operations = parser.parse("classpath:petstore.yaml");

        assertThat(operations)
                .extracting(OpenApiOperation::operationId)
                .containsExactlyInAnyOrder("getPetById", "findPetsByStatus", "addPet");
    }

    @Test
    void mapsMethodPathAndSummary() {
        var getPet = operation("getPetById");

        assertThat(getPet.httpMethod()).isEqualTo(HttpMethod.GET);
        assertThat(getPet.pathTemplate()).isEqualTo("/pet/{petId}");
        assertThat(getPet.summary()).isEqualTo("Find pet by ID");
    }

    @Test
    void capturesPathAndQueryParameterRouting() {
        var petId = operation("getPetById").parameters().getFirst();
        assertThat(petId.name()).isEqualTo("petId");
        assertThat(petId.in()).isEqualTo(ParameterLocation.PATH);
        assertThat(petId.required()).isTrue();

        var status = operation("findPetsByStatus").parameters().getFirst();
        assertThat(status.name()).isEqualTo("status");
        assertThat(status.in()).isEqualTo(ParameterLocation.QUERY);
        assertThat(status.required()).isFalse();
    }

    @Test
    void bodyOnlyOperationHasNoParameters() {
        assertThat(operation("addPet").parameters()).isEmpty();
    }

    @Test
    void buildsInputSchemaForPathParameter() {
        var schema = operation("getPetById").inputSchema();

        assertThat(schema)
                .contains("\"type\":\"object\"")
                .contains("\"petId\"")
                .contains("\"format\":\"int64\"")
                .contains("\"required\":[\"petId\"]");
    }

    @Test
    void flattensRequestBodyPropertiesIntoInputSchema() {
        var schema = operation("addPet").inputSchema();

        assertThat(schema)
                .contains("\"name\"")
                .contains("\"status\"")
                .contains("\"required\":[\"name\"]")
                .doesNotContain("\"body\"");
    }

    private OpenApiOperation operation(String operationId) {
        return parser.parse("classpath:petstore.yaml").stream()
                .filter(op -> op.operationId().equals(operationId))
                .findFirst()
                .orElseThrow();
    }
}
