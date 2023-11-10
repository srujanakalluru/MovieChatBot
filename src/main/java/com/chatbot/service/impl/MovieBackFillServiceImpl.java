/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.service.impl;

import static com.chatbot.constant.Constants.DATE_FORMATTER;

import com.chatbot.beans.Genre;
import com.chatbot.beans.Language;
import com.chatbot.beans.Movie;
import com.chatbot.client.TMDBServiceApi;
import com.chatbot.repository.GenreRepository;
import com.chatbot.repository.LanguageRepository;
import com.chatbot.repository.MovieRepository;
import com.chatbot.service.MovieBackFillService;
import com.chatbot.utils.DateConversionUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MovieBackFillServiceImpl implements MovieBackFillService {

  private final MovieRepository movieRepository;
  private final LanguageRepository languageRepository;
  private final GenreRepository genreRepository;
  private final TMDBServiceApi tmdbServiceApi;

  @Autowired
  public MovieBackFillServiceImpl(
      TMDBServiceApi tmdbServiceApi,
      MovieRepository movieRepository,
      LanguageRepository languageRepository,
      GenreRepository genreRepository) {
    this.tmdbServiceApi = tmdbServiceApi;
    this.movieRepository = movieRepository;
    this.languageRepository = languageRepository;
    this.genreRepository = genreRepository;
  }

  public void addMoviesToDatabase(String startDateStr, String endDateStr) {

    LocalDate startDate = DateConversionUtils.convertStringToDate(startDateStr);
    LocalDate endDate = DateConversionUtils.convertStringToDate(endDateStr);

    List<LocalDate> dateList =
        IntStream.range(0, startDate.until(endDate.plusDays(1)).getDays())
            .mapToObj(startDate::plusDays)
            .collect(Collectors.toList());

    List<String> dateListStr =
        dateList.stream().map(date -> date.format(DATE_FORMATTER)).collect(Collectors.toList());

    List<String> existingDates =
        movieRepository.findExistingDates(dateList).stream()
            .map(date -> date.format(DATE_FORMATTER))
            .collect(Collectors.toList());

    if (dateListStr.stream().anyMatch(existingDates::contains)) {
      throw new RuntimeException(
          String.format(
              "The selected date range overlaps with existing data for the following dates: %s. Please choose a different date range.",
              existingDates));
    }

    List<Movie> movieList = tmdbServiceApi.getMoviesInDateRange(startDate, endDate);
    movieRepository.saveAllAndFlush(movieList);
  }

  @Override
  public void addLanguagesToDatabase() {
    List<Language> languageList = tmdbServiceApi.getLanguages();
    languageRepository.saveAllAndFlush(languageList);
  }

  @Override
  public void addGenresToDatabase() {
    List<Genre> genreList = tmdbServiceApi.getGenres();
    genreRepository.saveAllAndFlush(genreList);
  }
}
