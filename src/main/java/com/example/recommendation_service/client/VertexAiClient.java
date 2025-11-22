package com.example.recommendation_service.client;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class VertexAiClient {
    private final String endpointId;
    private final String projectId;
    private final String location;
    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public VertexAiClient(@Value("${gcp.project-id}") String projectId,
                          @Value("${gcp.location}") String location,
                          @Value("${gcp.vertex-ai.endpoint-id}") String endpointId,
                          WebClient.Builder webClientBuilder,
                          ReactiveCircuitBreakerFactory cbFactory) {
        this.projectId = projectId;
        this.location = location;
        this.endpointId = endpointId;
        this.webClient = webClientBuilder.build();
        this.circuitBreaker = cbFactory.create("vertex-service");

        log.info("VertexAiClient created. Project: {}, Location: {}, Endpoint: {}",
                projectId, location, endpointId);
    }

    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                                                             .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
            AccessToken accessToken = credentials.refreshAccessToken();
            return accessToken.getTokenValue();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Double> predict(double longitude, double latitude, int timeSlot, int dayOfWeek, String city) {
        String apiUrl = String.format("https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/endpoints/%s:predict",
                location, projectId, location, endpointId);

        Map<String, Object> instance = new HashMap<>();
        instance.put("city", city);
        instance.put("latitude", String.valueOf(latitude));
        instance.put("longitude", String.valueOf(longitude));
        instance.put("time_slot", String.valueOf(timeSlot));
        instance.put("day_of_week", String.valueOf(dayOfWeek));

        Map<String, Object> requestBody = Map.of("instances", List.of(instance));

        Mono<Double> apiCall = getAccessToken().flatMap(token ->
                webClient.post()
                         .uri(apiUrl)
                         .header("Authorization", "Bearer " + token)
                         .bodyValue(requestBody)
                         .retrieve()
                         .bodyToMono(JsonNode.class)
                         .map(responseNode -> {
                             if (responseNode.has("predictions") &&
                                     !responseNode.get("predictions").isEmpty()) {
                                 return responseNode.get("predictions").get(0).get("value").asDouble();
                             }
                             log.warn("Vertex AI 예측값 없음: {}", responseNode);
                             return 0.0;
                         })
        );

        return circuitBreaker.run(apiCall, throwable -> {
            log.warn("Vertex AI 호출 실패 또는 서킷 오픈 (Fallback: 0.0 반환). Error: {}", throwable.getMessage());
            return Mono.just(0.0);
        });
    }
}