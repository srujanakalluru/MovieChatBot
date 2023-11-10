package com.chatbot.controller;

import com.chatbot.service.MovieIntelService;
import com.chatbot.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class MovieIntelController {

  private final MovieIntelService movieIntelService;
  private final OpenAiService openAiService;

  @Autowired
  public MovieIntelController(
          MovieIntelService movieIntelService, OpenAiService openAiService) {
    this.movieIntelService = movieIntelService;
    this.openAiService = openAiService;
  }

  @PostMapping("/query")
  public String submitOpenAiRequest(@RequestBody String userInput) {
    String sql = openAiService.submitOpenAiRequest(userInput);
    if (null == sql) {
      return "No recommendations found";
    }
    return movieIntelService.searchByCriteria(sql);
  }
}
