package com.example.recommendation_service.controller;

import com.example.recommendation_service.dto.RecommendationRequest;
import com.example.recommendation_service.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public Mono<String> getRecommendation(@Valid @ModelAttribute RecommendationRequest request) {
        return recommendationService.getBestLocationRecommendation(request.lon(), request.lat());
    }
}