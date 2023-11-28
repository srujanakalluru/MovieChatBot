package com.chatbot.messaging;

import com.chatbot.config.RabbitMQConfig;
import com.chatbot.errorhandling.RetryPolicy;
import com.chatbot.service.MovieBackFillService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FailedBatchConsumer {

  @Value("${sync.max-retry-attempts:3}")
  private int maxRetryAttempts;

  private final MovieBackFillService movieBackFillService;
  private final FailedBatchProducer failedBatchProducer;

  @Autowired
  public FailedBatchConsumer(MovieBackFillService movieBackFillService,
                             FailedBatchProducer failedBatchProducer) {
    this.movieBackFillService = movieBackFillService;
    this.failedBatchProducer = failedBatchProducer;
  }

  @RabbitListener(queues = RabbitMQConfig.FAILED_QUEUE)
  public void handleFailedBatch(FailedBatchMessage msg) {
    log.info("Batch {} -> {} attempt {}/{}",
        msg.getStartDate(), msg.getEndDate(), msg.getAttemptCount(), maxRetryAttempts);

    try {
      List<long[]> genrePairs = movieBackFillService.addMoviesToDatabase(
          msg.getStartDate(), msg.getEndDate());
      movieBackFillService.saveMovieGenres(genrePairs);
      log.info("Batch {} -> {} succeeded on attempt {}",
          msg.getStartDate(), msg.getEndDate(), msg.getAttemptCount());
    } catch (Exception e) {
      if (!RetryPolicy.isRetryable(e)) {
        log.warn("Batch {} -> {} skipped (non-retryable): {}",
            msg.getStartDate(), msg.getEndDate(), e.getMessage());
      } else if (msg.getAttemptCount() < maxRetryAttempts) {
        log.warn("Batch {} -> {} failed again: {}",
            msg.getStartDate(), msg.getEndDate(), e.getMessage());
        failedBatchProducer.republish(msg);
      } else {
        failedBatchProducer.park(msg);
      }
    }
  }
}
