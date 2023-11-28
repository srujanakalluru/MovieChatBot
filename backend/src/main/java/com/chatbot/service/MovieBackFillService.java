
package com.chatbot.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface MovieBackFillService {
  List<long[]> addMoviesToDatabase(String startDateStr, String endDateStr);

  void saveMovieGenres(List<long[]> genrePairs);

  void addLanguagesToDatabase();

  void addGenresToDatabase();
}
