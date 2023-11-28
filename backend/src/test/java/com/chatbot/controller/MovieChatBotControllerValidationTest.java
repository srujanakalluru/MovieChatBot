package com.chatbot.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.dto.JsonObjectWrapper;
import com.chatbot.errorhandling.GlobalExceptionHandler;
import com.chatbot.service.LlmService;
import com.chatbot.service.MovieIntelService;
import com.chatbot.service.SyncService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MovieChatBotControllerValidationTest {

  @Mock private MovieIntelService movieIntelService;
  @Mock private LlmService llmService;
  @Mock private SyncService syncService;
  @Mock private org.springframework.context.MessageSource messageSource;
  @InjectMocks private MovieChatBotController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(
            messageSource.getMessage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(java.util.Locale.class)))
        .thenReturn("msg");

    org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validatorFactory =
        new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
    validatorFactory.afterPropertiesSet();
    org.springframework.aop.framework.ProxyFactory proxyFactory =
        new org.springframework.aop.framework.ProxyFactory(controller);
    proxyFactory.addAdvice(
        new org.springframework.validation.beanvalidation.MethodValidationInterceptor(
                (ValidatorFactory) validatorFactory));
    MovieChatBotController validatedController = (MovieChatBotController) proxyFactory.getProxy();

    mockMvc =
        MockMvcBuilders.standaloneSetup(validatedController)
            .setControllerAdvice(new GlobalExceptionHandler(messageSource))
            .build();
  }

  @Test
  void returnsMovieRowsAsJson() throws Exception {
    Map<String, Object> row = new HashMap<>();
    row.put("title", "Inception");
    when(syncService.isSyncInProgress()).thenReturn(false);
    when(llmService.generateSql("top movies")).thenReturn("SELECT title FROM movie");
    when(movieIntelService.searchByCriteria("SELECT title FROM movie"))
        .thenReturn(new JsonObjectWrapper(List.of(row)));

    mockMvc
        .perform(post("/query").contentType(MediaType.TEXT_PLAIN).content("top movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value("Inception"));
  }

  @Test
  void blankQueryReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/query").contentType(MediaType.TEXT_PLAIN).content("   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorReason").exists());
  }

  @Test
  void syncInProgressReturns503() throws Exception {
    when(syncService.isSyncInProgress()).thenReturn(true);

    mockMvc
        .perform(post("/query").contentType(MediaType.TEXT_PLAIN).content("top movies"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").exists());
  }
}
