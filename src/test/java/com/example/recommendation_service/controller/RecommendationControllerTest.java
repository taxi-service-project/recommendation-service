package com.example.recommendation_service.controller;

import com.example.recommendation_service.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.BDDMockito.given;

@WebFluxTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    @DisplayName("유효한 위도, 경도 파라미터 요청 시 200 OK와 추천 결과를 반환한다")
    void getRecommendation_Success() {
        // Given
        double validLon = 127.123;
        double validLat = 37.567;
        String expectedResponse = "약 15분뒤 서울역 인근의 수요가 가장 높을 것으로 예상됩니다.";

        given(recommendationService.getBestLocationRecommendation(validLon, validLat))
                .willReturn(Mono.just(expectedResponse));

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/recommendations")
                             .queryParam("lon", validLon)
                             .queryParam("lat", validLat)
                             .build())
                     .exchange()
                     .expectStatus().isOk()
                     .expectBody(String.class).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("경도(lon)가 범위를 벗어나면(-180 미만) 400 Bad Request를 반환한다")
    void getRecommendation_InvalidLon_TooSmall() {
        // Given
        double invalidLon = -181.0;
        double validLat = 37.5;

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/recommendations")
                             .queryParam("lon", invalidLon)
                             .queryParam("lat", validLat)
                             .build())
                     .exchange()
                     .expectStatus().isBadRequest();
        // .expectBody(...).consumeWith(...) 로 상세 에러 메시지 검증 가능
    }

    @Test
    @DisplayName("위도(lat)가 범위를 벗어나면(90 초과) 400 Bad Request를 반환한다")
    void getRecommendation_InvalidLat_TooLarge() {
        // Given
        double validLon = 127.0;
        double invalidLat = 91.0;

        // When & Then
        webTestClient.get()
                     .uri(uriBuilder -> uriBuilder
                             .path("/api/recommendations")
                             .queryParam("lon", validLon)
                             .queryParam("lat", invalidLat)
                             .build())
                     .exchange()
                     .expectStatus().isBadRequest();
    }
}