package com.shake.openapi.ai.parser;

import com.shake.openapi.ai.model.OpenApiOperation;
import com.shake.openapi.ai.model.ParameterLocation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void overlayOverridesSummaryAndParameterDescription() {
        var operations = parser.parse("classpath:petstore.yaml", "classpath:overlay.yaml");
        var getPet = operations.stream()
                .filter(op -> op.operationId().equals("getPetById"))
                .findFirst()
                .orElseThrow();

        assertThat(getPet.summary()).isEqualTo("Fetch a single pet record by its numeric identifier");
        assertThat(getPet.inputSchema()).contains("The pet's numeric ID, not its name.");
    }

    @Test
    void overlayLeavesOtherOperationsUntouched() {
        var operations = parser.parse("classpath:petstore.yaml", "classpath:overlay.yaml");
        var addPet = operations.stream()
                .filter(op -> op.operationId().equals("addPet"))
                .findFirst()
                .orElseThrow();

        assertThat(addPet.summary()).isEqualTo("Add a new pet");
    }

    @Test
    void overlayReferencingUnknownPathThrows() {
        var overlayLocation = writeOverlay("""
                paths:
                  /pet/unknown:
                    get:
                      summary: nope
                """);

        assertThatThrownBy(() -> parser.parse("classpath:petstore.yaml", overlayLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/pet/unknown");
    }

    @Test
    void overlayReferencingUnknownParameterThrows() {
        var overlayLocation = writeOverlay("""
                paths:
                  /pet/{petId}:
                    get:
                      parameters:
                        - name: doesNotExist
                          in: query
                          description: nope
                """);

        assertThatThrownBy(() -> parser.parse("classpath:petstore.yaml", overlayLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doesNotExist");
    }

    private String writeOverlay(String contents) {
        try {
            var file = File.createTempFile("overlay", ".yaml");
            file.deleteOnExit();
            Files.writeString(file.toPath(), contents);
            return "file:" + file.getAbsolutePath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private OpenApiOperation operation(String operationId) {
        return parser.parse("classpath:petstore.yaml").stream()
                .filter(op -> op.operationId().equals(operationId))
                .findFirst()
                .orElseThrow();
    }
}
