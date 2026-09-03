package com.splitz.expense.repository;

import com.splitz.expense.model.Payment;
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

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.id = :id")
  Optional<Payment> findByIdWithLock(@Param("id") Long id);

  List<Payment> findByPayerIdOrPayeeId(Long payerId, Long payeeId);

  @Query(
      "SELECT p FROM Payment p WHERE (p.payerId = :userId1 AND p.payeeId = :userId2) OR (p.payerId = :userId2 AND p.payeeId = :userId1)")
  List<Payment> findBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

  List<Payment> findByGroupId(Long groupId);

  List<Payment> findByGroupIdAndStatusIn(Long groupId, Collection<SettlementStatus> statuses);

  @Query(
      "SELECT p FROM Payment p WHERE p.type = com.splitz.expense.model.PaymentType.DIRECT "
          + "AND ((p.payerId = :userId1 AND p.payeeId = :userId2) OR (p.payerId = :userId2 AND p.payeeId = :userId1))")
  List<Payment> findDirectBetweenUsers(
      @Param("userId1") Long userId1, @Param("userId2") Long userId2);

  @Query(
      "SELECT p.payerId AS userId, p.groupId AS groupId, SUM(p.amount) AS total "
          + "FROM Payment p WHERE p.type = com.splitz.expense.model.PaymentType.GROUP "
          + "AND p.groupId IN :groupIds AND p.payerId IN :userIds AND p.status IN :statuses "
          + "GROUP BY p.payerId, p.groupId")
  List<UserGroupAggregate> calculateTotalPaymentsPaidInGroups(
      @Param("userIds") Collection<Long> userIds,
      @Param("groupIds") Collection<Long> groupIds,
      @Param("statuses") Collection<SettlementStatus> statuses);

  @Query(
      "SELECT p.payeeId AS userId, p.groupId AS groupId, SUM(p.amount) AS total "
          + "FROM Payment p WHERE p.type = com.splitz.expense.model.PaymentType.GROUP "
          + "AND p.groupId IN :groupIds AND p.payeeId IN :userIds AND p.status IN :statuses "
          + "GROUP BY p.payeeId, p.groupId")
  List<UserGroupAggregate> calculateTotalPaymentsReceivedInGroups(
      @Param("userIds") Collection<Long> userIds,
      @Param("groupIds") Collection<Long> groupIds,
      @Param("statuses") Collection<SettlementStatus> statuses);

  @Query(
      "SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.type = com.splitz.expense.model.PaymentType.GROUP "
          + "AND p.groupId = :groupId AND p.payerId = :payerId AND p.payeeId = :payeeId AND p.status = :status")
  BigDecimal calculateTotalSettledBetweenUsersInGroup(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("groupId") Long groupId,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.type = com.splitz.expense.model.PaymentType.DIRECT "
          + "AND p.payerId = :payerId AND p.payeeId = :payeeId AND p.status = :status")
  BigDecimal calculateTotalDirectSettledBetweenUsers(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("status") SettlementStatus status);

  @Query(
      "SELECT COUNT(p) > 0 FROM Payment p WHERE p.groupId = :groupId "
          + "AND (p.payerId = :userId OR p.payeeId = :userId) AND p.status IN :statuses")
  boolean hasActivePaymentsInGroup(
      @Param("userId") Long userId,
      @Param("groupId") Long groupId,
      @Param("statuses") Collection<SettlementStatus> statuses);

  @Query(
      "SELECT p FROM Payment p WHERE p.type = com.splitz.expense.model.PaymentType.DIRECT "
          + "AND (p.payerId = :userId OR p.payeeId = :userId) AND p.status IN :statuses")
  List<Payment> findDirectPaymentsForUser(
      @Param("userId") Long userId, @Param("statuses") Collection<SettlementStatus> statuses);
}
