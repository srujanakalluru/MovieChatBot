package com.chatbot.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chatbot.beans.Genre;
import com.chatbot.beans.Language;
import com.chatbot.beans.Movie;
import com.chatbot.client.TMDBServiceApi;
import com.chatbot.errorhandling.NonRetryableException;
import com.chatbot.repository.GenreRepository;
import com.chatbot.repository.LanguageRepository;
import com.chatbot.repository.MovieRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class MovieBackFillServiceImplTest {

  @Mock private TMDBServiceApi tmdbServiceApi;
  @Mock private MovieRepository movieRepository;
  @Mock private LanguageRepository languageRepository;
  @Mock private GenreRepository genreRepository;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private org.springframework.context.MessageSource messageSource;

  @InjectMocks private MovieBackFillServiceImpl service;

  @Captor private ArgumentCaptor<List<Movie>> moviesCaptor;

  @Test
  void addMoviesFlattensGenrePairsAndClearsGenreIdsBeforeSaving() {
    when(movieRepository.findExistingDates(anyList())).thenReturn(Collections.emptyList());

    Movie m1 = new Movie();
    m1.setId(10L);
    m1.setGenreIds(List.of(1, 2));
    Movie m2 = new Movie();
    m2.setId(20L);
    m2.setGenreIds(List.of(3));
    when(tmdbServiceApi.getMoviesInDateRange(any(), any())).thenReturn(List.of(m1, m2));
    when(movieRepository.saveAllAndFlush(anyList())).thenReturn(List.of(m1, m2));

    List<long[]> pairs = service.addMoviesToDatabase("01-01-2020", "01-01-2020");

    assertThat(pairs).hasSize(3);
    assertThat(pairs.stream().map(p -> p[0] + ":" + p[1]).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("10:1", "10:2", "20:3");

    verify(movieRepository).saveAllAndFlush(moviesCaptor.capture());
    assertThat(moviesCaptor.getValue())
        .allSatisfy(m -> assertThat(m.getGenreIds()).isEmpty());
  }

  @Test
  void addMoviesThrowsNonRetryableWhenDateRangeOverlapsExistingData() {
    when(movieRepository.findExistingDates(anyList()))
        .thenReturn(List.of(LocalDate.of(2020, 1, 1)));

    assertThatThrownBy(() -> service.addMoviesToDatabase("01-01-2020", "01-01-2020"))
        .isInstanceOf(NonRetryableException.class);

    verify(tmdbServiceApi, never()).getMoviesInDateRange(any(), any());
    verify(movieRepository, never()).saveAllAndFlush(anyList());
  }

  @Test
  void saveMovieGenresDoesNothingForEmptyList() {
    service.saveMovieGenres(Collections.emptyList());
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void saveMovieGenresBatchInsertsWithIgnore() {
    when(jdbcTemplate.batchUpdate(any(String.class), anyCollection(), anyInt(), any()))
        .thenReturn(new int[][] {});

    service.saveMovieGenres(List.of(new long[] {1L, 2L}));

    verify(jdbcTemplate)
        .batchUpdate(
            eq("INSERT IGNORE INTO movie_genres (movie_id, genre_ids) VALUES (?, ?)"),
            anyCollection(),
            eq(500),
            any());
  }

  @Test
  void addLanguagesPersistsWhatTmdbReturns() {
    List<Language> langs = List.of(new Language("en", "English", "English"));
    when(tmdbServiceApi.getLanguages()).thenReturn(langs);

    service.addLanguagesToDatabase();

    verify(languageRepository).saveAllAndFlush(langs);
  }

  @Test
  void addGenresPersistsWhatTmdbReturns() {
    List<Genre> genres = List.of(new Genre(28, "Action"));
    when(tmdbServiceApi.getGenres()).thenReturn(genres);

    service.addGenresToDatabase();

    verify(genreRepository).saveAllAndFlush(genres);
  }
}
