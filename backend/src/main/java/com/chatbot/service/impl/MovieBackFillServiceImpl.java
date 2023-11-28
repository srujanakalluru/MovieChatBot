package com.chatbot.service.impl;

import static com.chatbot.constant.Constants.DATE_FORMATTER;

import com.chatbot.beans.Genre;
import com.chatbot.beans.Language;
import com.chatbot.beans.Movie;
import com.chatbot.constant.ApiMessages;
import com.chatbot.errorhandling.NonRetryableException;
import com.chatbot.client.TMDBServiceApi;
import com.chatbot.repository.GenreRepository;
import com.chatbot.repository.LanguageRepository;
import com.chatbot.repository.MovieRepository;
import com.chatbot.service.MovieBackFillService;
import com.chatbot.utils.DateConversionUtils;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovieBackFillServiceImpl implements MovieBackFillService {

  private final MovieRepository movieRepository;
  private final LanguageRepository languageRepository;
  private final GenreRepository genreRepository;
  private final TMDBServiceApi tmdbServiceApi;
  private final JdbcTemplate jdbcTemplate;
  private final MessageSource messageSource;

  @Autowired
  public MovieBackFillServiceImpl(
      TMDBServiceApi tmdbServiceApi,
      MovieRepository movieRepository,
      LanguageRepository languageRepository,
      GenreRepository genreRepository,
      JdbcTemplate jdbcTemplate,
      MessageSource messageSource) {
    this.tmdbServiceApi = tmdbServiceApi;
    this.movieRepository = movieRepository;
    this.languageRepository = languageRepository;
    this.genreRepository = genreRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.messageSource = messageSource;
  }

  @Transactional
  public List<long[]> addMoviesToDatabase(String startDateStr, String endDateStr) {

    LocalDate startDate = DateConversionUtils.convertStringToDate(startDateStr);
    LocalDate endDate = DateConversionUtils.convertStringToDate(endDateStr);

    List<LocalDate> dateList =
        IntStream.range(0, startDate.until(endDate.plusDays(1)).getDays())
            .mapToObj(startDate::plusDays)
            .collect(Collectors.toList());

    List<String> dateListStr =
        dateList.stream().map(date -> date.format(DATE_FORMATTER)).toList();

    List<String> existingDates =
        movieRepository.findExistingDates(dateList).stream()
            .map(date -> date.format(DATE_FORMATTER))
            .toList();

    if (dateListStr.stream().anyMatch(existingDates::contains)) {
      throw new NonRetryableException(
          messageSource.getMessage(
              ApiMessages.DATE_RANGE_OVERLAP,
              new Object[] {existingDates},
              LocaleContextHolder.getLocale()));
    }

    List<Movie> movieList = tmdbServiceApi.getMoviesInDateRange(startDate, endDate);

    List<long[]> genrePairs = movieList.stream()
        .flatMap(m -> m.getGenreIds() == null ? Stream.empty() :
            m.getGenreIds().stream().map(g -> new long[]{m.getId(), g}))
        .collect(Collectors.toList());

    movieList.forEach(m -> m.setGenreIds(Collections.emptyList()));
    movieRepository.saveAllAndFlush(movieList);

    return genrePairs;
  }

  @Transactional
  public synchronized void saveMovieGenres(List<long[]> genrePairs) {
    if (genrePairs.isEmpty()) return;
    jdbcTemplate.batchUpdate(
        "INSERT IGNORE INTO movie_genres (movie_id, genre_ids) VALUES (?, ?)",
        genrePairs,
        500,
        (ps, pair) -> {
          ps.setLong(1, pair[0]);
          ps.setInt(2, (int) pair[1]);
        }
    );
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
