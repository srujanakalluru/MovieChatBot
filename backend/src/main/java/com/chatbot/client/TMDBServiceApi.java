package com.chatbot.client;

import com.chatbot.beans.Genre;
import com.chatbot.beans.Language;
import com.chatbot.beans.Movie;
import com.chatbot.dto.tmdb.GenreListResponse;
import com.chatbot.dto.tmdb.MovieResponse;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TMDBServiceApi {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(TMDBServiceApi.class);

  private static final int MAX_RATE_LIMIT_RETRIES = 3;

  private final RestTemplate restTemplate;

  @Autowired
  public TMDBServiceApi(RestTemplate tmdbRestTemplate) {
    this.restTemplate = tmdbRestTemplate;
  }

  public List<Movie> getMoviesInDateRange(LocalDate startDate, LocalDate endDate) {
    int page = 1;
    int totalPages = -1;
    List<Movie> allMovies = new ArrayList<>();

    int maxPages = 500;

    while (totalPages == -1 || page <= Math.min(totalPages, maxPages)) {
      URI uri = buildMovieApiUri(startDate, endDate, page);
      MovieResponse body = fetch(uri, new ParameterizedTypeReference<MovieResponse>() {});
      allMovies.addAll(body.getResults());

      if (totalPages == -1) {
        totalPages = body.getTotalPages();
        if (totalPages > maxPages) {
          log.warn("{} pages available but capped at {} - consider narrowing the date range",
              totalPages, maxPages);
        }
      }

      page++;
    }

    return allMovies;
  }

  public List<Language> getLanguages() {
    return fetch(buildLanguageApiUri(), new ParameterizedTypeReference<List<Language>>() {});
  }

  public List<Genre> getGenres() {
    return fetch(buildGenreApiUri(), new ParameterizedTypeReference<GenreListResponse>() {})
        .getGenres();
  }

  private <T> T fetch(URI uri, ParameterizedTypeReference<T> type) {
    int attempt = 0;
    while (true) {
      try {
        ResponseEntity<T> response = restTemplate.exchange(uri, HttpMethod.GET, null, type);
        T body = response.getBody();
        if (body == null) {
          throw new ResourceAccessException("Empty response from TMDB: " + uri.getPath());
        }
        return body;
      } catch (HttpClientErrorException.TooManyRequests e) {
        attempt++;
        if (attempt > MAX_RATE_LIMIT_RETRIES) {
          throw e;
        }
        long waitMs = retryAfterMs(e, attempt);
        log.warn("TMDB rate limit hit - waiting {} ms (attempt {}/{})",
            waitMs, attempt, MAX_RATE_LIMIT_RETRIES);
        try {
          Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  private long retryAfterMs(HttpClientErrorException.TooManyRequests e, int attempt) {
    String retryAfter =
        e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
    if (retryAfter != null) {
      try {
        return Long.parseLong(retryAfter) * 1000L;
      } catch (NumberFormatException ignored) {
      }
    }
    return 1000L * attempt;
  }

  private URI buildMovieApiUri(LocalDate startDate, LocalDate endDate, int page) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString("https://api.themoviedb.org/3/discover/movie")
            .queryParam("primary_release_date.gte", startDate.toString())
            .queryParam("primary_release_date.lte", endDate.toString())
            .queryParam("page", page);

    return builder.build().toUri();
  }

  private URI buildLanguageApiUri() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString("https://api.themoviedb.org/3/configuration/languages");
    return builder.build().toUri();
  }

  private URI buildGenreApiUri() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString("https://api.themoviedb.org/3/genre/movie/list");
    return builder.build().toUri();
  }
}
