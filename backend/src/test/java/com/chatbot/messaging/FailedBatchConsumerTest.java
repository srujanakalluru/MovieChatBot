package com.chatbot.messaging;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.errorhandling.NonRetryableException;
import com.chatbot.service.MovieBackFillService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FailedBatchConsumerTest {

  @Mock private MovieBackFillService movieBackFillService;
  @Mock private FailedBatchProducer failedBatchProducer;
  @InjectMocks private FailedBatchConsumer consumer;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(consumer, "maxRetryAttempts", 3);
  }

  private FailedBatchMessage message(int attempt) {
    return new FailedBatchMessage("01-01-2020", "31-01-2020", attempt, LocalDateTime.now(), "err");
  }

  @Test
  void successfulRetrySavesGenresAndDoesNotRepublish() {
    when(movieBackFillService.addMoviesToDatabase("01-01-2020", "31-01-2020"))
        .thenReturn(List.of(new long[] {1L, 2L}));

    consumer.handleFailedBatch(message(1));

    verify(movieBackFillService).saveMovieGenres(anyList());
    verify(failedBatchProducer, never()).republish(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void nonRetryableFailureIsSkippedWithoutRepublish() {
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new NonRetryableException("overlap"));

    consumer.handleFailedBatch(message(1));

    verify(movieBackFillService, never()).saveMovieGenres(anyList());
    verify(failedBatchProducer, never()).republish(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void retryableFailureUnderLimitRepublishes() {
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("transient"));

    FailedBatchMessage msg = message(1);
    consumer.handleFailedBatch(msg);

    verify(failedBatchProducer).republish(msg);
  }

  @Test
  void retryableFailureAtLimitParksTheMessage() {
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("transient"));

    FailedBatchMessage msg = message(3);
    consumer.handleFailedBatch(msg);

    verify(failedBatchProducer, never()).republish(org.mockito.ArgumentMatchers.any());
    verify(failedBatchProducer).park(msg);
  }

  @Test
  void unknownExceptionIsNotRetriedByDefault() {
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new RuntimeException("data bug"));

    consumer.handleFailedBatch(message(1));

    verify(failedBatchProducer, never()).republish(org.mockito.ArgumentMatchers.any());
  }
}
