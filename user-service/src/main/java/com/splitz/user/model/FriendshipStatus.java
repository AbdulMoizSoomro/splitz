package com.splitz.user.model;

/** Represents the status of a friendship request between two users. */
public enum FriendshipStatus {
  /** Friend request has been sent but not yet acted upon. */
  PENDING,

  /** Friend request has been accepted by the addressee. */
  ACCEPTED,

  /** Friend request has been rejected by the addressee. */
  REJECTED,

  /** One user has blocked the other. */
  BLOCKED
}
