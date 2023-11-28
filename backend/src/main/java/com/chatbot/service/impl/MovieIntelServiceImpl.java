package com.chatbot.service.impl;

import com.chatbot.dto.JsonObjectWrapper;
import com.chatbot.service.MovieIntelService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MovieIntelServiceImpl implements MovieIntelService {

  private static final Pattern READ_ONLY_SQL = Pattern.compile("^(?is)\\s*(SELECT|WITH)\\b.*");

  private final JdbcTemplate queryJdbcTemplate;

  @Value("${query.timeout-ms:5000}")
  private int queryTimeoutMs;

  @Autowired
  public MovieIntelServiceImpl(@Qualifier("queryJdbcTemplate") JdbcTemplate queryJdbcTemplate) {
    this.queryJdbcTemplate = queryJdbcTemplate;
  }

  @Override
  public JsonObjectWrapper searchByCriteria(String sql) {
    String trimmed = sql.trim().replaceAll(";+$", "");

    if (trimmed.contains(";") || !READ_ONLY_SQL.matcher(trimmed).matches()) {
      log.warn("Rejected non-SELECT or multi-statement SQL from model [{}]", trimmed);
      return new JsonObjectWrapper(Collections.emptyList());
    }

    String hint = "/*+ MAX_EXECUTION_TIME(" + queryTimeoutMs + ") */ ";
    String sanitizedSQL;
    if (trimmed.toUpperCase().startsWith("WITH ")) {
      sanitizedSQL = trimmed.replaceFirst("(?si)(\\bWITH\\b.+\\))\\s+SELECT\\b\\s+", "$1 SELECT " + hint);
    } else {
      sanitizedSQL = "SELECT " + hint + trimmed.replaceAll("(?i)^select\\s+", "");
    }
    try {
      List<Map<String, Object>> rows = queryJdbcTemplate.queryForList(sanitizedSQL);
      log.debug("{}", sanitizedSQL);
      return new JsonObjectWrapper(rows);
    } catch (DataAccessException e) {
      if (e instanceof QueryTimeoutException) {
        log.warn("Query exceeded {}ms [{}]", queryTimeoutMs, sanitizedSQL);
      } else if (e instanceof BadSqlGrammarException) {
        log.warn("Model produced invalid SQL [{}] - {}", sanitizedSQL, e.getMessage());
      } else if (e instanceof UncategorizedSQLException u
          && u.getSQLException() != null
          && u.getSQLException().getErrorCode() == 3024) {
        log.warn("Query exceeded {}ms (MySQL hint) [{}]", queryTimeoutMs, sanitizedSQL);
      } else {
        log.warn("{} - {} [{}]", e.getClass().getSimpleName(), e.getMessage(), sanitizedSQL);
      }
      return new JsonObjectWrapper(Collections.emptyList());
    }
  }
}
