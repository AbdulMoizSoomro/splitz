package com.splitz.expense.repository;

import com.splitz.expense.model.Expense;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

  List<Expense> findByGroupId(Long groupId);

  List<Expense> findByGroupIdIn(Collection<Long> groupIds);

  @Query(
      "SELECT COALESCE(SUM(s.shareAmount), 0) FROM Expense e JOIN e.splits s WHERE e.groupId IN"
          + " :groupIds AND e.paidBy = :payerId AND s.userId = :payeeId")
  BigDecimal calculateTotalOwedBetweenUsers(
      @Param("payerId") Long payerId,
      @Param("payeeId") Long payeeId,
      @Param("groupIds") Collection<Long> groupIds);

  @Query(
      "SELECT COALESCE(SUM(s.shareAmount), 0) FROM Expense e JOIN e.splits s WHERE e.paidBy = :payerId AND s.userId = :payeeId")
  BigDecimal calculateTotalOwedBetweenUsersGlobally(
      @Param("payerId") Long payerId, @Param("payeeId") Long payeeId);
}
