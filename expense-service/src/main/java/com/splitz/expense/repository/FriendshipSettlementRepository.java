package com.splitz.expense.repository;

import com.splitz.expense.model.FriendshipSettlement;
import com.splitz.expense.model.SettlementStatus;
import java.math.BigDecimal;
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

  List<FriendshipSettlement> findByGroupId(Long groupId);

  @Query(
      "SELECT COALESCE(SUM(fs.amount), 0) FROM FriendshipSettlement fs WHERE fs.payerId ="
          + " :payerId AND fs.payeeId = :payeeId AND fs.status = :status")
  BigDecimal calculateTotalSettledBetweenUsers(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("status") SettlementStatus status);
}
