package com.splitz.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.event.FriendshipEvent;
import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.exception.ResourceNotFoundException;
import com.splitz.user.mapper.FriendshipMapper;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Friendship;
import com.splitz.user.model.FriendshipStatus;
import com.splitz.user.model.OutboxEvent;
import com.splitz.user.model.User;
import com.splitz.user.repository.FriendshipRepository;
import com.splitz.user.repository.OutboxRepository;
import com.splitz.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final UserRepository userRepository;
  private final OutboxRepository outboxRepository;
  private final FriendshipMapper friendshipMapper;
  private final UserMapper userMapper;
  private final ObjectMapper objectMapper;

  @Autowired
  public FriendshipService(
      FriendshipRepository friendshipRepository,
      UserRepository userRepository,
      OutboxRepository outboxRepository,
      FriendshipMapper friendshipMapper,
      UserMapper userMapper,
      ObjectMapper objectMapper) {
    this.friendshipRepository = friendshipRepository;
    this.userRepository = userRepository;
    this.outboxRepository = outboxRepository;
    this.friendshipMapper = friendshipMapper;
    this.userMapper = userMapper;
    this.objectMapper =
        objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
  }

  public FriendshipService(
      FriendshipRepository friendshipRepository,
      UserRepository userRepository,
      FriendshipMapper friendshipMapper,
      UserMapper userMapper) {
    this(friendshipRepository, userRepository, null, friendshipMapper, userMapper, null);
  }

  /** Send a friend request from requester to addressee. */
  @Transactional
  public FriendshipDTO sendFriendRequest(Long requesterId, Long addresseeId) {
    Objects.requireNonNull(requesterId, "requesterId");
    Objects.requireNonNull(addresseeId, "addresseeId");
    if (Objects.equals(requesterId, addresseeId)) {
      throw new IllegalArgumentException("Cannot send friend request to yourself");
    }

    User requester = getUserOrThrow(requesterId);
    User addressee = getUserOrThrow(addresseeId);

    if (friendshipRepository.existsBetweenUsers(requester, addressee)) {
      throw new IllegalArgumentException("Friendship already exists between users");
    }

    Friendship friendship = Friendship.createRequest(requester, addressee);
    Friendship saved =
        Objects.requireNonNull(friendshipRepository.save(friendship), "saved friendship");

    saveFriendshipOutboxEvent("FRIENDSHIP_REQUESTED", saved);

    return friendshipMapper.toDTO(saved);
  }

  /** Accept a pending friend request. Only the addressee may accept. */
  @Transactional
  public FriendshipDTO acceptFriendRequest(Long friendshipId, Long actingUserId) {
    Objects.requireNonNull(friendshipId, "friendshipId");
    Objects.requireNonNull(actingUserId, "actingUserId");
    Friendship friendship = getFriendshipOrThrow(friendshipId);

    if (!friendship.isAddressee(actingUserId)) {
      throw new IllegalArgumentException("Only the addressee can accept this friendship request");
    }

    friendship.accept();
    Friendship saved =
        Objects.requireNonNull(friendshipRepository.save(friendship), "saved friendship");

    saveFriendshipOutboxEvent("FRIENDSHIP_ACCEPTED", saved);

    return friendshipMapper.toDTO(saved);
  }

  /** Reject a pending friend request. Only the addressee may reject. */
  @Transactional
  public FriendshipDTO rejectFriendRequest(Long friendshipId, Long actingUserId) {
    Objects.requireNonNull(friendshipId, "friendshipId");
    Objects.requireNonNull(actingUserId, "actingUserId");
    Friendship friendship = getFriendshipOrThrow(friendshipId);

    if (!friendship.isAddressee(actingUserId)) {
      throw new IllegalArgumentException("Only the addressee can reject this friendship request");
    }

    friendship.reject();
    Friendship saved =
        Objects.requireNonNull(friendshipRepository.save(friendship), "saved friendship");

    saveFriendshipOutboxEvent("FRIENDSHIP_REJECTED", saved);

    return friendshipMapper.toDTO(saved);
  }

  /** List pending requests for a user. */
  public List<FriendshipDTO> getPendingRequests(Long userId, String direction) {
    Objects.requireNonNull(userId, "userId");
    User user = getUserOrThrow(userId);

    List<Friendship> pending;
    if ("OUTGOING".equalsIgnoreCase(direction)) {
      pending = friendshipRepository.findByRequesterAndStatus(user, FriendshipStatus.PENDING);
    } else {
      pending = friendshipRepository.findByAddresseeAndStatus(user, FriendshipStatus.PENDING);
    }
    return friendshipMapper.toDTOs(pending);
  }

  /** List accepted friends for a user. */
  public List<UserDTO> getAcceptedFriends(Long userId) {
    Objects.requireNonNull(userId, "userId");
    User user = getUserOrThrow(userId);
    List<Friendship> accepted = friendshipRepository.findAcceptedFriendships(user);

    return accepted.stream()
        .map(
            friendship -> {
              User other =
                  friendship.getRequester().getId().equals(userId)
                      ? friendship.getAddressee()
                      : friendship.getRequester();
              return userMapper.toDTO(other);
            })
        .toList();
  }

  /** Remove a friendship or cancel a pending request. Either party may remove/cancel. */
  @Transactional
  public void removeFriend(Long userId, Long friendId) {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(friendId, "friendId");
    if (Objects.equals(userId, friendId)) {
      throw new IllegalArgumentException("Cannot remove yourself");
    }

    User user = getUserOrThrow(userId);
    User friend = getUserOrThrow(friendId);

    Friendship friendship =
        friendshipRepository
            .findBetweenUsers(user, friend)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Friendship not found between user " + userId + " and user " + friendId));

    if (!friendship.isActive() && !friendship.isPending()) {
      throw new IllegalStateException(
          "Cannot remove friendship: friendship is neither accepted nor pending");
    }

    friendshipRepository.delete(friendship);

    saveFriendshipOutboxEvent("FRIENDSHIP_REMOVED", friendship);
  }

  private User getUserOrThrow(Long userId) {
    Objects.requireNonNull(userId, "userId");
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
  }

  private Friendship getFriendshipOrThrow(Long friendshipId) {
    Objects.requireNonNull(friendshipId, "friendshipId");
    return friendshipRepository
        .findById(friendshipId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Friendship not found with id: " + friendshipId));
  }

  private void saveFriendshipOutboxEvent(String eventType, Friendship friendship) {
    if (outboxRepository == null) {
      return;
    }
    try {
      FriendshipEvent event =
          FriendshipEvent.builder()
              .eventId(UUID.randomUUID().toString())
              .eventType(eventType)
              .userId(friendship.getRequester().getId())
              .friendId(friendship.getAddressee().getId())
              .status(friendship.getStatus() != null ? friendship.getStatus().name() : "REMOVED")
              .timestamp(Instant.now())
              .build();

      String payload = objectMapper.writeValueAsString(event);

      OutboxEvent outboxEvent =
          OutboxEvent.builder()
              .id(event.getEventId())
              .aggregateType("FRIENDSHIP")
              .aggregateId(String.valueOf(friendship.getId()))
              .eventType(eventType)
              .payload(payload)
              .createdAt(event.getTimestamp())
              .processed(false)
              .build();

      outboxRepository.save(outboxEvent);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to serialize OutboxEvent payload for friendship: " + friendship.getId(), e);
    }
  }
}
