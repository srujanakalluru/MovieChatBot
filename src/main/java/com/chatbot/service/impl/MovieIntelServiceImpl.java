/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatbot.dto.JsonObjectWrapper;
import com.chatbot.repository.MovieRepository;
import com.chatbot.service.MovieIntelService;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MovieIntelServiceImpl implements MovieIntelService {
  private final MovieRepository movieRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private EntityManager entityManager;

  @Autowired
  public MovieIntelServiceImpl(
      MovieRepository movieRepository, EntityManager entityManager) {
    this.movieRepository = movieRepository;
    this.entityManager = entityManager;
  }

  @Override
  public String searchByCriteria(String sql) {
    log.debug("Message: "+sql);

    String sanitizedSQL = "SELECT " + sql;

    try (ResultSet resultSet =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  Statement statement = connection.createStatement();
                  return statement.executeQuery(sanitizedSQL);
                })) {
      ResultSetMetaData metaData = resultSet.getMetaData();
      int columnCount = metaData.getColumnCount();
      List<Map<String, Object>> rows = new ArrayList<>();

      while (resultSet.next()) {
        Map<String, Object> rowMap = new HashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          String columnName = metaData.getColumnName(i);
          Object columnValue = resultSet.getObject(i);
          rowMap.put(columnName, columnValue);
        }
        rows.add(rowMap);
      }

      JsonObjectWrapper jsonObjectWrapper = new JsonObjectWrapper(rows);

        return objectMapper.writeValueAsString(jsonObjectWrapper);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
