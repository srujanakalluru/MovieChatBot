package com.chatbot.startup;

import com.chatbot.config.LlmConfig;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class StartupDependencyChecker {

  private final DataSource dataSource;
  private final ConnectionFactory rabbitConnectionFactory;
  private final LlmConfig llmConfig;
  private final boolean enabled;
  private final int probeTimeoutMs;

  public StartupDependencyChecker(
      DataSource dataSource,
      ConnectionFactory rabbitConnectionFactory,
      LlmConfig llmConfig,
      @Value("${startup.dependency-check.enabled:true}") boolean enabled,
      @Value("${startup.dependency-check.timeout-ms:3000}") int probeTimeoutMs) {
    this.dataSource = dataSource;
    this.rabbitConnectionFactory = rabbitConnectionFactory;
    this.llmConfig = llmConfig;
    this.enabled = enabled;
    this.probeTimeoutMs = probeTimeoutMs;
  }

  @PostConstruct
  void verifyDependencies() {
    if (!enabled) {
      log.warn("Startup dependency check disabled - MySQL/RabbitMQ/LLM availability is not enforced");
      return;
    }
    checkMysql();
    checkRabbitMq();
    checkLlm();
    log.info("Startup dependency check passed: MySQL, RabbitMQ and LLM are reachable");
  }

  private void checkMysql() {
    try (Connection connection = dataSource.getConnection()) {
      if (!connection.isValid(Math.max(1, probeTimeoutMs / 1000))) {
        throw new IllegalStateException("MySQL connection is not valid");
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "MySQL is not reachable - backend cannot start. " + e.getMessage(), e);
    }
  }

  private void checkRabbitMq() {
    try (org.springframework.amqp.rabbit.connection.Connection connection =
        rabbitConnectionFactory.createConnection()) {
    } catch (Exception e) {
      throw new IllegalStateException(
          "RabbitMQ is not reachable - backend cannot start. " + e.getMessage(), e);
    }
  }

  private void checkLlm() {
    String healthUrl = llmHealthUrl();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(probeTimeoutMs);
    factory.setReadTimeout(probeTimeoutMs);
    RestTemplate probe = new RestTemplate(factory);
    try {
      probe.getForEntity(healthUrl, String.class);
    } catch (RestClientResponseException e) {
      log.info("LLM server reachable at {} (HTTP {})", healthUrl, e.getStatusCode());
    } catch (RestClientException e) {
      throw new IllegalStateException(
          "LLM server is not reachable at " + healthUrl + " - backend cannot start. "
              + e.getMessage(), e);
    }
  }

  private String llmHealthUrl() {
    URI endpoint = URI.create(llmConfig.getEndpoint());
    return endpoint.getScheme() + "://" + endpoint.getAuthority() + "/health";
  }
}
