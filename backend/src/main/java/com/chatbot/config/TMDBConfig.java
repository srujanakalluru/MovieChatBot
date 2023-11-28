package com.chatbot.config;

import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TMDBConfig {
  @Value("${tmdb.api.key}")
  String apiKey;

  @Value("${tmdb.api.token}")
  String apiToken;

  @Bean
  public RestTemplate tmdbRestTemplate(
      @Value("${tmdb.connect-timeout-ms:5000}") int connectTimeoutMs,
      @Value("${tmdb.read-timeout-ms:15000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    RestTemplate restTemplate = new RestTemplate(factory);
    restTemplate
        .getInterceptors()
        .add(
            (request, body, execution) -> {
              request.getHeaders().add("Authorization", "Bearer " + apiToken);
              request.getHeaders().add("apiKey", apiKey);
              request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
              request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
              return execution.execute(request, body);
            });
    return restTemplate;
  }
}
