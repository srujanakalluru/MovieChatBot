/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.dto.tmdb;

import com.chatbot.beans.Movie;
import java.util.List;
import lombok.*;

@Data
public class MoviePage {
  private List<Movie> results;
  private int page;
  private int totalPages;
}
