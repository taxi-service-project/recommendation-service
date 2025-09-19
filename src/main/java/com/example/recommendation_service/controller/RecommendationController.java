package com.example.recommendation_service.controller;

import com.example.recommendation_service.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public Mono<String> getRecommendation(@RequestParam double lon, @RequestParam double lat, @RequestParam String city) {
        return recommendationService.getBestLocationRecommendation(lon, lat, city);
    }
}