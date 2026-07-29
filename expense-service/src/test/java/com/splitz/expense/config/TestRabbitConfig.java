package com.splitz.expense.config;

import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestRabbitConfig {

  @Bean
  @Primary
  public ConnectionFactory connectionFactory() {
    return Mockito.mock(ConnectionFactory.class);
  }

  @Bean
  @Primary
  public RabbitTemplate rabbitTemplate() {
    return Mockito.mock(RabbitTemplate.class);
  }
}
