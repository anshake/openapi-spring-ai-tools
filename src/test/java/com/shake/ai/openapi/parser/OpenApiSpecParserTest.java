package com.shake.ai.openapi.parser;

import com.shake.ai.openapi.model.OpenApiOperation;
import com.shake.ai.openapi.model.ParameterLocation;
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
        var petId = operation("getPetById").parameters().get(0);
        assertThat(petId.name()).isEqualTo("petId");
        assertThat(petId.in()).isEqualTo(ParameterLocation.PATH);
        assertThat(petId.required()).isTrue();

        var status = operation("findPetsByStatus").parameters().get(0);
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
    void capturesPathLevelSharedParameter() {
        var operations = parser.parse("classpath:path-level-parameter.yaml");
        var getWidget = operations.stream()
                .filter(op -> op.operationId().equals("getWidget"))
                .findFirst()
                .orElseThrow();

        var widgetId = getWidget.parameters().get(0);
        assertThat(widgetId.name()).isEqualTo("widgetId");
        assertThat(widgetId.in()).isEqualTo(ParameterLocation.PATH);
        assertThat(widgetId.required()).isTrue();
    }

    @Test
    void overlayOverridesPathLevelSharedParameterDescription() {
        var overlayLocation = writeOverlay("""
                paths:
                  /widgets/{widgetId}:
                    get:
                      parameters:
                        - name: widgetId
                          in: path
                          description: The widget's numeric ID.
                """);

        var operations = parser.parse("classpath:path-level-parameter.yaml", overlayLocation);
        var getWidget = operations.stream()
                .filter(op -> op.operationId().equals("getWidget"))
                .findFirst()
                .orElseThrow();

        assertThat(getWidget.inputSchema()).contains("The widget's numeric ID.");
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
    void overlayMissingTopLevelPathsFieldThrows() {
        var overlayLocation = writeOverlay("""
                path:
                  /pet/{petId}:
                    get:
                      summary: nope
                """);

        assertThatThrownBy(() -> parser.parse("classpath:petstore.yaml", overlayLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paths")
                .hasMessageContaining(overlayLocation);
    }

    @Test
    void overlayReferencingUnknownMethodThrows() {
        var overlayLocation = writeOverlay("""
                paths:
                  /pet/{petId}:
                    gett:
                      summary: nope
                """);

        assertThatThrownBy(() -> parser.parse("classpath:petstore.yaml", overlayLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gett")
                .hasMessageContaining("/pet/{petId}");
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
    void overlayParameterMissingNameThrows() {
        var overlayLocation = writeOverlay("""
                paths:
                  /pet/{petId}:
                    get:
                      parameters:
                        - in: path
                          description: nope
                """);

        assertThatThrownBy(() -> parser.parse("classpath:petstore.yaml", overlayLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field 'name'");
    }

    @Test
    void overlayParameterMissingInThrows() {
        var overlayLocation = writeOverlay("""
                paths:
                  /pet/{petId}:
                    get:
                      parameters:
                        - name: petId
                          description: nope
                """);

        assertThatThrownBy(() -> parser.parse("classpath:petstore.yaml", overlayLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field 'in'");
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
