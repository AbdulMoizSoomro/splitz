package com.splitz.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.user.model.OutboxEvent;
import com.splitz.user.repository.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher Unit Tests")
class OutboxPublisherTest {

  @Mock private OutboxRepository outboxRepository;
  @Mock private RabbitTemplate rabbitTemplate;

  @InjectMocks private OutboxPublisher outboxPublisher;

  @Test
  @DisplayName("Should publish pending outbox events to RabbitMQ and mark as processed")
  void testPublishPendingEvents_PublishesAndMarksProcessed() {
    OutboxEvent userEvent =
        OutboxEvent.builder()
            .id("evt-1")
            .aggregateType("USER")
            .aggregateId("100")
            .eventType("USER_CREATED")
            .payload("{\"userId\":100,\"username\":\"alice\"}")
            .createdAt(Instant.now())
            .processed(false)
            .build();

    OutboxEvent friendshipEvent =
        OutboxEvent.builder()
            .id("evt-2")
            .aggregateType("FRIENDSHIP")
            .aggregateId("50")
            .eventType("FRIENDSHIP_ACCEPTED")
            .payload("{\"userId\":10,\"friendId\":20}")
            .createdAt(Instant.now())
            .processed(false)
            .build();

    when(outboxRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc())
        .thenReturn(List.of(userEvent, friendshipEvent));

    outboxPublisher.publishPendingEvents();

    verify(rabbitTemplate)
        .convertAndSend(
            eq("splitz.events"), eq("user.created"), eq("{\"userId\":100,\"username\":\"alice\"}"));
    verify(rabbitTemplate)
        .convertAndSend(
            eq("splitz.events"), eq("friendship.accepted"), eq("{\"userId\":10,\"friendId\":20}"));

    assertThat(userEvent.isProcessed()).isTrue();
    assertThat(friendshipEvent.isProcessed()).isTrue();
    verify(outboxRepository).saveAll(List.of(userEvent, friendshipEvent));
  }
}
