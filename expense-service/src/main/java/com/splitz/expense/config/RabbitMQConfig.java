package com.splitz.expense.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  public static final String EXCHANGE_NAME = "splitz.events";
  public static final String USER_QUEUE_NAME = "expense.user.events";
  public static final String FRIENDSHIP_QUEUE_NAME = "expense.friendship.events";

  @Bean
  public TopicExchange eventsExchange() {
    return new TopicExchange(EXCHANGE_NAME, true, false);
  }

  @Bean
  public Queue userQueue() {
    return new Queue(USER_QUEUE_NAME, true);
  }

  @Bean
  public Queue friendshipQueue() {
    return new Queue(FRIENDSHIP_QUEUE_NAME, true);
  }

  @Bean
  public Binding userBinding(Queue userQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(userQueue).to(eventsExchange).with("user.*");
  }

  @Bean
  public Binding friendshipBinding(Queue friendshipQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(friendshipQueue).to(eventsExchange).with("friendship.*");
  }
}
