/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.chatbot.beans.Movie;
import java.util.List;
import lombok.*;

@Data
public class MovieResponse {
  private int page;
  private List<Movie> results;

  @JsonProperty("total_pages")
  private int totalPages;

  @JsonProperty("total_results")
  private int totalResults;
}
