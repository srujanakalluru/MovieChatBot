package com.chatbot.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.chatbot.dto.JsonObjectWrapper;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MovieIntelServiceImplTest {

  @Mock private JdbcTemplate jdbcTemplate;
  private MovieIntelServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new MovieIntelServiceImpl(jdbcTemplate);
    ReflectionTestUtils.setField(service, "queryTimeoutMs", 5000);
  }

  @Test
  void stripsLeadingSelectAndTrailingSemicolonThenAddsTimeoutHint() {
    Map<String, Object> row = new HashMap<>();
    row.put("title", "Inception");
    List<Map<String, Object>> rows = List.of(row);
    when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

    JsonObjectWrapper result = service.searchByCriteria("SELECT title FROM movie;;");

    assertThat(result.getData()).isEqualTo(rows);
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .isEqualTo("SELECT /*+ MAX_EXECUTION_TIME(5000) */ title FROM movie");
  }

  @Test
  void handlesLowercaseSelect() {
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

    service.searchByCriteria("select x from movie");

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .isEqualTo("SELECT /*+ MAX_EXECUTION_TIME(5000) */ x from movie");
  }

  @Test
  void injectsHintIntoOuterSelectOfCte() {
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

    service.searchByCriteria(
        "WITH gc AS (SELECT genre_id, COUNT(*) AS cnt FROM movie_genres GROUP BY genre_id) "
        + "SELECT g.name, gc.cnt FROM gc JOIN genre g ON gc.genre_id = g.id ORDER BY gc.cnt DESC LIMIT 1;");

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue())
        .isEqualTo("WITH gc AS (SELECT genre_id, COUNT(*) AS cnt FROM movie_genres GROUP BY genre_id) "
            + "SELECT /*+ MAX_EXECUTION_TIME(5000) */ g.name, gc.cnt FROM gc JOIN genre g ON gc.genre_id = g.id ORDER BY gc.cnt DESC LIMIT 1");
  }

  @Test
  void rejectsNonSelectStatementsWithoutTouchingTheDatabase() {
    assertThat(service.searchByCriteria("DROP TABLE movie").getData()).isEmpty();
    assertThat(service.searchByCriteria("UPDATE movie SET title='x'").getData()).isEmpty();
    assertThat(service.searchByCriteria("DELETE FROM movie").getData()).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void rejectsMultiStatementAttempts() {
    assertThat(service.searchByCriteria("SELECT 1; DROP TABLE movie").getData()).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void allowsCteStatements() {
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());
    assertThat(service.searchByCriteria("WITH x AS (SELECT 1) SELECT * FROM x").getData())
        .isEmpty();
    org.mockito.Mockito.verify(jdbcTemplate).queryForList(anyString());
  }

  @Test
  void returnsEmptyOnQueryTimeout() {
    when(jdbcTemplate.queryForList(anyString())).thenThrow(new QueryTimeoutException("timeout"));
    assertThat(service.searchByCriteria("SELECT 1").getData()).isEmpty();
  }

  @Test
  void returnsEmptyOnBadSqlGrammar() {
    when(jdbcTemplate.queryForList(anyString()))
        .thenThrow(new BadSqlGrammarException("task", "SELECT 1", new SQLException("bad grammar")));
    assertThat(service.searchByCriteria("SELECT 1").getData()).isEmpty();
  }

  @Test
  void returnsEmptyOnMysqlTimeoutHintErrorCode3024() {
    SQLException mysqlTimeout = new SQLException("max execution time exceeded", "HY000", 3024);
    when(jdbcTemplate.queryForList(anyString()))
        .thenThrow(new UncategorizedSQLException("task", "SELECT 1", mysqlTimeout));
    assertThat(service.searchByCriteria("SELECT 1").getData()).isEmpty();
  }

  @Test
  void returnsEmptyOnGenericDataAccessException() {
    when(jdbcTemplate.queryForList(anyString()))
        .thenThrow(new DataIntegrityViolationException("constraint"));
    assertThat(service.searchByCriteria("SELECT 1").getData()).isEmpty();
  }
}
