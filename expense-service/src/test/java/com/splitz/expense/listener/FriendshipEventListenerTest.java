package com.splitz.expense.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.FriendshipEvent;
import com.splitz.expense.model.ProcessedEvent;
import com.splitz.expense.model.ReplicatedFriendship;
import com.splitz.expense.repository.ProcessedEventRepository;
import com.splitz.expense.repository.ReplicatedFriendshipRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendshipEventListener Unit Tests")
class FriendshipEventListenerTest {

  @Mock private ReplicatedFriendshipRepository replicatedFriendshipRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @InjectMocks private FriendshipEventListener friendshipEventListener;

  @Test
  @DisplayName("Should process FriendshipEvent, update replicated_friendships, and record event_id")
  void testHandleFriendshipEvent_NewEvent_UpdatesReplicatedFriendships() throws Exception {
    FriendshipEvent event =
        FriendshipEvent.builder()
            .eventId("evt-200")
            .eventType("FRIENDSHIP_ACCEPTED")
            .userId(10L)
            .friendId(20L)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

    String json = objectMapper.writeValueAsString(event);

    when(processedEventRepository.existsById("evt-200")).thenReturn(false);

    friendshipEventListener.handleFriendshipEvent(json);

    ArgumentCaptor<ReplicatedFriendship> friendshipCaptor =
        ArgumentCaptor.forClass(ReplicatedFriendship.class);
    verify(replicatedFriendshipRepository).save(friendshipCaptor.capture());
    assertThat(friendshipCaptor.getValue().getUserId()).isEqualTo(10L);
    assertThat(friendshipCaptor.getValue().getFriendId()).isEqualTo(20L);
    assertThat(friendshipCaptor.getValue().getStatus()).isEqualTo("ACCEPTED");

    ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
    verify(processedEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventId()).isEqualTo("evt-200");
  }

  @Test
  @DisplayName("Should ignore duplicate FriendshipEvent if already processed")
  void testHandleFriendshipEvent_DuplicateEvent_Ignored() throws Exception {
    FriendshipEvent event =
        FriendshipEvent.builder()
            .eventId("evt-200")
            .eventType("FRIENDSHIP_ACCEPTED")
            .userId(10L)
            .friendId(20L)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

    String json = objectMapper.writeValueAsString(event);

    when(processedEventRepository.existsById("evt-200")).thenReturn(true);

    friendshipEventListener.handleFriendshipEvent(json);

    verify(replicatedFriendshipRepository, never()).save(any());
  }
}
