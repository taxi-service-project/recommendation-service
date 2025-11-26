package com.example.recommendation_service.service;

import com.example.recommendation_service.client.NaverMapsClient;
import com.example.recommendation_service.client.VertexAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveGeoOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private RecommendationService recommendationService;

    @Mock
    private VertexAiClient vertexAiClient;
    @Mock
    private NaverMapsClient naverMapsClient;
    @Mock
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    @Mock
    private ReactiveGeoOperations<String, String> reactiveGeoOperations;

    @BeforeEach
    void setUp() {
        // GeoOperations Mocking 연결
        given(reactiveRedisTemplate.opsForGeo()).willReturn(reactiveGeoOperations);

        recommendationService = new RecommendationService(vertexAiClient, naverMapsClient, reactiveRedisTemplate);
    }

    @Test
    @DisplayName("정상 흐름: 주변 핫스팟 발견 -> 예측 점수 계산 -> 최고 점수 선정 -> 주소 변환 -> 추천 메시지 반환")
    void getBestLocationRecommendation_Success() {
        // Given
        double lon = 127.0;
        double lat = 37.5;

        // 1. Redis GeoResult Mocking
        Point point1 = new Point(127.01, 37.51); // 후보 1
        Point point2 = new Point(127.02, 37.52); // 후보 2

        GeoResult<RedisGeoCommands.GeoLocation<String>> result1 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("hotspot1", point1), new Distance(1.0, Metrics.KILOMETERS));
        GeoResult<RedisGeoCommands.GeoLocation<String>> result2 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("hotspot2", point2), new Distance(2.0, Metrics.KILOMETERS));

        given(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .willReturn(Flux.just(result1, result2));

        // 2. Vertex AI Prediction Mocking
        // point1은 0.5점, point2는 0.9점 (point2가 당선되어야 함)
        given(vertexAiClient.predict(eq(127.01), eq(37.51), anyInt(), anyInt(), anyString()))
                .willReturn(Mono.just(0.5));
        given(vertexAiClient.predict(eq(127.02), eq(37.52), anyInt(), anyInt(), anyString()))
                .willReturn(Mono.just(0.9));

        // 3. Naver Maps Reverse Geocoding Mocking (point2 좌표로 호출됨)
        given(naverMapsClient.reverseGeocode(eq(127.02), eq(37.52)))
                .willReturn(Mono.just("서울특별시 강남구 역삼동"));

        // When
        Mono<String> result = recommendationService.getBestLocationRecommendation(lon, lat);

        // Then
        StepVerifier.create(result)
                    .expectNextMatches(message ->
                            message.contains("서울특별시 강남구 역삼동") &&
                                    message.contains("약 15분뒤"))
                    .verifyComplete();
    }

    @Test
    @DisplayName("주변에 핫스팟이 아예 없는 경우 기본 메시지를 반환한다")
    void getBestLocationRecommendation_NoHotspots() {
        // Given
        double lon = 127.0;
        double lat = 37.5;

        // Redis 결과 없음
        given(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .willReturn(Flux.empty());

        // When
        Mono<String> result = recommendationService.getBestLocationRecommendation(lon, lat);

        // Then
        StepVerifier.create(result)
                    .expectNext("주변에 추천할 만한 핫스팟이 없습니다.")
                    .verifyComplete();

        // AI 예측이나 지도 API는 호출되지 않아야 함
        then(vertexAiClient).should(never()).predict(anyDouble(), anyDouble(), anyInt(), anyInt(), anyString());
        then(naverMapsClient).should(never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Vertex AI 예측 중 일부가 실패해도, 성공한 것 중 최고점을 찾는다")
    void getBestLocationRecommendation_PartialAiFailure() {
        // Given
        Point point1 = new Point(127.01, 37.51); // AI 실패할 후보
        Point point2 = new Point(127.02, 37.52); // AI 성공할 후보

        GeoResult<RedisGeoCommands.GeoLocation<String>> result1 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("fail-spot", point1), new Distance(1.0, Metrics.KILOMETERS));
        GeoResult<RedisGeoCommands.GeoLocation<String>> result2 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("success-spot", point2), new Distance(2.0, Metrics.KILOMETERS));

        given(reactiveGeoOperations.radius(anyString(), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .willReturn(Flux.just(result1, result2));

        // 1번 후보는 에러 -> onErrorResume에 의해 Mono.empty() -> 스트림에서 제외됨
        given(vertexAiClient.predict(eq(127.01), eq(37.51), anyInt(), anyInt(), anyString()))
                .willReturn(Mono.error(new RuntimeException("AI Error")));

        // 2번 후보는 성공
        given(vertexAiClient.predict(eq(127.02), eq(37.52), anyInt(), anyInt(), anyString()))
                .willReturn(Mono.just(0.8));

        // Naver Maps (2번 후보 좌표)
        given(naverMapsClient.reverseGeocode(eq(127.02), eq(37.52)))
                .willReturn(Mono.just("성공한 위치"));

        // When
        Mono<String> result = recommendationService.getBestLocationRecommendation(127.0, 37.5);

        // Then
        StepVerifier.create(result)
                    .expectNextMatches(msg -> msg.contains("성공한 위치"))
                    .verifyComplete();
    }
}