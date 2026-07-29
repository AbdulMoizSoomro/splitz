package com.splitz.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.mapper.FriendshipMapper;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Friendship;
import com.splitz.user.model.OutboxEvent;
import com.splitz.user.model.User;
import com.splitz.user.repository.FriendshipRepository;
import com.splitz.user.repository.OutboxRepository;
import com.splitz.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendshipService Outbox Unit Tests")
class FriendshipServiceOutboxTest {

  @Mock private FriendshipRepository friendshipRepository;
  @Mock private UserRepository userRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private FriendshipMapper friendshipMapper;
  @Mock private UserMapper userMapper;

  @InjectMocks private FriendshipService friendshipService;

  @Test
  @DisplayName("Should save FRIENDSHIP_ACCEPTED OutboxEvent when friend request is accepted")
  void testAcceptFriendRequest_SavesOutboxEvent() {
    User requester = new User();
    requester.setId(10L);

    User addressee = new User();
    addressee.setId(20L);

    Friendship friendship = Friendship.createRequest(requester, addressee);
    friendship.setId(50L);

    when(friendshipRepository.findById(50L)).thenReturn(Optional.of(friendship));
    when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);
    when(friendshipMapper.toDTO(friendship)).thenReturn(new FriendshipDTO());

    friendshipService.acceptFriendRequest(50L, 20L);

    ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(outboxCaptor.capture());

    OutboxEvent savedEvent = outboxCaptor.getValue();
    assertThat(savedEvent).isNotNull();
    assertThat(savedEvent.getAggregateType()).isEqualTo("FRIENDSHIP");
    assertThat(savedEvent.getAggregateId()).isEqualTo("50");
    assertThat(savedEvent.getEventType()).isEqualTo("FRIENDSHIP_ACCEPTED");
    assertThat(savedEvent.getPayload()).contains("\"userId\":10");
    assertThat(savedEvent.getPayload()).contains("\"friendId\":20");
  }
}
