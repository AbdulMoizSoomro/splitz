package com.splitz.expense.allocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.model.SettlementAllocation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DefaultAllocationEngineTest {

  @Mock private DebtPositionResolver debtPositionResolver;
  @Mock private SettlementAutoAllocator settlementAutoAllocator;

  private DefaultAllocationEngine engine;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    engine = new DefaultAllocationEngine(debtPositionResolver, settlementAutoAllocator);
  }

  @Test
  void groupBoundPayment_createsSingleAllocationForThatGroup() {
    List<SettlementAllocation> result =
        engine.resolveAllocations(1L, 2L, new BigDecimal("100.00"), 10L, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getGroupId()).isEqualTo(10L);
    assertThat(result.get(0).getAmount()).isEqualByComparingTo("100.00");
  }

  @Test
  void explicitAllocations_producesMatchingSettlementAllocations() {
    List<CreateFriendshipSettlementRequest.Allocation> explicit =
        List.of(
            CreateFriendshipSettlementRequest.Allocation.builder()
                .groupId(10L)
                .amount(new BigDecimal("60.00"))
                .build(),
            CreateFriendshipSettlementRequest.Allocation.builder()
                .groupId(20L)
                .amount(new BigDecimal("40.00"))
                .build());

    List<SettlementAllocation> result =
        engine.resolveAllocations(1L, 2L, new BigDecimal("100.00"), null, explicit);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getGroupId()).isEqualTo(10L);
    assertThat(result.get(0).getAmount()).isEqualByComparingTo("60.00");
    assertThat(result.get(1).getGroupId()).isEqualTo(20L);
    assertThat(result.get(1).getAmount()).isEqualByComparingTo("40.00");
  }

  @Test
  void explicitAllocations_totalMismatch_throwsIllegalArgument() {
    List<CreateFriendshipSettlementRequest.Allocation> explicit =
        List.of(
            CreateFriendshipSettlementRequest.Allocation.builder()
                .groupId(10L)
                .amount(new BigDecimal("50.00"))
                .build());

    assertThatThrownBy(
            () -> engine.resolveAllocations(1L, 2L, new BigDecimal("100.00"), null, explicit))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("50.00")
        .hasMessageContaining("100.00");
  }

  @Test
  void noGroupIdOrExplicitAllocations_delegatesToAutoAllocator() {
    DebtPosition position =
        DebtPosition.builder()
            .payerId(1L)
            .payeeId(2L)
            .debts(
                List.of(
                    GroupDebt.builder().groupId(10L).owedAmount(new BigDecimal("80.00")).build()))
            .build();
    when(debtPositionResolver.resolve(1L, 2L)).thenReturn(position);

    List<SettlementAllocation> autoResult =
        List.of(
            SettlementAllocation.builder().groupId(10L).amount(new BigDecimal("80.00")).build(),
            SettlementAllocation.builder().groupId(null).amount(new BigDecimal("20.00")).build());
    when(settlementAutoAllocator.allocate(position, new BigDecimal("100.00")))
        .thenReturn(autoResult);

    List<SettlementAllocation> result =
        engine.resolveAllocations(1L, 2L, new BigDecimal("100.00"), null, null);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getGroupId()).isEqualTo(10L);
    assertThat(result.get(0).getAmount()).isEqualByComparingTo("80.00");
    assertThat(result.get(1).getGroupId()).isNull();
    assertThat(result.get(1).getAmount()).isEqualByComparingTo("20.00");
    verify(debtPositionResolver).resolve(1L, 2L);
    verify(settlementAutoAllocator).allocate(position, new BigDecimal("100.00"));
  }
}
