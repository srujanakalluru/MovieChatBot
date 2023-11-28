package com.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TransliterateConfig {

  @Bean
  public RestTemplate transliterateRestTemplate(
      @Value("${transliterate.connect-timeout-ms:3000}") int connectTimeoutMs,
      @Value("${transliterate.read-timeout-ms:5000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    return new RestTemplate(factory);
  }
}
