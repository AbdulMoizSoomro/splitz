package com.splitz.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents a friendship relationship between two users. The requester initiates the friend
 * request, and the addressee receives it.
 */
@Entity
@Table(
    name = "friendships",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"requester_id", "addressee_id"})})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The user who initiated the friend request. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requester_id", nullable = false)
  private User requester;

  /** The user who receives the friend request. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "addressee_id", nullable = false)
  private User addressee;

  /** Current status of the friendship. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FriendshipStatus status;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /**
   * Creates a new pending friendship request.
   *
   * @param requester the user sending the request
   * @param addressee the user receiving the request
   * @return a new Friendship with PENDING status
   */
  public static Friendship createRequest(User requester, User addressee) {
    return Friendship.builder()
        .requester(requester)
        .addressee(addressee)
        .status(FriendshipStatus.PENDING)
        .build();
  }

  /**
   * Accepts the friendship request. Only valid when status is PENDING.
   *
   * @throws IllegalStateException if current status is not PENDING
   */
  public void accept() {
    if (this.status != FriendshipStatus.PENDING) {
      throw new IllegalStateException(
          "Cannot accept friendship: current status is " + this.status + ", expected PENDING");
    }
    this.status = FriendshipStatus.ACCEPTED;
  }

  /**
   * Rejects the friendship request. Only valid when status is PENDING.
   *
   * @throws IllegalStateException if current status is not PENDING
   */
  public void reject() {
    if (this.status != FriendshipStatus.PENDING) {
      throw new IllegalStateException(
          "Cannot reject friendship: current status is " + this.status + ", expected PENDING");
    }
    this.status = FriendshipStatus.REJECTED;
  }

  /** Blocks the other user. Can be done from any status. */
  public void block() {
    this.status = FriendshipStatus.BLOCKED;
  }

  /**
   * Checks if the friendship is currently active (accepted).
   *
   * @return true if status is ACCEPTED
   */
  public boolean isActive() {
    return this.status == FriendshipStatus.ACCEPTED;
  }

  /**
   * Checks if the friendship request is pending.
   *
   * @return true if status is PENDING
   */
  public boolean isPending() {
    return this.status == FriendshipStatus.PENDING;
  }

  /**
   * Checks if the given user is the requester of this friendship.
   *
   * @param userId the user ID to check
   * @return true if the user is the requester
   */
  public boolean isRequester(Long userId) {
    return this.requester != null && this.requester.getId().equals(userId);
  }

  /**
   * Checks if the given user is the addressee of this friendship.
   *
   * @param userId the user ID to check
   * @return true if the user is the addressee
   */
  public boolean isAddressee(Long userId) {
    return this.addressee != null && this.addressee.getId().equals(userId);
  }

  /**
   * Checks if the given user is involved in this friendship (either requester or addressee).
   *
   * @param userId the user ID to check
   * @return true if the user is either the requester or addressee
   */
  public boolean involvesUser(Long userId) {
    return isRequester(userId) || isAddressee(userId);
  }
}
