package com.example.recommendation_service.service;

import com.example.recommendation_service.client.NaverMapsClient;
import com.example.recommendation_service.client.VertexAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {
    private final VertexAiClient vertexAiClient;
    private final NaverMapsClient naverMapsClient;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String HOTSPOTS_KEY = "demand_hotspots";

    private record PredictedLocation(Point location, double score) {
    }

    public Mono<String> getBestLocationRecommendation(double lon, double lat) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue() - 1;
        String city = " ";

        Point center = new Point(lon, lat);
        Distance radius = new Distance(5, Metrics.KILOMETERS);

        Flux<Point> nearbyHotspots = reactiveRedisTemplate.opsForGeo()
                                                          .radius(
                                                                  HOTSPOTS_KEY,
                                                                  new Circle(center, radius),
                                                                  RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates()
                                                          )
                                                          .filter(geoResult -> geoResult != null && geoResult.getContent() != null && geoResult.getContent().getPoint() != null)
                                                          .map(geoResult -> geoResult.getContent().getPoint());

        // nearbyHotspots 스트림을 예측 점수로 변환
        Flux<PredictedLocation> predictions = nearbyHotspots
                .flatMap(hotspot ->
                        vertexAiClient.predict(hotspot.getX(), hotspot.getY(), hour, dayOfWeek, city)
                                      .doOnNext(score -> log.info("Vertex AI 예측 점수: {}", score))
                                      .map(score -> new PredictedLocation(hotspot, score))
                                      .doOnError(e -> log.error("Vertex AI 예측 중 오류 발생", e))
                                      .onErrorResume(e -> Mono.empty())
                )
                .doOnNext(prediction -> log.info("예측된 핫스팟: {}", prediction))
                .doOnError(e -> log.error("예측 스트림에서 오류 발생: {}", e.getMessage()));

        // 가장 높은 점수의 핫스팟을 찾아 주소로 변환
        return predictions
                .reduce((loc1, loc2) -> loc1.score > loc2.score ? loc1 : loc2)
                .doOnSuccess(best -> log.info("최고 핫스팟 선정: {}", best))
                .flatMap(best -> naverMapsClient.reverseGeocode(best.location.getX(), best.location.getY()))
                .doOnNext(locationName -> log.info("주소 변환 결과: {}", locationName))
                .doOnError(e -> log.error("최종 주소 변환 중 오류 발생: {}", e.getMessage()))
                .map(locationName -> String.format("10분 후 %s 인근의 수요가 가장 높을 것으로 예상됩니다. 이동을 추천합니다.", locationName))
                .defaultIfEmpty("주변에 추천할 만한 핫스팟이 없습니다.");
    }
}
