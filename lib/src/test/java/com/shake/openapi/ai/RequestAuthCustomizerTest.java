package com.shake.openapi.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class RequestAuthCustomizerTest {

    @Test
    void bearerSetsAuthorizationHeader() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost"));

        var result = RequestAuthCustomizer.bearer(() -> "secret").apply(request);

        assertThat(result.getHeaders().getFirst("Authorization")).isEqualTo("Bearer secret");
    }

    @Test
    void apiKeySetsNamedHeader() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost"));

        var result = RequestAuthCustomizer.apiKey("X-Api-Key", () -> "key-123").apply(request);

        assertThat(result.getHeaders().getFirst("X-Api-Key")).isEqualTo("key-123");
    }

    @Test
    void apiKeyQueryAppendsQueryParameter() throws Exception {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost/pets?status=available"));

        var result = RequestAuthCustomizer.apiKeyQuery("api_key", () -> "secret").apply(request);

        assertThat(result.getURI()).hasToString("http://localhost/pets?status=available&api_key=secret");
    }

    @Test
    void credentialIsResolvedAtRequestTime() throws Exception {
        var token = new String[]{"first"};
        var customizer = RequestAuthCustomizer.bearer(() -> token[0]);

        var first = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost"));
        customizer.apply(first);
        token[0] = "second";
        var second = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost"));
        customizer.apply(second);

        assertThat(first.getHeaders().getFirst("Authorization")).isEqualTo("Bearer first");
        assertThat(second.getHeaders().getFirst("Authorization")).isEqualTo("Bearer second");
    }
}
