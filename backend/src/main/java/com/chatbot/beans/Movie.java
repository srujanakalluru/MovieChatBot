package com.chatbot.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;
import lombok.*;

@Getter
@Setter
@ToString(exclude = "genreIds")
@EqualsAndHashCode(of = "id")
@Entity
public class Movie {
  @Id private long id;
  private boolean adult;

  @JsonProperty("backdrop_path")
  private String backdropPath;

  @JsonProperty("genre_ids")
  @ElementCollection
  @CollectionTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"))
  private List<Integer> genreIds;

  @JsonProperty("original_language")
  private String originalLanguage;

  @JsonProperty("original_title")
  private String originalTitle;

  @Lob
  @Column(columnDefinition = "MEDIUMTEXT")
  private String overview;

  private double popularity;

  @JsonProperty("poster_path")
  private String posterPath;

  @JsonProperty("release_date")
  private LocalDate releaseDate;

  private String title;
  private boolean video;

  @JsonProperty("vote_average")
  private double voteAverage;

  @JsonProperty("vote_count")
  private int voteCount;
}
