package com.chatbot.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.chatbot.config.RabbitMQConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class FailedBatchProducerTest {

  @Mock private RabbitTemplate rabbitTemplate;
  @InjectMocks private FailedBatchProducer producer;

  @Test
  void publishSendsToDelayExchangeWithAttemptCountOne() {
    producer.publish("01-01-2020", "31-01-2020", "oops");

    ArgumentCaptor<FailedBatchMessage> captor = ArgumentCaptor.forClass(FailedBatchMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitMQConfig.DELAY_EXCHANGE),
            eq(RabbitMQConfig.DELAY_ROUTING_KEY),
            captor.capture());

    FailedBatchMessage sent = captor.getValue();
    assertThat(sent.getStartDate()).isEqualTo("01-01-2020");
    assertThat(sent.getEndDate()).isEqualTo("31-01-2020");
    assertThat(sent.getAttemptCount()).isEqualTo(1);
    assertThat(sent.getLastError()).isEqualTo("oops");
  }

  @Test
  void parkSendsToParkingQueueRoutingKey() {
    FailedBatchMessage msg =
        new FailedBatchMessage("01-01-2020", "31-01-2020", 3, LocalDateTime.now(), "oops");

    producer.park(msg);

    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitMQConfig.FAILED_EXCHANGE),
            eq(RabbitMQConfig.PARKING_ROUTING_KEY),
            eq(msg));
  }

  @Test
  void republishIncrementsAttemptCount() {
    FailedBatchMessage msg =
        new FailedBatchMessage("01-01-2020", "31-01-2020", 1, LocalDateTime.now(), "oops");

    producer.republish(msg);

    ArgumentCaptor<FailedBatchMessage> captor = ArgumentCaptor.forClass(FailedBatchMessage.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitMQConfig.DELAY_EXCHANGE),
            eq(RabbitMQConfig.DELAY_ROUTING_KEY),
            captor.capture());
    assertThat(captor.getValue().getAttemptCount()).isEqualTo(2);
  }
}
