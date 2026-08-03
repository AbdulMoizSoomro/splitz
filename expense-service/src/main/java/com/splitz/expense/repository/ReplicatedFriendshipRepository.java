package com.splitz.expense.repository;

import com.splitz.expense.model.ReplicatedFriendship;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplicatedFriendshipRepository
    extends JpaRepository<ReplicatedFriendship, String> {

  @Query(
      "SELECT rf FROM ReplicatedFriendship rf WHERE "
          + "(rf.userId = :userId AND rf.friendId = :friendId) OR "
          + "(rf.userId = :friendId AND rf.friendId = :userId)")
  Optional<ReplicatedFriendship> findBetweenUsers(
      @Param("userId") Long userId, @Param("friendId") Long friendId);

  @Query(
      "SELECT rf FROM ReplicatedFriendship rf WHERE "
          + "(rf.userId = :userId OR rf.friendId = :userId) AND rf.status = 'ACCEPTED'")
  List<ReplicatedFriendship> findAcceptedFriendships(@Param("userId") Long userId);
}
