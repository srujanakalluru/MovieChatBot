/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.service;

import org.springframework.stereotype.Service;

@Service
public interface MovieBackFillService {
  void addMoviesToDatabase(String startDateStr, String endDateStr);

  void addLanguagesToDatabase();

  void addGenresToDatabase();
}
