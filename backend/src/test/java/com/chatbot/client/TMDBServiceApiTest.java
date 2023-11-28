package com.chatbot.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.beans.Genre;
import com.chatbot.beans.Language;
import com.chatbot.beans.Movie;
import com.chatbot.dto.tmdb.GenreListResponse;
import com.chatbot.dto.tmdb.MovieResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class TMDBServiceApiTest {

  @Mock private RestTemplate restTemplate;
  @InjectMocks private TMDBServiceApi api;

  private static MovieResponse pageOf(int totalPages, Movie... movies) {
    MovieResponse response = new MovieResponse();
    response.setResults(List.of(movies));
    response.setTotalPages(totalPages);
    return response;
  }

  @SuppressWarnings("unchecked")
  private <T> org.mockito.stubbing.OngoingStubbing<ResponseEntity<T>> whenExchange() {
    return (org.mockito.stubbing.OngoingStubbing<ResponseEntity<T>>)
        (Object)
            when(
                restTemplate.exchange(
                    any(URI.class),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)));
  }

  @SuppressWarnings("unchecked")
  private void verifyExchangeCount(int count) {
    verify(restTemplate, times(count))
        .exchange(any(URI.class), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));
  }

  @Test
  void singlePageRangeFetchesOnce() {
    Movie movie = new Movie();
    this.<MovieResponse>whenExchange().thenReturn(ResponseEntity.ok(pageOf(1, movie)));

    List<Movie> result =
        api.getMoviesInDateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

    assertThat(result).containsExactly(movie);
    verifyExchangeCount(1);
  }

  @Test
  void multiPageRangeFetchesAllPages() {
    Movie first = new Movie();
    first.setId(1L);
    Movie second = new Movie();
    second.setId(2L);
    this.<MovieResponse>whenExchange()
        .thenReturn(ResponseEntity.ok(pageOf(2, first)))
        .thenReturn(ResponseEntity.ok(pageOf(2, second)));

    List<Movie> result =
        api.getMoviesInDateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

    assertThat(result).containsExactly(first, second);
    verifyExchangeCount(2);
  }

  @Test
  void pageCountAboveCapStopsAtFiveHundredPages() {
    Movie movie = new Movie();
    this.<MovieResponse>whenExchange().thenReturn(ResponseEntity.ok(pageOf(501, movie)));

    List<Movie> result =
        api.getMoviesInDateRange(LocalDate.of(1990, 1, 1), LocalDate.of(2020, 12, 31));

    assertThat(result).hasSize(500);
    verifyExchangeCount(500);
  }

  @Test
  void languagesBindDirectlyFromJson() {
    Language telugu = new Language("te", "Telugu", "తెలుగు");
    this.<List<Language>>whenExchange().thenReturn(ResponseEntity.ok(List.of(telugu)));

    List<Language> result = api.getLanguages();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getIso_639_1()).isEqualTo("te");
    assertThat(result.get(0).getEnglish_name()).isEqualTo("Telugu");
  }

  @Test
  void genresBindThroughWrapperDto() {
    GenreListResponse body = new GenreListResponse();
    body.setGenres(List.of(new Genre(28, "Action")));
    this.<GenreListResponse>whenExchange().thenReturn(ResponseEntity.ok(body));

    List<Genre> result = api.getGenres();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(28);
    assertThat(result.get(0).getName()).isEqualTo("Action");
  }

  @Test
  void emptyBodyIsReportedAsTransientFailure() {
    this.<GenreListResponse>whenExchange().thenReturn(ResponseEntity.ok(null));

    assertThatThrownBy(() -> api.getGenres()).isInstanceOf(ResourceAccessException.class);
  }

  @Test
  void rateLimitRetriesWithBackoffThenSucceeds() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Retry-After", "0");
    HttpClientErrorException tooMany =
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers,
            new byte[0], StandardCharsets.UTF_8);

    GenreListResponse body = new GenreListResponse();
    body.setGenres(List.of(new Genre(28, "Action")));

    this.<GenreListResponse>whenExchange()
        .thenThrow(tooMany)
        .thenThrow(tooMany)
        .thenReturn(ResponseEntity.ok(body));

    List<Genre> result = api.getGenres();

    assertThat(result).hasSize(1);
    verifyExchangeCount(3);
  }

  @Test
  void rateLimitGivesUpAfterMaxRetriesAndRethrows() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Retry-After", "0");
    HttpClientErrorException tooMany =
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers,
            new byte[0], StandardCharsets.UTF_8);

    this.<GenreListResponse>whenExchange().thenThrow(tooMany);

    assertThatThrownBy(() -> api.getGenres())
        .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
    verifyExchangeCount(4);
  }
}
