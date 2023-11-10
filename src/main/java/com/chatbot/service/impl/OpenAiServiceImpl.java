/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.service.impl;

import com.chatbot.dto.openai.Message;
import com.chatbot.dto.openai.OpenAiRequest;
import com.chatbot.dto.openai.OpenAiResponse;
import com.chatbot.service.OpenAiService;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class OpenAiServiceImpl implements OpenAiService {
  private static final String PROMPT_PREFIX =
      "Given the MySQL tables: ###\\n"
          + "##genre(id,name,PRIMARY KEY (id))\\n"
          + "##language(iso_639_1,english_name,name,PRIMARY KEY (iso_639_1))\\n"
          + "##movie_genres(movie_id,genre_ids,KEY (movie_id),CONSTRAINT FOREIGN KEY (movie_id) REFERENCES movie(id))\\n"
          + "##movie(id,adult,backdrop_path,original_language,original_title,overview,popularity,poster_path,release_date,"
          + "title,video,vote_average,vote_count,PRIMARY KEY (id))"
          + "###";
  private static final String PROMPT_SUFFIX = "###SELECT";
  private final RestTemplate openAIRestTemplate;
  private final String openAiModel;
  private final String openAiEndpoint;
  private final Double openAiTemperature;
  private final Integer openAiMaxTokens;

  @Autowired
  public OpenAiServiceImpl(
      String openAiModel,
      String openAiEndpoint,
      RestTemplate openAIRestTemplate,
      Integer openAiMaxTokens,
      Double openAiTemperature) {
    this.openAiModel = openAiModel;
    this.openAiEndpoint = openAiEndpoint;
    this.openAIRestTemplate = openAIRestTemplate;
    this.openAiMaxTokens = openAiMaxTokens;
    this.openAiTemperature = openAiTemperature;
  }

  @Override
  public String submitOpenAiRequest(String inputCommand) {
    String prompt = PROMPT_PREFIX + inputCommand + PROMPT_SUFFIX;
    Message message = new Message("user", prompt);

    log.debug("Prompt: "+prompt);

    OpenAiRequest aiRequest =
        new OpenAiRequest(
            openAiModel, Collections.singletonList(message), openAiMaxTokens, openAiTemperature);

    ResponseEntity<OpenAiResponse> restCallResponse =
        this.openAIRestTemplate.postForEntity(openAiEndpoint, aiRequest, OpenAiResponse.class);

    if (HttpStatus.OK.equals(restCallResponse.getStatusCode())) {
      OpenAiResponse aiResponse = restCallResponse.getBody();
      return aiResponse.getChoices().get(0).getMessage().get("content");
    }
    return null;
  }
}
