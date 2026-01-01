package com.splitz.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.splitz.user.model.Friendship;
import com.splitz.user.model.FriendshipStatus;
import com.splitz.user.model.User;

/**
 * Repository for Friendship entity operations.
 */
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * Finds all friendships where the user is either the requester or addressee.
     * 
     * @param user the user to search for
     * @return list of all friendships involving the user
     */
    List<Friendship> findByRequesterOrAddressee(User requester, User addressee);

    /**
     * Finds all friendships involving a user with a specific status.
     * 
     * @param requester the user as requester
     * @param addressee the user as addressee
     * @param status    the friendship status to filter by
     * @return list of friendships matching the criteria
     */
    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = :status")
    List<Friendship> findByUserAndStatus(@Param("user") User user, @Param("status") FriendshipStatus status);

    /**
     * Finds all pending friend requests received by a user (where user is the
     * addressee).
     * 
     * @param addressee the user receiving the requests
     * @return list of pending friendships
     */
    List<Friendship> findByAddresseeAndStatus(User addressee, FriendshipStatus status);

    /**
     * Finds all pending friend requests received by a user with pagination.
     * 
     * @param addressee the user receiving the requests
     * @param status    the friendship status
     * @param pageable  pagination information
     * @return page of pending friendships
     */
    Page<Friendship> findByAddresseeAndStatus(User addressee, FriendshipStatus status, Pageable pageable);

    /**
     * Finds all pending friend requests sent by a user (where user is the
     * requester).
     * 
     * @param requester the user who sent the requests
     * @param status    the friendship status
     * @return list of pending friendships sent by the user
     */
    List<Friendship> findByRequesterAndStatus(User requester, FriendshipStatus status);

    /**
     * Finds all accepted friendships for a user.
     * 
     * @param user the user to find friends for
     * @return list of accepted friendships
     */
    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriendships(@Param("user") User user);

    /**
     * Finds all accepted friendships for a user with pagination.
     * 
     * @param user     the user to find friends for
     * @param pageable pagination information
     * @return page of accepted friendships
     */
    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = 'ACCEPTED'")
    Page<Friendship> findAcceptedFriendships(@Param("user") User user, Pageable pageable);

    /**
     * Checks if a friendship exists between two users (in either direction).
     * 
     * @param user1 first user
     * @param user2 second user
     * @return true if any friendship record exists between the users
     */
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE " +
            "(f.requester = :user1 AND f.addressee = :user2) OR " +
            "(f.requester = :user2 AND f.addressee = :user1)")
    boolean existsBetweenUsers(@Param("user1") User user1, @Param("user2") User user2);

    /**
     * Finds a friendship between two specific users (in either direction).
     * 
     * @param user1 first user
     * @param user2 second user
     * @return the friendship if it exists
     */
    @Query("SELECT f FROM Friendship f WHERE " +
            "(f.requester = :user1 AND f.addressee = :user2) OR " +
            "(f.requester = :user2 AND f.addressee = :user1)")
    Optional<Friendship> findBetweenUsers(@Param("user1") User user1, @Param("user2") User user2);

    /**
     * Finds a friendship where user1 is the requester and user2 is the addressee.
     * 
     * @param requester the user who sent the request
     * @param addressee the user who received the request
     * @return the friendship if it exists
     */
    Optional<Friendship> findByRequesterAndAddressee(User requester, User addressee);

    /**
     * Counts pending friend requests received by a user.
     * 
     * @param addressee the user receiving the requests
     * @return count of pending requests
     */
    long countByAddresseeAndStatus(User addressee, FriendshipStatus status);

    /**
     * Counts accepted friendships for a user.
     * 
     * @param user the user to count friends for
     * @return count of accepted friendships
     */
    @Query("SELECT COUNT(f) FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = 'ACCEPTED'")
    long countAcceptedFriendships(@Param("user") User user);
}
