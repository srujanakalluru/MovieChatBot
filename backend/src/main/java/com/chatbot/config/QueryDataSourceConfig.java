package com.chatbot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class QueryDataSourceConfig {

  @Bean
  @Primary
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean(name = "queryJdbcTemplate")
  public JdbcTemplate queryJdbcTemplate(
      @Value("${query.datasource.url}") String url,
      @Value("${query.datasource.username}") String username,
      @Value("${query.datasource.password}") String password,
      @Value("${query.timeout-ms:5000}") int queryTimeoutMs) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    config.setReadOnly(true);
    config.setMaximumPoolSize(5);
    config.setPoolName("query-ro");
    JdbcTemplate template = new JdbcTemplate(new HikariDataSource(config));
    template.setQueryTimeout((int) Math.ceil(queryTimeoutMs / 1000.0) + 2);
    return template;
  }
}
