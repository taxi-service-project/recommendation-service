package com.example.recommendation_service.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class HotspotLoader {
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String HOTSPOTS_KEY = "demand_hotspots";

    @PostConstruct
    public void loadHotspots() {
        try {
            // 기존 키 제거
            reactiveRedisTemplate.delete(HOTSPOTS_KEY).block();

            // CSV 읽기
            List<Point> hotspots = new ArrayList<>();
            ClassPathResource resource = new ClassPathResource("hotspots.csv");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                reader.readLine(); // 헤더 스킵
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    // CSV: latitude,longitude
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    hotspots.add(new Point(lon, lat)); // Point(x=lon, y=lat)
                }
            }

            // 멤버 이름은 "spot1", "spot2", ... 처럼 고유 ID로 저장
            AtomicInteger idx = new AtomicInteger(1);
            Flux.fromIterable(hotspots)
                .flatMap(point -> {
                    String memberName = "spot" + idx.getAndIncrement();
                    return reactiveRedisTemplate.opsForGeo()
                                                .add(HOTSPOTS_KEY, point, memberName);
                })
                .doOnComplete(() -> log.info("{}개의 핫스팟을 Redis에 로드 완료.", hotspots.size()))
                .blockLast(); // PostConstruct 에서 완료를 기다립니다.

        } catch (Exception e) {
            log.error("hotspots.csv 로드 실패. src/main/resources 폴더에 파일이 있는지 확인해주세요.", e);
        }
    }
}
