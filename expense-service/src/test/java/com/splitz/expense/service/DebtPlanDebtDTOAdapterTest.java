package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.model.DebtSimplificationPlan;
import com.splitz.expense.model.SimplifiedDebtTransaction;
import com.splitz.expense.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebtPlanDebtDTOAdapterTest {

  private final DebtPlanDebtDTOAdapter adapter = new DebtPlanDebtDTOAdapter();

  @Test
  void mapsPlanTransactionsToDebtDtos() {
    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder()
            .groupId(10L)
            .transactions(
                List.of(
                    SimplifiedDebtTransaction.builder()
                        .fromUserId(2L)
                        .fromUsername("bob")
                        .toUserId(1L)
                        .toUsername("alice")
                        .amount(new BigDecimal("5.00"))
                        .status(TransactionStatus.PENDING)
                        .build(),
                    SimplifiedDebtTransaction.builder()
                        .fromUserId(3L)
                        .fromUsername("charlie")
                        .toUserId(1L)
                        .toUsername("alice")
                        .amount(new BigDecimal("7.50"))
                        .status(TransactionStatus.PENDING)
                        .build()))
            .build();

    List<DebtDTO> debts = adapter.toDebtDtos(plan);

    assertThat(debts).hasSize(2);
    assertThat(debts.get(0).getFrom()).isEqualTo(2L);
    assertThat(debts.get(0).getFromUsername()).isEqualTo("bob");
    assertThat(debts.get(0).getTo()).isEqualTo(1L);
    assertThat(debts.get(0).getToUsername()).isEqualTo("alice");
    assertThat(debts.get(0).getAmount()).isEqualByComparingTo("5.00");

    assertThat(debts.get(1).getFrom()).isEqualTo(3L);
    assertThat(debts.get(1).getFromUsername()).isEqualTo("charlie");
    assertThat(debts.get(1).getTo()).isEqualTo(1L);
    assertThat(debts.get(1).getToUsername()).isEqualTo("alice");
    assertThat(debts.get(1).getAmount()).isEqualByComparingTo("7.50");
  }

  @Test
  void mapsEmptyPlanToEmptyList() {
    DebtSimplificationPlan plan =
        DebtSimplificationPlan.builder().groupId(10L).transactions(List.of()).build();

    assertThat(adapter.toDebtDtos(plan)).isEmpty();
  }

  @Test
  void mapsNullTransactionsToEmptyList() {
    DebtSimplificationPlan plan = DebtSimplificationPlan.builder().groupId(10L).build();

    assertThat(adapter.toDebtDtos(plan)).isEmpty();
  }
}
