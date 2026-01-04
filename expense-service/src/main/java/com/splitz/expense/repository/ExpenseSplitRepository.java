package com.splitz.expense.repository;

import com.splitz.expense.model.ExpenseSplit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

  List<ExpenseSplit> findByExpenseId(Long expenseId);
}
