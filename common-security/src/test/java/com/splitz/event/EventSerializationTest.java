package com.splitz.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void shouldSerializeAndDeserializeUserEvent() throws Exception {
    UserEvent event =
        UserEvent.builder()
            .eventId("evt-101")
            .eventType("USER_CREATED")
            .userId(42L)
            .username("johndoe")
            .fullName("John Doe")
            .email("john@example.com")
            .timestamp(Instant.now())
            .build();

    String json = objectMapper.writeValueAsString(event);
    assertNotNull(json);

    UserEvent deserialized = objectMapper.readValue(json, UserEvent.class);
    assertEquals(event.getEventId(), deserialized.getEventId());
    assertEquals(event.getEventType(), deserialized.getEventType());
    assertEquals(event.getUserId(), deserialized.getUserId());
    assertEquals(event.getUsername(), deserialized.getUsername());
    assertEquals(event.getFullName(), deserialized.getFullName());
    assertEquals(event.getEmail(), deserialized.getEmail());
  }

  @Test
  void shouldSerializeAndDeserializeFriendshipEvent() throws Exception {
    FriendshipEvent event =
        FriendshipEvent.builder()
            .eventId("evt-102")
            .eventType("FRIENDSHIP_ACCEPTED")
            .userId(10L)
            .friendId(20L)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

    String json = objectMapper.writeValueAsString(event);
    assertNotNull(json);

    FriendshipEvent deserialized = objectMapper.readValue(json, FriendshipEvent.class);
    assertEquals(event.getEventId(), deserialized.getEventId());
    assertEquals(event.getEventType(), deserialized.getEventType());
    assertEquals(event.getUserId(), deserialized.getUserId());
    assertEquals(event.getFriendId(), deserialized.getFriendId());
    assertEquals(event.getStatus(), deserialized.getStatus());
  }
}
