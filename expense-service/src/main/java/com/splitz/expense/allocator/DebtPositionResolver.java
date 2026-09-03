package com.splitz.expense.allocator;

import com.splitz.expense.balance.FinancialLedgerEngine;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deep module that turns (payerId, payeeId) into the payer's {@link DebtPosition}. Hides the {@link
 * FinancialLedgerEngine} fetch, the rule "only the groups where the payer owes count", and the
 * deterministic group-id ordering. Allocators consume the resolved {@link DebtPosition} as a pure
 * input, so they no longer re-derive the debt picture themselves (locality for the debt rule).
 */
@Component
public class DebtPositionResolver {

  private final FinancialLedgerEngine financialLedgerEngine;

  public DebtPositionResolver(FinancialLedgerEngine financialLedgerEngine) {
    this.financialLedgerEngine = financialLedgerEngine;
  }

  public DebtPosition resolve(Long payerId, Long payeeId) {
    FriendBalanceResponseDTO response =
        financialLedgerEngine.getNetBalanceWithFriend(payerId, payeeId);

    List<GroupDebt> debts = new ArrayList<>();
    if (response != null && response.getGroupBalances() != null) {
      response.getGroupBalances().stream()
          .filter(gb -> gb.getBalance() != null && gb.getBalance().compareTo(BigDecimal.ZERO) < 0)
          .sorted(Comparator.comparing(FriendGroupBalanceDTO::getGroupId))
          .forEach(
              gb ->
                  debts.add(
                      GroupDebt.builder()
                          .groupId(gb.getGroupId())
                          .owedAmount(gb.getBalance().abs())
                          .build()));
    }

    return DebtPosition.builder()
        .payerId(payerId)
        .payeeId(payeeId)
        .debts(Collections.unmodifiableList(debts))
        .build();
  }
}
