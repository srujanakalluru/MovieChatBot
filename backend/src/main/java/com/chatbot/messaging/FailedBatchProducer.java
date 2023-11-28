package com.chatbot.messaging;

import com.chatbot.config.RabbitMQConfig;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FailedBatchProducer {

  private final RabbitTemplate rabbitTemplate;

  @Autowired
  public FailedBatchProducer(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publish(String startDate, String endDate, String error) {
    FailedBatchMessage msg = new FailedBatchMessage(
        startDate, endDate, 1, LocalDateTime.now(), error);
    enqueueForRetry(msg);
  }

  public void republish(FailedBatchMessage msg) {
    msg.setAttemptCount(msg.getAttemptCount() + 1);
    enqueueForRetry(msg);
  }

  public void park(FailedBatchMessage msg) {
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.FAILED_EXCHANGE,
        RabbitMQConfig.PARKING_ROUTING_KEY,
        msg);
    log.error("Batch {} -> {} parked after {} attempts - inspect/replay via the "
            + "RabbitMQ management UI or the advanced sync panel",
        msg.getStartDate(), msg.getEndDate(), msg.getAttemptCount());
  }

  private void enqueueForRetry(FailedBatchMessage msg) {
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.DELAY_EXCHANGE,
        RabbitMQConfig.DELAY_ROUTING_KEY,
        msg);
    log.info("Batch {} -> {} queued for retry (attempt {})",
        msg.getStartDate(), msg.getEndDate(), msg.getAttemptCount());
  }
}
