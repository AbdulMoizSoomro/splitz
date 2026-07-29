package com.splitz.user.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE_NAME = "splitz.events";

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(EXCHANGE_NAME, true, false);
  }
}
