package com.splitz.expense.repository;

import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementAllocationRepository extends JpaRepository<SettlementAllocation, Long> {

  List<SettlementAllocation> findByGroupId(Long groupId);

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
      "SELECT COALESCE(SUM(sa.amount), 0) FROM SettlementAllocation sa JOIN sa.payment p WHERE "
          + "sa.groupId = :groupId AND p.payerId = :userId AND p.status = :status")
  BigDecimal calculateTotalSettlementsPaidByUserInGroup(
      @Param("userId") Long userId,
      @Param("groupId") Long groupId,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COALESCE(SUM(sa.amount), 0) FROM SettlementAllocation sa JOIN sa.payment p WHERE "
          + "sa.groupId = :groupId AND p.payeeId = :userId AND p.status = :status")
  BigDecimal calculateTotalSettlementsReceivedByUserInGroup(
      @Param("userId") Long userId,
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
