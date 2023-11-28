package com.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

  private String endpoint;
  private String model;
  private double temperature;
  private int maxTokens;

  public String getEndpoint() { return endpoint; }
  public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }

  public double getTemperature() { return temperature; }
  public void setTemperature(double temperature) { this.temperature = temperature; }

  public int getMaxTokens() { return maxTokens; }
  public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

  @Bean
  public RestTemplate llmRestTemplate(
      @Value("${llm.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${llm.read-timeout-ms:60000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    RestTemplate restTemplate = new RestTemplate(factory);
    restTemplate.getInterceptors().add((request, body, execution) -> {
      request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return execution.execute(request, body);
    });
    return restTemplate;
  }
}
