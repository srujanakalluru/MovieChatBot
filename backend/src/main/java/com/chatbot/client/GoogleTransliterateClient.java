package com.chatbot.client;

import com.chatbot.service.TransliterateService;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class GoogleTransliterateClient implements TransliterateService {

  private final RestTemplate transliterateRestTemplate;
  private final ObjectMapper objectMapper;

  @Value("${transliterate.enabled:true}")
  private boolean enabled;

  @Value("${transliterate.endpoint:https://inputtools.google.com/request}")
  private String endpoint;

  public GoogleTransliterateClient(RestTemplate transliterateRestTemplate, ObjectMapper objectMapper) {
    this.transliterateRestTemplate = transliterateRestTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<String> transliterate(String text, String lang) {
    if (!enabled || text == null || text.isBlank()) {
      return List.of();
    }
    try {
      String url = UriComponentsBuilder.fromUriString(endpoint)
          .queryParam("text", text)
          .queryParam("itc", lang + "-t-i0-und")
          .queryParam("num", 5)
          .build()
          .toUriString();
      String body = transliterateRestTemplate.getForObject(url, String.class);
      return parseSuggestions(body);
    } catch (Exception e) {
      log.warn("transliteration failed ({}): {} - returning empty", lang, e.getMessage());
      return List.of();
    }
  }

  private List<String> parseSuggestions(String body) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    if (root == null || !"SUCCESS".equals(root.path(0).asText())) {
      return List.of();
    }
    JsonNode suggestions = root.path(1).path(0).path(1);
    List<String> result = new ArrayList<>();
    suggestions.forEach(s -> result.add(s.asText()));
    return result;
  }
}
