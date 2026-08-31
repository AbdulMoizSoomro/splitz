package com.splitz.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.FriendshipEvent;
import com.splitz.event.UserEvent;
import com.splitz.user.model.OutboxEvent;
import com.splitz.user.repository.OutboxRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxDomainEventPublisher Unit Tests")
class OutboxDomainEventPublisherTest {

  @Mock private OutboxRepository outboxRepository;
  private ObjectMapper objectMapper;
  private OutboxDomainEventPublisher publisher;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    publisher = new OutboxDomainEventPublisher(outboxRepository, objectMapper);
  }

  @Test
  @DisplayName("Should serialize UserEvent and save OutboxEvent")
  void testPublish_UserEvent_SavesOutboxEvent() {
    Instant now = Instant.parse("2026-08-31T12:00:00Z");
    UserEvent userEvent =
        UserEvent.builder()
            .eventId("evt-123")
            .eventType("USER_CREATED")
            .userId(42L)
            .username("alice")
            .fullName("Alice Smith")
            .email("alice@example.com")
            .timestamp(now)
            .build();

    publisher.publish("USER", "42", "USER_CREATED", userEvent);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());

    OutboxEvent saved = captor.getValue();
    assertThat(saved).isNotNull();
    assertThat(saved.getId()).isEqualTo("evt-123");
    assertThat(saved.getAggregateType()).isEqualTo("USER");
    assertThat(saved.getAggregateId()).isEqualTo("42");
    assertThat(saved.getEventType()).isEqualTo("USER_CREATED");
    assertThat(saved.getCreatedAt()).isEqualTo(now);
    assertThat(saved.isProcessed()).isFalse();
    assertThat(saved.getPayload()).contains("\"userId\":42");
    assertThat(saved.getPayload()).contains("\"username\":\"alice\"");
    assertThat(saved.getPayload()).contains("\"email\":\"alice@example.com\"");
  }

  @Test
  @DisplayName("Should serialize FriendshipEvent and save OutboxEvent")
  void testPublish_FriendshipEvent_SavesOutboxEvent() {
    Instant now = Instant.parse("2026-08-31T12:00:00Z");
    FriendshipEvent friendshipEvent =
        FriendshipEvent.builder()
            .eventId("evt-999")
            .eventType("FRIENDSHIP_ACCEPTED")
            .userId(10L)
            .friendId(20L)
            .status("ACCEPTED")
            .timestamp(now)
            .build();

    publisher.publish("FRIENDSHIP", "50", "FRIENDSHIP_ACCEPTED", friendshipEvent);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());

    OutboxEvent saved = captor.getValue();
    assertThat(saved).isNotNull();
    assertThat(saved.getId()).isEqualTo("evt-999");
    assertThat(saved.getAggregateType()).isEqualTo("FRIENDSHIP");
    assertThat(saved.getAggregateId()).isEqualTo("50");
    assertThat(saved.getEventType()).isEqualTo("FRIENDSHIP_ACCEPTED");
    assertThat(saved.getCreatedAt()).isEqualTo(now);
    assertThat(saved.isProcessed()).isFalse();
    assertThat(saved.getPayload()).contains("\"userId\":10");
    assertThat(saved.getPayload()).contains("\"friendId\":20");
    assertThat(saved.getPayload()).contains("\"status\":\"ACCEPTED\"");
  }

  @Test
  @DisplayName("Should generate UUID and timestamp when payload does not provide them")
  void testPublish_GenericPayload_GeneratesIdAndTimestamp() {
    Object genericPayload = java.util.Map.of("message", "hello");

    publisher.publish("TEST", "1", "GENERIC_EVENT", genericPayload);

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());

    OutboxEvent saved = captor.getValue();
    assertThat(saved).isNotNull();
    assertThat(saved.getId()).isNotEmpty();
    assertThat(saved.getAggregateType()).isEqualTo("TEST");
    assertThat(saved.getAggregateId()).isEqualTo("1");
    assertThat(saved.getEventType()).isEqualTo("GENERIC_EVENT");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.isProcessed()).isFalse();
    assertThat(saved.getPayload()).contains("\"message\":\"hello\"");
  }

  @Test
  @DisplayName("Should throw RuntimeException if JSON serialization fails")
  void testPublish_SerializationFails_ThrowsRuntimeException() throws Exception {
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    when(mockMapper.writeValueAsString(any()))
        .thenThrow(new JsonProcessingException("Serialization error") {});

    OutboxDomainEventPublisher failPublisher =
        new OutboxDomainEventPublisher(outboxRepository, mockMapper);

    assertThatThrownBy(() -> failPublisher.publish("USER", "42", "USER_CREATED", new Object()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to serialize OutboxEvent payload for user: 42");
  }
}
