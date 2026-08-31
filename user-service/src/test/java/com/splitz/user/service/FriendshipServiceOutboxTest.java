package com.splitz.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.event.FriendshipEvent;
import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.event.DomainEventPublisher;
import com.splitz.user.mapper.FriendshipMapper;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Friendship;
import com.splitz.user.model.User;
import com.splitz.user.repository.FriendshipRepository;
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
@DisplayName("FriendshipService Domain Event Unit Tests")
class FriendshipServiceOutboxTest {

  @Mock private FriendshipRepository friendshipRepository;
  @Mock private UserRepository userRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private FriendshipMapper friendshipMapper;
  @Mock private UserMapper userMapper;

  @InjectMocks private FriendshipService friendshipService;

  @Test
  @DisplayName("Should publish FRIENDSHIP_ACCEPTED event when friend request is accepted")
  void testAcceptFriendRequest_PublishesEvent() {
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

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher)
        .publish(eq("FRIENDSHIP"), eq("50"), eq("FRIENDSHIP_ACCEPTED"), payloadCaptor.capture());

    Object payload = payloadCaptor.getValue();
    assertThat(payload).isInstanceOf(FriendshipEvent.class);
    FriendshipEvent event = (FriendshipEvent) payload;
    assertThat(event.getUserId()).isEqualTo(10L);
    assertThat(event.getFriendId()).isEqualTo(20L);
    assertThat(event.getStatus()).isEqualTo("ACCEPTED");
    assertThat(event.getEventType()).isEqualTo("FRIENDSHIP_ACCEPTED");
  }
}
