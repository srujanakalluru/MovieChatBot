/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.repository;

import com.chatbot.beans.Movie;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

  @Query("SELECT DISTINCT m.releaseDate FROM Movie m WHERE m.releaseDate IN :dateList")
  List<LocalDate> findExistingDates(List<LocalDate> dateList);
}
