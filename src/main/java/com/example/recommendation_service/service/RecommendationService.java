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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private static final String HOTSPOTS_KEY = "demand_hotspots";

    private final VertexAiClient vertexAiClient;
    private final NaverMapsClient naverMapsClient;

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate; // 영속성 레디스

    private record PredictedLocation(Point location, double score) {
    }

    public Mono<String> getBestLocationRecommendation(double lon, double lat) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        int dayOfWeek = now.getDayOfWeek().getValue() - 1;
        String city = " ";
        int timeSlot = (hour * 4) + (minute / 15);

        Point center = new Point(lon, lat);
        Distance radius = new Distance(7, Metrics.KILOMETERS);

        // 1. Redis에서 반경 7km 내 핫스팟 조회 후 하나의 List로 수집
        Mono<List<Point>> hotspotsMono = reactiveRedisTemplate.opsForGeo()
                                                              .radius(
                                                                      HOTSPOTS_KEY,
                                                                      new Circle(center, radius),
                                                                      RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().sortAscending()
                                                              )
                                                              .filter(geoResult -> geoResult != null && geoResult.getContent() != null && geoResult.getContent().getPoint() != null)
                                                              .map(geoResult -> geoResult.getContent().getPoint())
                                                              .collectList();

        // 2. 수집된 핫스팟 리스트로 Vertex AI 단 1회 Bulk 호출
        Mono<PredictedLocation> bestPredictionMono = hotspotsMono.flatMap(hotspots -> {
            if (hotspots.isEmpty()) {
                log.info("반경 내 핫스팟이 존재하지 않습니다.");
                return Mono.empty();
            }

            // AI 모델에 전달할 Bulk Payload(List<Map>) 조립
            List<Map<String, Object>> instances = hotspots.stream().map(hotspot -> {
                Map<String, Object> instance = new HashMap<>();
                instance.put("city", city);
                instance.put("latitude", String.valueOf(hotspot.getY()));
                instance.put("longitude", String.valueOf(hotspot.getX()));
                instance.put("time_slot", String.valueOf(timeSlot));
                instance.put("day_of_week", String.valueOf(dayOfWeek));
                return instance;
            }).toList();

            // 단 1회의 외부 API 호출
            return vertexAiClient.predictBulk(instances)
                                 .map(scores -> {
                                     // 리턴받은 점수 리스트 중 최고 점수와 해당 핫스팟 매핑
                                     double maxScore = -1.0;
                                     int maxIndex = -1;

                                     for (int i = 0; i < scores.size(); i++) {
                                         if (scores.get(i) > maxScore) {
                                             maxScore = scores.get(i);
                                             maxIndex = i;
                                         }
                                     }

                                     if (maxIndex == -1) {
                                         return null;
                                     }

                                     PredictedLocation best = new PredictedLocation(hotspots.get(maxIndex), maxScore);
                                     log.info("Vertex AI Bulk 예측 완료. 최고 핫스팟: {}", best);
                                     return best;
                                 });
        });

        // 3. 가장 높은 점수의 핫스팟을 주소로 변환하여 응답
        return bestPredictionMono
                .flatMap(best -> {
                    if (best == null) return Mono.empty();
                    return naverMapsClient.reverseGeocode(best.location().getX(), best.location().getY());
                })
                .doOnNext(locationName -> log.info("주소 변환 결과: {}", locationName))
                .doOnError(e -> log.error("최종 주소 변환 중 오류 발생: {}", e.getMessage()))
                .map(locationName -> String.format("약 15분뒤 %s 인근의 수요가 가장 높을 것으로 예상됩니다. 이동을 추천합니다.", locationName))
                .defaultIfEmpty("주변에 추천할 만한 핫스팟이 없습니다.");
    }
}