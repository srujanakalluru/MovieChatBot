/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.controller;

import com.chatbot.service.MovieBackFillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/internal")
@RestController
public class MovieBackFillController {

  MovieBackFillService movieBackfillService;

  @Autowired
  public MovieBackFillController(MovieBackFillService movieBackfillService) {
    this.movieBackfillService = movieBackfillService;
  }

  @GetMapping("/backfill_movie_data")
  public void addMoviesToDatabase(String startDateStr, String endDateStr) {
    movieBackfillService.addMoviesToDatabase(startDateStr, endDateStr);
  }

  @GetMapping("/backfill_language_data")
  public void addLanguagesToDatabase() {
    movieBackfillService.addLanguagesToDatabase();
  }

  @GetMapping("/backfill_genre_data")
  public void addGenreToDatabase() {
    movieBackfillService.addGenresToDatabase();
  }
}
