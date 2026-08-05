package com.splitz.expense.repository;

import com.splitz.expense.model.Expense;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

  List<Expense> findByGroupId(Long groupId);

  List<Expense> findByGroupIdIn(Collection<Long> groupIds);

  @Query(
      "SELECT COALESCE(SUM(s.shareAmount), 0) FROM Expense e JOIN e.splits s WHERE e.group.id IN"
          + " :groupIds AND e.paidBy = :payerId AND s.userId = :payeeId")
  BigDecimal calculateTotalOwedBetweenUsers(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("groupIds") Collection<Long> groupIds);

  @Query(
      "SELECT COALESCE(SUM(s.shareAmount), 0) FROM Expense e JOIN e.splits s WHERE e.paidBy = :payerId AND s.userId = :payeeId")
  BigDecimal calculateTotalOwedBetweenUsersGlobally(
      @Param("payerId") Long payerId, @Param("payeeId") Long payeeId);

  @Query(
      "SELECT e.paidBy AS userId, e.group.id AS groupId, SUM(e.amount) AS total"
          + " FROM Expense e WHERE e.group.id IN :groupIds AND e.paidBy IN :userIds"
          + " GROUP BY e.paidBy, e.group.id")
  List<UserGroupAggregate> calculateTotalPaidByUsersInGroups(
      @Param("userIds") Collection<Long> userIds, @Param("groupIds") Collection<Long> groupIds);

  @Query(
      "SELECT s.userId AS userId, e.group.id AS groupId, SUM(s.shareAmount) AS total"
          + " FROM Expense e JOIN e.splits s WHERE e.group.id IN :groupIds AND s.userId IN :userIds"
          + " GROUP BY s.userId, e.group.id")
  List<UserGroupAggregate> calculateTotalShareForUsersInGroups(
      @Param("userIds") Collection<Long> userIds, @Param("groupIds") Collection<Long> groupIds);

  @Query(
      "SELECT DISTINCT e FROM Expense e LEFT JOIN e.splits s WHERE e.paidBy = :userId OR s.userId = :userId")
  List<Expense> findAllByInvolvedUserId(@Param("userId") Long userId);
}
