package com.chatbot.dto.tmdb;

import com.chatbot.beans.Genre;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenreListResponse {
  private List<Genre> genres;
}
