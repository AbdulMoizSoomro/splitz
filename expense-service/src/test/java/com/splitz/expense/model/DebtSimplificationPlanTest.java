package com.splitz.expense.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.dto.DebtDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebtSimplificationPlanTest {

  @Test
  void toDebtDTOs_WhenTransactionsPresent_MapsToDebtDTOs() {
    SimplifiedDebtTransaction tx =
        SimplifiedDebtTransaction.builder()
            .fromUserId(1L)
            .fromUsername("alice")
            .toUserId(2L)
            .toUsername("bob")
            .amount(new BigDecimal("25.50"))
            .status(TransactionStatus.PENDING)
            .build();

    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder().groupId(10L).transactions(List.of(tx)).build();

    List<DebtDTO> dtos = plan.toDebtDTOs();

    assertThat(dtos).hasSize(1);
    assertThat(dtos.get(0).getFrom()).isEqualTo(1L);
    assertThat(dtos.get(0).getFromUsername()).isEqualTo("alice");
    assertThat(dtos.get(0).getTo()).isEqualTo(2L);
    assertThat(dtos.get(0).getToUsername()).isEqualTo("bob");
    assertThat(dtos.get(0).getAmount()).isEqualByComparingTo("25.50");
  }

  @Test
  void toDebtDTOs_WhenTransactionsNull_ReturnsEmptyList() {
    DebtSimplificationPlan plan = new DebtSimplificationPlan();
    plan.setTransactions(null);

    List<DebtDTO> dtos = plan.toDebtDTOs();

    assertThat(dtos).isEmpty();
  }
}
