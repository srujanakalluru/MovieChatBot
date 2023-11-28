package com.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync(order = Ordered.HIGHEST_PRECEDENCE)
public class MovieChatBot {

  public static void main(String[] args) {
    SpringApplication.run(MovieChatBot.class, args);
  }
}
