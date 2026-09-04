package com.splitz.expense.repository;

import com.splitz.expense.model.Expense;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<Expense, Long> {

  @Query(
      value =
          "SELECT sub.user_id AS userId, SUM(sub.amount) AS balance "
              + "FROM ( "
              + "    SELECT e.paid_by AS user_id, e.amount AS amount "
              + "    FROM expenses e "
              + "    WHERE e.group_id = :groupId "
              + "    UNION ALL "
              + "    SELECT s.user_id AS user_id, -s.share_amount AS amount "
              + "    FROM expense_splits s "
              + "    JOIN expenses e ON s.expense_id = e.id "
              + "    WHERE e.group_id = :groupId "
              + "    UNION ALL "
              + "    SELECT p.payer_id AS user_id, p.amount AS amount "
              + "    FROM payments p "
              + "    WHERE p.group_id = :groupId AND p.status IN ('COMPLETED', 'MARKED_PAID') "
              + "    UNION ALL "
              + "    SELECT p.payee_id AS user_id, -p.amount AS amount "
              + "    FROM payments p "
              + "    WHERE p.group_id = :groupId AND p.status IN ('COMPLETED', 'MARKED_PAID') "
              + ") sub "
              + "GROUP BY sub.user_id",
      nativeQuery = true)
  List<UserBalanceAggregate> calculateGroupBalances(@Param("groupId") Long groupId);

  @Query(
      value =
          "SELECT COALESCE(SUM(sub.amount), 0) "
              + "FROM ( "
              + "    SELECT s.share_amount AS amount "
              + "    FROM expense_splits s "
              + "    JOIN expenses e ON s.expense_id = e.id "
              + "    WHERE e.group_id = :groupId AND e.paid_by = :userId AND s.user_id = :friendId "
              + "    UNION ALL "
              + "    SELECT -s.share_amount AS amount "
              + "    FROM expense_splits s "
              + "    JOIN expenses e ON s.expense_id = e.id "
              + "    WHERE e.group_id = :groupId AND e.paid_by = :friendId AND s.user_id = :userId "
              + "    UNION ALL "
              + "    SELECT p.amount AS amount "
              + "    FROM payments p "
              + "    WHERE p.group_id = :groupId AND p.payer_id = :userId AND p.payee_id = :friendId "
              + "      AND p.status IN ('COMPLETED', 'MARKED_PAID') "
              + "    UNION ALL "
              + "    SELECT -p.amount AS amount "
              + "    FROM payments p "
              + "    WHERE p.group_id = :groupId AND p.payer_id = :friendId AND p.payee_id = :userId "
              + "      AND p.status IN ('COMPLETED', 'MARKED_PAID') "
              + ") sub",
      nativeQuery = true)
  BigDecimal calculatePairwiseBalanceInGroup(
      @Param("userId") Long userId,
      @Param("friendId") Long friendId,
      @Param("groupId") Long groupId);
}
