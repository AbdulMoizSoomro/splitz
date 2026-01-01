package com.splitz.user.service;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

@Service
public class FriendshipService {

    @Autowired
    private final FriendshipRepository friendshipRepository;

    @Autowired
    private final UserRepository userRepository;

    @Autowired
    private final FriendshipMapper friendshipMapper;

    @Autowired
    private final UserMapper userMapper;

    public FriendshipService(
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            FriendshipMapper friendshipMapper,
            UserMapper userMapper) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.friendshipMapper = friendshipMapper;
        this.userMapper = userMapper;
    }

    /**
     * Send a friend request from requester to addressee.
     */
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
        Friendship saved = Objects.requireNonNull(friendshipRepository.save(friendship), "saved friendship");
        return friendshipMapper.toDTO(saved);
    }

    /**
     * Accept a pending friend request.
     * Only the addressee may accept.
     */
    public FriendshipDTO acceptFriendRequest(Long friendshipId, Long actingUserId) {
        Objects.requireNonNull(friendshipId, "friendshipId");
        Objects.requireNonNull(actingUserId, "actingUserId");
        Friendship friendship = getFriendshipOrThrow(friendshipId);

        if (!friendship.isAddressee(actingUserId)) {
            throw new IllegalArgumentException("Only the addressee can accept this friendship request");
        }

        friendship.accept();
        Friendship saved = Objects.requireNonNull(friendshipRepository.save(friendship), "saved friendship");
        return friendshipMapper.toDTO(saved);
    }

    /**
     * Reject a pending friend request.
     * Only the addressee may reject.
     */
    public FriendshipDTO rejectFriendRequest(Long friendshipId, Long actingUserId) {
        Objects.requireNonNull(friendshipId, "friendshipId");
        Objects.requireNonNull(actingUserId, "actingUserId");
        Friendship friendship = getFriendshipOrThrow(friendshipId);

        if (!friendship.isAddressee(actingUserId)) {
            throw new IllegalArgumentException("Only the addressee can reject this friendship request");
        }

        friendship.reject();
        Friendship saved = Objects.requireNonNull(friendshipRepository.save(friendship), "saved friendship");
        return friendshipMapper.toDTO(saved);
    }

    /**
     * List pending incoming requests for a user (where the user is the addressee).
     */
    public List<FriendshipDTO> getPendingRequests(Long userId) {
        Objects.requireNonNull(userId, "userId");
        User user = getUserOrThrow(userId);
        List<Friendship> pending = friendshipRepository.findByAddresseeAndStatus(user, FriendshipStatus.PENDING);
        return friendshipMapper.toDTOs(pending);
    }

    /**
     * List accepted friends for a user.
     */
    public List<UserDTO> getAcceptedFriends(Long userId) {
        Objects.requireNonNull(userId, "userId");
        User user = getUserOrThrow(userId);
        List<Friendship> accepted = friendshipRepository.findAcceptedFriendships(user);

        return accepted.stream()
                .map(friendship -> {
                    User other = friendship.getRequester().getId().equals(userId)
                            ? friendship.getAddressee()
                            : friendship.getRequester();
                    return userMapper.toDTO(other);
                })
                .toList();
    }

    /**
     * Remove an accepted friendship. Either party may remove.
     */
    public void removeFriend(Long userId, Long friendId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(friendId, "friendId");
        if (Objects.equals(userId, friendId)) {
            throw new IllegalArgumentException("Cannot remove yourself");
        }

        User user = getUserOrThrow(userId);
        User friend = getUserOrThrow(friendId);

        Friendship friendship = friendshipRepository.findBetweenUsers(user, friend)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Friendship not found between user " + userId + " and user " + friendId));

        if (!friendship.isActive()) {
            throw new IllegalStateException("Cannot remove friendship: friendship is not accepted");
        }

        friendshipRepository.delete(friendship);
    }

    private User getUserOrThrow(Long userId) {
        Objects.requireNonNull(userId, "userId");
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private Friendship getFriendshipOrThrow(Long friendshipId) {
        Objects.requireNonNull(friendshipId, "friendshipId");
        return friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found with id: " + friendshipId));
    }
}
