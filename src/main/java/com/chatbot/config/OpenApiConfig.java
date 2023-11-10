package com.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OpenApiConfig {
  @Value("${openAi.personal.token}")
  private String personalOpenAiToken;

  @Value("${openAi.model}")
  private String openAiModel;

  @Value("${openAi.url.endpoint}")
  private String openAiEndpoint;

  @Value("${openAi.temperature}")
  private Double temperature;

  @Value("${openAi.maxTokens}")
  private Integer maxTokens;

  @Bean
  public String personalOpenAiToken() {
    return personalOpenAiToken;
  }

  @Bean
  public String openAiModel() {
    return openAiModel;
  }

  @Bean
  public String openAiEndpoint() {
    return openAiEndpoint;
  }

  @Bean
  public Double openAiTemperature() {
    return temperature;
  }

  @Bean
  public Integer openAiMaxTokens() {
    return maxTokens;
  }

  @Bean
  public RestTemplate openAIRestTemplate() {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate
        .getInterceptors()
        .add(
            (request, body, execution) -> {
              request.getHeaders().add("Authorization", "Bearer " + personalOpenAiToken);
              request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
              return execution.execute(request, body);
            });
    return restTemplate;
  }
}
