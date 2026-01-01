package com.splitz.user.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.exception.ResourceNotFoundException;
import com.splitz.user.mapper.FriendshipMapper;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Friendship;
import com.splitz.user.model.FriendshipStatus;
import com.splitz.user.model.User;
import com.splitz.user.repository.FriendshipRepository;
import com.splitz.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("FriendshipService Unit Tests")
@SuppressWarnings("unused")
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipMapper friendshipMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FriendshipService friendshipService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");

        bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
    }

    // ============ SEND FRIEND REQUEST ============

    @Nested
    @DisplayName("sendFriendRequest")
    class SendFriendRequestTests {

        @Test
        @DisplayName("creates a PENDING request")
        void createsPendingRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(friendshipRepository.existsBetweenUsers(alice, bob)).thenReturn(false);

            Friendship saved = Friendship.createRequest(alice, bob);
            saved.setId(10L);

            FriendshipDTO dto = new FriendshipDTO(10L, 1L, 2L, FriendshipStatus.PENDING, null, null);

            when(friendshipRepository.save(any(Friendship.class))).thenReturn(saved);
            when(friendshipMapper.toDTO(saved)).thenReturn(dto);

            FriendshipDTO result = friendshipService.sendFriendRequest(1L, 2L);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getRequesterId()).isEqualTo(1L);
            assertThat(result.getAddresseeId()).isEqualTo(2L);
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        @DisplayName("rejects self-friendship")
        void rejectsSelfFriendship() {
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yourself");

            verify(userRepository, never()).findById(anyLong());
            verify(friendshipRepository, never()).save(any(Friendship.class));
        }

        @Test
        @DisplayName("rejects duplicate requests between same users")
        void rejectsDuplicateRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
            when(friendshipRepository.existsBetweenUsers(alice, bob)).thenReturn(true);

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(friendshipRepository, never()).save(any(Friendship.class));
        }

        @Test
        @DisplayName("throws 404-style exception when requester not found")
        void requesterNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> friendshipService.sendFriendRequest(1L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ============ ACCEPT / REJECT ============

    @Nested
    @DisplayName("acceptFriendRequest")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("addressee can accept a PENDING request")
        void addresseeCanAccept() {
            Friendship friendship = Friendship.createRequest(alice, bob);
            friendship.setId(99L);

            when(friendshipRepository.findById(99L)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(friendship)).thenReturn(friendship);

            FriendshipDTO dto = new FriendshipDTO(99L, 1L, 2L, FriendshipStatus.ACCEPTED, null, null);
            when(friendshipMapper.toDTO(friendship)).thenReturn(dto);

            FriendshipDTO result = friendshipService.acceptFriendRequest(99L, 2L);

            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        }

        @Test
        @DisplayName("requester cannot accept")
        void requesterCannotAccept() {
            Friendship friendship = Friendship.createRequest(alice, bob);
            friendship.setId(99L);

            when(friendshipRepository.findById(99L)).thenReturn(Optional.of(friendship));

            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(99L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("addressee");

            verify(friendshipRepository, never()).save(any(Friendship.class));
        }

        @Test
        @DisplayName("cannot accept non-pending friendship")
        void cannotAcceptNonPending() {
            Friendship friendship = Friendship.createRequest(alice, bob);
            friendship.setId(99L);
            friendship.accept();

            when(friendshipRepository.findById(99L)).thenReturn(Optional.of(friendship));

            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(99L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot accept");

            verify(friendshipRepository, never()).save(any(Friendship.class));
        }
    }

    @Nested
    @DisplayName("rejectFriendRequest")
    class RejectFriendRequestTests {

        @Test
        @DisplayName("addressee can reject a PENDING request")
        void addresseeCanReject() {
            Friendship friendship = Friendship.createRequest(alice, bob);
            friendship.setId(100L);

            when(friendshipRepository.findById(100L)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(friendship)).thenReturn(friendship);

            FriendshipDTO dto = new FriendshipDTO(100L, 1L, 2L, FriendshipStatus.REJECTED, null, null);
            when(friendshipMapper.toDTO(friendship)).thenReturn(dto);

            FriendshipDTO result = friendshipService.rejectFriendRequest(100L, 2L);

            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        }

        @Test
        @DisplayName("requester cannot reject")
        void requesterCannotReject() {
            Friendship friendship = Friendship.createRequest(alice, bob);
            friendship.setId(100L);

            when(friendshipRepository.findById(100L)).thenReturn(Optional.of(friendship));

            assertThatThrownBy(() -> friendshipService.rejectFriendRequest(100L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("addressee");

            verify(friendshipRepository, never()).save(any(Friendship.class));
        }
    }

    // ============ QUERIES ============

    @Nested
    @DisplayName("getPendingRequests")
    class GetPendingRequestsTests {

        @Test
        @DisplayName("returns pending incoming requests")
        void returnsPendingIncomingRequests() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

            Friendship f1 = Friendship.createRequest(alice, bob);
            Friendship f2 = Friendship.createRequest(new User() {
                {
                    setId(3L);
                }
            }, bob);

            List<Friendship> pending = List.of(f1, f2);
            List<FriendshipDTO> pendingDtos = List.of(
                    new FriendshipDTO(1L, 1L, 2L, FriendshipStatus.PENDING, null, null),
                    new FriendshipDTO(2L, 3L, 2L, FriendshipStatus.PENDING, null, null));

            when(friendshipRepository.findByAddresseeAndStatus(bob, FriendshipStatus.PENDING)).thenReturn(pending);
            when(friendshipMapper.toDTOs(pending)).thenReturn(pendingDtos);

            List<FriendshipDTO> result = friendshipService.getPendingRequests(2L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("getAcceptedFriends")
    class GetAcceptedFriendsTests {

        @Test
        @DisplayName("returns the other party as UserDTO")
        void returnsOtherPartyAsUserDTO() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

            Friendship f1 = Friendship.builder()
                    .requester(alice)
                    .addressee(bob)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            User charlie = new User();
            charlie.setId(3L);
            charlie.setUsername("charlie");

            Friendship f2 = Friendship.builder()
                    .requester(charlie)
                    .addressee(alice)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            when(friendshipRepository.findAcceptedFriendships(alice)).thenReturn(List.of(f1, f2));

            UserDTO bobDto = new UserDTO(2L, "bob", "bob@example.com", "Bob", null, null);
            UserDTO charlieDto = new UserDTO(3L, "charlie", "charlie@example.com", "Charlie", null, null);

            when(userMapper.toDTO(bob)).thenReturn(bobDto);
            when(userMapper.toDTO(charlie)).thenReturn(charlieDto);

            List<UserDTO> result = friendshipService.getAcceptedFriends(1L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserDTO::getId).containsExactly(2L, 3L);
        }
    }

    // ============ REMOVE FRIEND ============

    @Nested
    @DisplayName("removeFriend")
    class RemoveFriendTests {

        @Test
        @DisplayName("either party can remove an ACCEPTED friendship")
        void canRemoveAcceptedFriendship() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

            Friendship friendship = Friendship.builder()
                    .id(200L)
                    .requester(alice)
                    .addressee(bob)
                    .status(FriendshipStatus.ACCEPTED)
                    .build();

            when(friendshipRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.of(friendship));

            friendshipService.removeFriend(1L, 2L);

            verify(friendshipRepository).delete(eq(friendship));
        }

        @Test
        @DisplayName("cannot remove non-accepted friendships")
        void cannotRemoveNonAccepted() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

            Friendship friendship = Friendship.builder()
                    .id(200L)
                    .requester(alice)
                    .addressee(bob)
                    .status(FriendshipStatus.PENDING)
                    .build();

            when(friendshipRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.of(friendship));

            assertThatThrownBy(() -> friendshipService.removeFriend(1L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not accepted");

            verify(friendshipRepository, never()).delete(any(Friendship.class));
        }

        @Test
        @DisplayName("throws not found when no friendship exists")
        void friendshipNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
            when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

            when(friendshipRepository.findBetweenUsers(alice, bob)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> friendshipService.removeFriend(1L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Friendship not found");

            verify(friendshipRepository, never()).delete(any(Friendship.class));
        }

        @Test
        @DisplayName("rejects removing yourself")
        void rejectsRemovingYourself() {
            assertThatThrownBy(() -> friendshipService.removeFriend(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yourself");

            verify(userRepository, never()).findById(anyLong());
            verify(friendshipRepository, never()).findBetweenUsers(any(), any());
        }
    }
}
