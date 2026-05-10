package com.splitz.expense.repository;

import com.splitz.expense.model.FriendshipSettlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipSettlementRepository extends JpaRepository<FriendshipSettlement, Long> {

  @Query(
      "SELECT fs FROM FriendshipSettlement fs WHERE "
          + "(fs.payerId = :userId1 AND fs.payeeId = :userId2) OR "
          + "(fs.payerId = :userId2 AND fs.payeeId = :userId1)")
  List<FriendshipSettlement> findBetweenUsers(
      @Param("userId1") Long userId1, @Param("userId2") Long userId2);

  List<FriendshipSettlement> findByPayerIdOrPayeeId(Long payerId, Long payeeId);
}
