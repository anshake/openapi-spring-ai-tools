package com.shake.openapi.ai.demo;

import com.shake.openapi.ai.OpenApiToolBundle;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Live demo: loads a trimmed Open-Meteo OpenAPI spec, exposes its forecast
 * operation as a Spring AI tool via {@link OpenApiToolBundle}, and lets Claude
 * invoke it.
 *
 * <p>Sends a single prompt at startup, prints Claude's answer, then exits. Claude
 * should pick the {@code getCurrentWeather} tool, supplying the city's coordinates
 * itself, which performs a real {@code GET /v1/forecast?latitude=...&longitude=...}
 * against the live Open-Meteo API.
 */
@SpringBootApplication
public class WeatherDemoApplication {

    private static final String PROMPT =
            "What is the current temperature in Amsterdam right now?";

    public static void main(String[] args) {
        SpringApplication.run(WeatherDemoApplication.class, args);
    }

    @Bean
    ToolCallbackProvider weatherTools() {
        return OpenApiToolBundle
                .from("classpath:open-meteo.yaml")
                .baseUrl("https://api.open-meteo.com")
                .build();
    }

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder, ToolCallbackProvider weatherTools) {
        return args -> {
            var chatClient = chatClientBuilder
                    .defaultTools(weatherTools)
                    .build();

            var answer = chatClient.prompt(PROMPT)
                    .call()
                    .content();

            System.out.println("\n=== Prompt ===\n" + PROMPT);
            System.out.println("\n=== LLM ===\n" + answer + "\n");
        };
    }
}
