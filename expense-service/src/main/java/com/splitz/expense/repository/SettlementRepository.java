package com.splitz.expense.repository;

import com.splitz.expense.model.Settlement;
import com.splitz.expense.model.SettlementStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Settlement s WHERE s.id = :id")
  Optional<Settlement> findByIdWithLock(@Param("id") Long id);

  List<Settlement> findByGroupId(Long groupId);

  List<Settlement> findByPayerIdOrPayeeId(Long payerId, Long payeeId);

  @Query(
      "SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id IN :groupIds AND"
          + " s.payerId = :payerId AND s.payeeId = :payeeId AND s.status = :status")
  BigDecimal calculateTotalSettledBetweenUsers(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("groupIds") Collection<Long> groupIds,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.payerId = :payerId AND s.payeeId = :payeeId AND s.status = :status")
  BigDecimal calculateTotalSettledBetweenUsersGlobally(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.payerId = :userId AND s.status = :status")
  BigDecimal calculateTotalSettlementsPaidByUserInGroup(
      @Param("userId") Long userId,
      @Param("groupId") Long groupId,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.payeeId = :userId AND s.status = :status")
  BigDecimal calculateTotalSettlementsReceivedByUserInGroup(
      @Param("userId") Long userId,
      @Param("groupId") Long groupId,
      @Param("status") SettlementStatus status);
}
