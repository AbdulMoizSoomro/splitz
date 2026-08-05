package com.splitz.expense.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.lifecycle.PaymentLifecycle.InitialState;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Payment lifecycle is the state machine that carries a Payment from PENDING → MARKED_PAID →
 * COMPLETED plus the actor rules guarding each hop. PaymentService used to re-derive all of this
 * procedurally in every method; here it is a deep module with its own interface — every expectation
 * is expressed as (actor, payment state) → (next state / outcome), with no Spring and no
 * repositories. The front end already models the same lifecycle as a deep module
 * (frontend-user/src/features/balances/settlement.ts).
 */
class PaymentLifecycleTest {

  private final PaymentLifecycle lifecycle = new PaymentLifecycle();

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 12, 0);

  private static Payment payment(SettlementStatus status) {
    return Payment.builder()
        .payerId(1L)
        .payeeId(2L)
        .amount(new BigDecimal("50.00"))
        .status(status)
        .build();
  }

  // -- birth state: which state a newly-created Payment is born into -----------

  @Test
  @DisplayName("Payee-created Payment is born COMPLETED with its settled timestamp")
  void payeeCreatedPaymentIsBornCompleted() {
    InitialState state = lifecycle.initialState(2L, 1L, 2L, NOW);

    assertThat(state.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    assertThat(state.getSettledAt()).isEqualTo(NOW);
    assertThat(state.getMarkedPaidAt()).isNull();
  }

  @Test
  @DisplayName("Payer-created Payment is born MARKED_PAID with its marked-paid timestamp")
  void payerCreatedPaymentIsBornMarkedPaid() {
    InitialState state = lifecycle.initialState(1L, 1L, 2L, NOW);

    assertThat(state.getStatus()).isEqualTo(SettlementStatus.MARKED_PAID);
    assertThat(state.getMarkedPaidAt()).isEqualTo(NOW);
    assertThat(state.getSettledAt()).isNull();
  }

  @Test
  @DisplayName("Neither-party (admin) created Payment is born PENDING with no timestamps")
  void adminCreatedPaymentIsBornPending() {
    InitialState state = lifecycle.initialState(3L, 1L, 2L, NOW);

    assertThat(state.getStatus()).isEqualTo(SettlementStatus.PENDING);
    assertThat(state.getMarkedPaidAt()).isNull();
    assertThat(state.getSettledAt()).isNull();
  }

  // -- markAsPaid: PENDING -> MARKED_PAID --------------------------------------

  @Test
  @DisplayName("Payer can mark a PENDING payment paid")
  void payerCanMarkPendingPaymentPaid() {
    Payment result = lifecycle.markAsPaid(payment(SettlementStatus.PENDING), 1L, false, NOW);

    assertThat(result.getStatus()).isEqualTo(SettlementStatus.MARKED_PAID);
    assertThat(result.getMarkedPaidAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Admin can mark a PENDING payment paid")
  void adminCanMarkPendingPaymentPaid() {
    Payment result = lifecycle.markAsPaid(payment(SettlementStatus.PENDING), 3L, true, NOW);

    assertThat(result.getStatus()).isEqualTo(SettlementStatus.MARKED_PAID);
  }

  @Test
  @DisplayName("A non-paid payee cannot mark a PENDING payment paid")
  void payeeCannotMarkPaymentPaid() {
    assertThatThrownBy(
            () -> lifecycle.markAsPaid(payment(SettlementStatus.PENDING), 2L, false, NOW))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("mark this payment as paid");
  }

  @Test
  @DisplayName("Only a PENDING payment can be marked paid")
  void onlyPendingPaymentCanBeMarkedPaid() {
    assertThatThrownBy(
            () -> lifecycle.markAsPaid(payment(SettlementStatus.MARKED_PAID), 1L, false, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PENDING");

    assertThatThrownBy(
            () -> lifecycle.markAsPaid(payment(SettlementStatus.COMPLETED), 1L, false, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PENDING");
  }

  // -- confirm: MARKED_PAID -> COMPLETED ---------------------------------------

  @Test
  @DisplayName("Payee can confirm a MARKED_PAID payment")
  void payeeCanConfirmMarkedPaidPayment() {
    Payment result = lifecycle.confirm(payment(SettlementStatus.MARKED_PAID), 2L, false, NOW);

    assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    assertThat(result.getSettledAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Admin can confirm a MARKED_PAID payment")
  void adminCanConfirmMarkedPaidPayment() {
    Payment result = lifecycle.confirm(payment(SettlementStatus.MARKED_PAID), 3L, true, NOW);

    assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
  }

  @Test
  @DisplayName("A non-payee payer cannot confirm a payment")
  void payerCannotConfirmPayment() {
    assertThatThrownBy(
            () -> lifecycle.confirm(payment(SettlementStatus.MARKED_PAID), 1L, false, NOW))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("confirm this payment");
  }

  @Test
  @DisplayName("Only a MARKED_PAID payment can be confirmed")
  void onlyMarkedPaidPaymentCanBeConfirmed() {
    assertThatThrownBy(() -> lifecycle.confirm(payment(SettlementStatus.PENDING), 2L, false, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MARKED_PAID");
  }

  // -- actor rules -------------------------------------------------------------

  @Test
  @DisplayName("Only the two parties or an admin may create a payment")
  void canCreateAllowsPartiesAndAdmin() {
    assertThat(lifecycle.canCreate(1L, 1L, 2L, false)).isTrue();
    assertThat(lifecycle.canCreate(2L, 1L, 2L, false)).isTrue();
    assertThat(lifecycle.canCreate(3L, 1L, 2L, false)).isFalse();
    assertThat(lifecycle.canCreate(3L, 1L, 2L, true)).isTrue();
  }

  @Test
  @DisplayName("isParticipant is true for either party or an admin")
  void isParticipantCoversPartiesAndAdmin() {
    assertThat(lifecycle.isParticipant(payment(SettlementStatus.PENDING), 1L, false)).isTrue();
    assertThat(lifecycle.isParticipant(payment(SettlementStatus.PENDING), 2L, false)).isTrue();
    assertThat(lifecycle.isParticipant(payment(SettlementStatus.PENDING), 3L, true)).isTrue();
    assertThat(lifecycle.isParticipant(payment(SettlementStatus.PENDING), 3L, false)).isFalse();
  }

  @Test
  @DisplayName("A completed payment cannot be updated; only the parties or an admin may update")
  void canUpdateRespectsStatusAndActor() {
    assertThat(lifecycle.canUpdate(payment(SettlementStatus.PENDING), 1L, false)).isTrue();
    assertThat(lifecycle.canUpdate(payment(SettlementStatus.PENDING), 2L, false)).isTrue();
    assertThat(lifecycle.canUpdate(payment(SettlementStatus.PENDING), 3L, true)).isTrue();
    assertThat(lifecycle.canUpdate(payment(SettlementStatus.PENDING), 3L, false)).isFalse();
    assertThat(lifecycle.canUpdate(payment(SettlementStatus.COMPLETED), 1L, false)).isFalse();
    assertThat(lifecycle.canUpdate(payment(SettlementStatus.MARKED_PAID), 1L, false)).isTrue();
  }

  @Test
  @DisplayName("assertUpdateAllowed rejects a completed payment as immutable")
  void assertUpdateAllowedRejectsCompletedPayment() {
    assertThatThrownBy(
            () -> lifecycle.assertUpdateAllowed(payment(SettlementStatus.COMPLETED), 1L, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("completed payment");
  }

  @Test
  @DisplayName("assertUpdateAllowed rejects a stranger and permits a party")
  void assertUpdateAllowedRejectsStrangerAndPermitsParty() {
    assertThatThrownBy(
            () -> lifecycle.assertUpdateAllowed(payment(SettlementStatus.PENDING), 3L, false))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("update this payment");

    assertThatNoException()
        .isThrownBy(
            () -> lifecycle.assertUpdateAllowed(payment(SettlementStatus.PENDING), 1L, false));
  }

  @Test
  @DisplayName("isPayer / isPayee identify the party or an admin")
  void isPayerAndPayeeIdentifyPartyOrAdmin() {
    assertThat(lifecycle.isPayer(payment(SettlementStatus.PENDING), 1L, false)).isTrue();
    assertThat(lifecycle.isPayer(payment(SettlementStatus.PENDING), 3L, true)).isTrue();
    assertThat(lifecycle.isPayer(payment(SettlementStatus.PENDING), 2L, false)).isFalse();

    assertThat(lifecycle.isPayee(payment(SettlementStatus.PENDING), 2L, false)).isTrue();
    assertThat(lifecycle.isPayee(payment(SettlementStatus.PENDING), 3L, true)).isTrue();
    assertThat(lifecycle.isPayee(payment(SettlementStatus.PENDING), 1L, false)).isFalse();
  }
}
