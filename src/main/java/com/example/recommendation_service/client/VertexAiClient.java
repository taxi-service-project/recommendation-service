package com.example.recommendation_service.client;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;

@Component
@Slf4j
public class VertexAiClient {
    private final String endpointId;
    private final String projectId;
    private final String location;
    private final WebClient webClient;

    public VertexAiClient(@org.springframework.beans.factory.annotation.Value("${gcp.project-id}") String projectId,
                          @org.springframework.beans.factory.annotation.Value("${gcp.location}") String location,
                          @org.springframework.beans.factory.annotation.Value("${gcp.vertex-ai.endpoint-id}") String endpointId,
                          WebClient.Builder webClientBuilder) {
        this.projectId = projectId;
        this.location = location;
        this.endpointId = endpointId;
        this.webClient = webClientBuilder.build();
        log.info("VertexAiClient (REST) created with projectId: {}, location: {}, endpointId: {}",
                this.projectId, this.location, this.endpointId);
    }

    // 인증 토큰을 비동기적으로 가져오는 메소드
    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                                                             .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
            AccessToken accessToken = credentials.refreshAccessToken();
            return accessToken.getTokenValue();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Double> predict(double longitude, double latitude, int hour, int dayOfWeek, String city) {
        // 1. API 요청 URL 생성
        String apiUrl = String.format("https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/endpoints/%s:predict",
                location, projectId, location, endpointId);

        // 2. 요청 Body 생성
        String requestBody = String.format(
                "{\"instances\": [{\"city\": \"%s\", \"latitude\": \"%s\", \"longitude\": \"%s\", \"hour_of_day\": \"%s\", \"day_of_week\": \"%s\"}]}",
                city,
                String.valueOf(latitude),
                String.valueOf(longitude),
                String.valueOf(hour),
                String.valueOf(dayOfWeek)
        );

        // 3. 인증 토큰을 가져와서 API 호출
        return getAccessToken().flatMap(token ->
                webClient.post()
                         .uri(apiUrl)
                         .header("Authorization", "Bearer " + token)
                         .header("Content-Type", "application/json; charset=utf-8")
                         .bodyValue(requestBody)
                         .retrieve()
                         .bodyToMono(JsonNode.class)
                         .map(responseNode -> {
                             // 4. 응답에서 예측 결과 파싱
                             if (responseNode.has("predictions") && responseNode.get("predictions").isArray() && !responseNode.get("predictions").isEmpty()) {
                                 JsonNode prediction = responseNode.get("predictions").get(0);
                                 if (prediction.has("value")) {
                                     return prediction.get("value").asDouble();
                                 }
                             }
                             log.warn("Vertex AI 예측 결과가 없거나 형식이 올바르지 않습니다: {}", responseNode);
                             return 0.0;
                         })
        );
    }
}