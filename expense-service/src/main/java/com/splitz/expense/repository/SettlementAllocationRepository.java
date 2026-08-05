package com.splitz.expense.repository;

import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementAllocationRepository extends JpaRepository<SettlementAllocation, Long> {

  List<SettlementAllocation> findByGroupId(Long groupId);

  List<SettlementAllocation> findByGroupIdIn(Collection<Long> groupIds);

  @Query(
      "SELECT p.payerId AS userId, sa.groupId AS groupId, SUM(sa.amount) AS total"
          + " FROM SettlementAllocation sa JOIN sa.payment p WHERE sa.groupId IN :groupIds"
          + " AND p.payerId IN :userIds AND p.status IN :statuses"
          + " GROUP BY p.payerId, sa.groupId")
  List<UserGroupAggregate> calculateTotalSettlementsPaidByUsersInGroups(
      @Param("userIds") Collection<Long> userIds,
      @Param("groupIds") Collection<Long> groupIds,
      @Param("statuses") Collection<SettlementStatus> statuses);

  @Query(
      "SELECT p.payeeId AS userId, sa.groupId AS groupId, SUM(sa.amount) AS total"
          + " FROM SettlementAllocation sa JOIN sa.payment p WHERE sa.groupId IN :groupIds"
          + " AND p.payeeId IN :userIds AND p.status IN :statuses"
          + " GROUP BY p.payeeId, sa.groupId")
  List<UserGroupAggregate> calculateTotalSettlementsReceivedByUsersInGroups(
      @Param("userIds") Collection<Long> userIds,
      @Param("groupIds") Collection<Long> groupIds,
      @Param("statuses") Collection<SettlementStatus> statuses);

  @Query(
      "SELECT COALESCE(SUM(sa.amount), 0) FROM SettlementAllocation sa JOIN sa.payment p WHERE sa.groupId IN :groupIds AND"
          + " p.payerId = :payerId AND p.payeeId = :payeeId AND p.status = :status")
  BigDecimal calculateTotalSettledBetweenUsers(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("groupIds") Collection<Long> groupIds,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COALESCE(SUM(sa.amount), 0) FROM SettlementAllocation sa JOIN sa.payment p WHERE "
          + "((:groupId IS NULL AND sa.groupId IS NULL) OR (sa.groupId = :groupId)) AND "
          + "p.payerId = :payerId AND p.payeeId = :payeeId AND p.status = :status")
  BigDecimal calculateTotalSettledBetweenUsersInGroup(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("groupId") Long groupId,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COUNT(sa) > 0 FROM SettlementAllocation sa JOIN sa.payment p WHERE "
          + "sa.groupId = :groupId AND (p.payerId = :userId OR p.payeeId = :userId) AND "
          + "p.status IN :statuses")
  boolean hasActiveSettlementsForUserInGroup(
      @Param("userId") Long userId,
      @Param("groupId") Long groupId,
      @Param("statuses") Collection<SettlementStatus> statuses);
}
