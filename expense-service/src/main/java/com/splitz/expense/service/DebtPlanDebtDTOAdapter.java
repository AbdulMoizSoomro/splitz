package com.splitz.expense.service;

import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Thin DTO adapter that maps a {@link DebtSimplificationPlan}'s transactions into the {@link
 * DebtDTO} list consumed by the group-balances endpoint. Keeps the deep Debt-Netting Engine free of
 * DTO concerns.
 */
@Component
public class DebtPlanDebtDTOAdapter {

  public List<DebtDTO> toDebtDtos(DebtSimplificationPlan plan) {
    if (plan == null || plan.getTransactions() == null) {
      return Collections.emptyList();
    }
    List<DebtDTO> debts = new ArrayList<>();
    for (SimplifiedDebtTransaction tx : plan.getTransactions()) {
      debts.add(
          DebtDTO.builder()
              .from(tx.getFromUserId())
              .fromUsername(tx.getFromUsername())
              .to(tx.getToUserId())
              .toUsername(tx.getToUsername())
              .amount(tx.getAmount())
              .build());
    }
    return debts;
  }
}
