package com.splitz.expense.allocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import com.splitz.expense.service.BalanceService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the deep {@link DebtPositionResolver} — the module that turns (payerId, payeeId) into the
 * payer's eligible group debts, hiding the BalanceService fetch, the filter-to-owed-groups rule and
 * the deterministic group id ordering. This is the seam the settlement-allocation decision reads
 * from, instead of each allocator re-deriving the debt picture itself.
 */
class DebtPositionResolverTest {

  private BalanceService balanceService;
  private DebtPositionResolver resolver;

  @BeforeEach
  void setUp() {
    balanceService = mock(BalanceService.class);
    resolver = new DebtPositionResolver(balanceService);
  }

  @Test
  @DisplayName(
      "Should resolve only the groups where the payer owes, sorted by group id, as positive owed amounts")
  void shouldResolveOwedGroupsSortedByGroupId() {
    Long payerId = 1L;
    Long payeeId = 2L;

    // Group 20 (payer receives, positive balance) must be dropped; Group 10 and Group 30 are owed.
    FriendBalanceResponseDTO response =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(new BigDecimal("-40.00"))
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder()
                        .groupId(30L)
                        .balance(new BigDecimal("-30.00"))
                        .build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(20L)
                        .balance(new BigDecimal("50.00"))
                        .build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(10L)
                        .balance(new BigDecimal("-10.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(response);

    DebtPosition position = resolver.resolve(payerId, payeeId);

    assertThat(position.getPayerId()).isEqualTo(payerId);
    assertThat(position.getPayeeId()).isEqualTo(payeeId);
    // Only the owed groups survive, in ascending group id order, as positive magnitudes.
    assertThat(position.getDebts()).hasSize(2);
    assertThat(position.getDebts().get(0).getGroupId()).isEqualTo(10L);
    assertThat(position.getDebts().get(0).getOwedAmount()).isEqualByComparingTo("10.00");
    assertThat(position.getDebts().get(1).getGroupId()).isEqualTo(30L);
    assertThat(position.getDebts().get(1).getOwedAmount()).isEqualByComparingTo("30.00");
    verify(balanceService).getNetBalanceWithFriend(payerId, payeeId);
  }

  @Test
  @DisplayName("Should resolve an empty debt list when the payer owes nothing")
  void shouldResolveEmptyDebtsWhenPayerOwesNothing() {
    Long payerId = 1L;
    Long payeeId = 2L;

    FriendBalanceResponseDTO response =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .netBalance(BigDecimal.ZERO)
            .groupBalances(List.of())
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(response);

    DebtPosition position = resolver.resolve(payerId, payeeId);

    assertThat(position.getDebts()).isEmpty();
  }

  @Test
  @DisplayName("Should resolve an empty debt list when the balance response is null")
  void shouldResolveEmptyDebtsWhenResponseIsNull() {
    Long payerId = 1L;
    Long payeeId = 2L;

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(null);

    DebtPosition position = resolver.resolve(payerId, payeeId);

    assertThat(position.getDebts()).isEmpty();
  }

  @Test
  @DisplayName(
      "Should ignore the groups where the payer is owed (exactly zero balance) and keep only strict debts")
  void shouldIgnoreZeroAndPositiveBalances() {
    Long payerId = 1L;
    Long payeeId = 2L;

    FriendBalanceResponseDTO response =
        FriendBalanceResponseDTO.builder()
            .userId(payerId)
            .friendId(payeeId)
            .groupBalances(
                List.of(
                    FriendGroupBalanceDTO.builder().groupId(5L).balance(BigDecimal.ZERO).build(),
                    FriendGroupBalanceDTO.builder()
                        .groupId(6L)
                        .balance(new BigDecimal("20.00"))
                        .build()))
            .build();

    when(balanceService.getNetBalanceWithFriend(payerId, payeeId)).thenReturn(response);

    DebtPosition position = resolver.resolve(payerId, payeeId);

    assertThat(position.getDebts()).isEmpty();
  }
}
