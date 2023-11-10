/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.client;

import com.chatbot.beans.Genre;
import com.chatbot.beans.Language;
import com.chatbot.beans.Movie;
import com.chatbot.dto.tmdb.MovieResponse;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TMDBServiceApi {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(TMDBServiceApi.class);
  private final RestTemplate restTemplate;

  @Autowired
  public TMDBServiceApi(RestTemplate tmdbRestTemplate) {
    this.restTemplate = tmdbRestTemplate;
  }

  public List<Movie> getMoviesInDateRange(LocalDate startDate, LocalDate endDate) {
    int page = 1;
    int totalPages = -1;
    List<Movie> allMovies = new ArrayList<>();

    while (totalPages == -1 || page <= totalPages) {
      URI uri = buildMovieApiUri(startDate, endDate, page);

      ResponseEntity<MovieResponse> response =
          restTemplate.exchange(uri, HttpMethod.GET, null, MovieResponse.class);
      allMovies.addAll(response.getBody().getResults());

      if (totalPages == -1) {
        totalPages = response.getBody().getTotalPages();
      }

      page++;
    }

    return allMovies;
  }

  public List<Language> getLanguages() {

    URI uri = buildLanguageApiUri();
    ResponseEntity<List> response = restTemplate.exchange(uri, HttpMethod.GET, null, List.class);
    List<Language> allLanguages =
        ((List<LinkedHashMap<String, String>>) response.getBody())
            .stream()
                .map(
                    map ->
                        new Language(
                            map.get("iso_639_1"), map.get("english_name"), map.get("name")))
                .collect(Collectors.toList());

    return allLanguages;
  }

  public List<Genre> getGenres() {
    URI uri = buildGenreApiUri();
    ResponseEntity<Object> response =
        restTemplate.exchange(uri, HttpMethod.GET, null, Object.class);

    List<Genre> allGenres =
        (((List<LinkedHashMap>) ((LinkedHashMap) response.getBody()).get("genres"))
            .stream()
                .map(
                    genreData ->
                        new Genre((Integer) genreData.get("id"), (String) genreData.get("name")))
                .collect(Collectors.toList()));

    return allGenres;
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
