package com.splitz.expense.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.UserEvent;
import com.splitz.expense.model.ProcessedEvent;
import com.splitz.expense.model.ReplicatedUser;
import com.splitz.expense.repository.ProcessedEventRepository;
import com.splitz.expense.repository.ReplicatedUserRepository;
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
@DisplayName("UserEventListener Unit Tests")
class UserEventListenerTest {

  @Mock private ReplicatedUserRepository replicatedUserRepository;
  @Mock private ProcessedEventRepository processedEventRepository;

  @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @InjectMocks private UserEventListener userEventListener;

  @Test
  @DisplayName("Should process UserEvent, update replicated_users, and record event_id")
  void testHandleUserEvent_NewEvent_UpdatesReplicatedUsersAndProcessedEvents() throws Exception {
    UserEvent event =
        UserEvent.builder()
            .eventId("evt-100")
            .eventType("USER_CREATED")
            .userId(42L)
            .username("johndoe")
            .fullName("John Doe")
            .email("john@example.com")
            .timestamp(Instant.now())
            .build();

    String json = objectMapper.writeValueAsString(event);

    when(processedEventRepository.existsById("evt-100")).thenReturn(false);

    userEventListener.handleUserEvent(json);

    ArgumentCaptor<ReplicatedUser> userCaptor = ArgumentCaptor.forClass(ReplicatedUser.class);
    verify(replicatedUserRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getUserId()).isEqualTo(42L);
    assertThat(userCaptor.getValue().getUsername()).isEqualTo("johndoe");

    ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
    verify(processedEventRepository).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventId()).isEqualTo("evt-100");
  }

  @Test
  @DisplayName("Should ignore duplicate UserEvent if already processed")
  void testHandleUserEvent_DuplicateEvent_Ignored() throws Exception {
    UserEvent event =
        UserEvent.builder()
            .eventId("evt-100")
            .eventType("USER_CREATED")
            .userId(42L)
            .username("johndoe")
            .timestamp(Instant.now())
            .build();

    String json = objectMapper.writeValueAsString(event);

    when(processedEventRepository.existsById("evt-100")).thenReturn(true);

    userEventListener.handleUserEvent(json);

    verify(replicatedUserRepository, never()).save(any());
  }
}
