package com.shake.openapi.ai.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterSchemaBuilderTest {

    private final JsonMapper mapper = new JsonMapper();
    private final ObjectMapper schemaMapper = Json.mapper();

    @Test
    void emptyOperationProducesEmptyObjectSchema() {
        var root = schemaFor(List.of(), null, false);

        assertThat(root.get("type").asString()).isEqualTo("object");
        assertThat(root.get("properties").isEmpty()).isTrue();
        assertThat(root.has("required")).isFalse();
    }

    @Test
    void producesExpectedJsonString() {
        var petId = new Parameter()
                .name("petId").in("path").required(true)
                .schema(new IntegerSchema().format("int64"));

        var json = ParameterSchemaBuilder.build(List.of(petId), null, false, schemaMapper);

        System.out.println(json);
        assertThat(json).isEqualTo(
                "{\"type\":\"object\",\"properties\":{\"petId\":{\"type\":\"integer\",\"format\":\"int64\"}},\"required\":[\"petId\"]}");
    }

    @Test
    void mapsPathParameterTypeFormatAndRequired() {
        var petId = new Parameter()
                .name("petId").in("path").required(true)
                .schema(new IntegerSchema().format("int64"));

        var root = schemaFor(List.of(petId), null, false);

        var property = root.get("properties").get("petId");
        assertThat(property.get("type").asString()).isEqualTo("integer");
        assertThat(property.get("format").asString()).isEqualTo("int64");
        assertThat(requiredNames(root)).containsExactly("petId");
    }

    @Test
    void optionalQueryParameterIsNotRequired() {
        var status = new Parameter()
                .name("status").in("query").required(false)
                .schema(new StringSchema());

        var root = schemaFor(List.of(status), null, false);

        assertThat(root.get("properties").has("status")).isTrue();
        assertThat(root.has("required")).isFalse();
    }

    @Test
    void preservesArrayItemsAndEnum() {
        var items = new StringSchema();
        items.setEnum(List.of("available", "pending", "sold"));
        var status = new Parameter()
                .name("status").in("query")
                .schema(new ArraySchema().items(items));

        var root = schemaFor(List.of(status), null, false);

        var property = root.get("properties").get("status");
        assertThat(property.get("type").asString()).isEqualTo("array");
        assertThat(property.get("items").get("type").asString()).isEqualTo("string");
        assertThat(enumValues(property.get("items"))).containsExactly("available", "pending", "sold");
    }

    @Test
    void mergesParameterDescriptionIntoSchema() {
        var current = new Parameter()
                .name("current").in("query").required(true)
                .description("Comma-separated list of current-weather variables")
                .schema(new StringSchema());

        var root = schemaFor(List.of(current), null, false);

        assertThat(root.get("properties").get("current").get("description").asString())
                .isEqualTo("Comma-separated list of current-weather variables");
    }

    @Test
    void schemaLevelDescriptionWinsOverParameterDescription() {
        var current = new Parameter()
                .name("current").in("query")
                .description("parameter description")
                .schema(new StringSchema().description("schema description"));

        var root = schemaFor(List.of(current), null, false);

        assertThat(root.get("properties").get("current").get("description").asString())
                .isEqualTo("schema description");
    }

    @Test
    void flattensRequiredRequestBodyPropertiesToTopLevel() {
        var body = objectBody();

        var root = schemaFor(List.of(), body, true);

        assertThat(root.get("properties").has("name")).isTrue();
        assertThat(root.get("properties").has("status")).isTrue();
        assertThat(root.get("properties").get("name").get("description").asString()).isEqualTo("Pet name");
        assertThat(requiredNames(root)).containsExactly("name");
        assertThat(root.get("properties").has("body")).isFalse();
    }

    @Test
    void optionalRequestBodyDoesNotContributeRequiredFields() {
        var root = schemaFor(List.of(), objectBody(), false);

        assertThat(root.get("properties").has("name")).isTrue();
        assertThat(root.has("required")).isFalse();
    }

    @Test
    void nonObjectRequestBodyExposedUnderBodyProperty() {
        var root = schemaFor(List.of(), new StringSchema(), true);

        var body = root.get("properties").get("body");
        assertThat(body.get("type").asString()).isEqualTo("string");
        assertThat(requiredNames(root)).containsExactly("body");
    }

    @Test
    void mergesParametersAndBodyProperties() {
        var petId = new Parameter()
                .name("petId").in("path").required(true)
                .schema(new IntegerSchema());

        var root = schemaFor(List.of(petId), objectBody(), true);

        assertThat(root.get("properties").propertyStream().map(Map.Entry::getKey))
                .containsExactlyInAnyOrder("petId", "name", "status");
        assertThat(requiredNames(root)).containsExactlyInAnyOrder("petId", "name");
    }

    @Test
    void preservesComposedAllOfSchemas() {
        var partA = new ObjectSchema();
        partA.setProperties(Map.of("a", new StringSchema()));
        var partB = new ObjectSchema();
        partB.setProperties(Map.of("b", new IntegerSchema()));
        Schema<?> petType = new Schema<>().addAllOfItem(partA).addAllOfItem(partB);

        var body = new ObjectSchema();
        body.setProperties(Map.of("petType", petType));

        var root = schemaFor(List.of(), body, false);

        assertThat(root.get("properties").get("petType").get("allOf").size()).isEqualTo(2);
    }

    private ObjectSchema objectBody() {
        var body = new ObjectSchema();
        body.setProperties(Map.of(
                "name", new StringSchema().description("Pet name"),
                "status", new StringSchema()));
        body.setRequired(List.of("name"));
        return body;
    }

    private JsonNode schemaFor(List<Parameter> parameters, Schema<?> requestBodySchema, boolean requestBodyRequired) {
        return mapper.readTree(
                ParameterSchemaBuilder.build(parameters, requestBodySchema, requestBodyRequired, schemaMapper));
    }

    private List<String> requiredNames(JsonNode root) {
        return root.get("required").valueStream().map(JsonNode::asString).toList();
    }

    private List<String> enumValues(JsonNode schemaNode) {
        return schemaNode.get("enum").valueStream().map(JsonNode::asString).toList();
    }
}
