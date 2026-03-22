package com.example.recommendation_service.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                                          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                                          .responseTimeout(Duration.ofMillis(3500));

        return WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    public WebClient.Builder vertexWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                                          .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000)
                                          .responseTimeout(Duration.ofMillis(5500));

        return WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}