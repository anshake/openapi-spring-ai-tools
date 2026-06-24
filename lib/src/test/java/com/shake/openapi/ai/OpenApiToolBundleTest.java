package com.shake.openapi.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiToolBundleTest {

    @Test
    void buildsOneToolCallbackPerOperation() {
        var callbacks = build().getToolCallbacks();

        assertThat(callbacks)
                .extracting(c -> c.getToolDefinition().name())
                .containsExactlyInAnyOrder("getPetById", "findPetsByStatus", "addPet");
    }

    @Test
    void toolDefinitionCarriesNameDescriptionAndInputSchema() {
        var definition = definitionOf("getPetById");

        assertThat(definition.name()).isEqualTo("getPetById");
        assertThat(definition.description()).isEqualTo("Find pet by ID");
        assertThat(definition.inputSchema()).contains("\"petId\"");
    }

    private ToolDefinition definitionOf(String toolName) {
        return Arrays.stream(build().getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .filter(d -> d.name().equals(toolName))
                .findFirst()
                .orElseThrow();
    }

    private ToolCallbackProvider build() {
        return OpenApiToolBundle.from("classpath:petstore.yaml")
                .baseUrl("http://localhost")
                .build();
    }
}
