package com.splitz.expense.lifecycle;

import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Deep module owning the Payment lifecycle — the state machine that carries a Payment from PENDING
 * → MARKED_PAID → COMPLETED, plus the actor rules guarding every hop.
 *
 * <p>The front end already models this lifecycle as a deep module
 * (frontend-user/src/features/balances/settlement.ts: recordPayment collapses create +
 * mark-as-paid, confirmPayment completes it). On the back end the same knowledge used to be
 * procedural: {@link com.splitz.expense.service.PaymentService} re-derived the birth status, the
 * transition preconditions and the actor checks inline in every method. Here the lifecycle has a
 * home of its own: a small surface (initialState, markAsPaid, confirm, and the can* / is* actor
 * rules) hides the state-transition invariants — only PENDING can be marked paid, only MARKED_PAID
 * can be confirmed, a COMPLETED payment is immutable, a payee-created payment is born COMPLETED and
 * a payer-created one is born MARKED_PAID.
 *
 * <p>The module is stateless and takes every input (actor id, admin flag, now) explicitly, so it is
 * trivially testable through its interface with no Spring wiring and no repositories.
 */
@Component
public class PaymentLifecycle {

  /** The status and timestamps a freshly-created Payment is born with. */
  @Getter
  @Builder
  public static final class InitialState {
    private final SettlementStatus status;
    private final LocalDateTime markedPaidAt;
    private final LocalDateTime settledAt;
  }

  /**
   * Decides how a new Payment is born: the actor who records it determines the starting state. A
   * payee recording the payment has already received the funds (born COMPLETED); a payer recording
   * it has just paid (born MARKED_PAID); anyone else authorized to record it (e.g. an admin) starts
   * a PENDING payment that still needs the payer's mark.
   */
  public InitialState initialState(Long actorId, Long payerId, Long payeeId, LocalDateTime now) {
    if (actorId.equals(payeeId)) {
      return InitialState.builder().status(SettlementStatus.COMPLETED).settledAt(now).build();
    }
    if (actorId.equals(payerId)) {
      return InitialState.builder().status(SettlementStatus.MARKED_PAID).markedPaidAt(now).build();
    }
    return InitialState.builder().status(SettlementStatus.PENDING).build();
  }

  /** Transitions a PENDING payment to MARKED_PAID, recorded by the payer (or an admin). */
  public Payment markAsPaid(Payment payment, Long actorId, boolean admin, LocalDateTime now) {
    if (!admin && !actorId.equals(payment.getPayerId())) {
      throw new UnauthorizedException("You are not authorized to mark this payment as paid");
    }
    if (payment.getStatus() != SettlementStatus.PENDING) {
      throw new IllegalStateException("Payment must be in PENDING status to be marked as paid");
    }
    payment.setStatus(SettlementStatus.MARKED_PAID);
    payment.setMarkedPaidAt(now);
    return payment;
  }

  /** Transitions a MARKED_PAID payment to COMPLETED, recorded by the payee (or an admin). */
  public Payment confirm(Payment payment, Long actorId, boolean admin, LocalDateTime now) {
    if (!admin && !actorId.equals(payment.getPayeeId())) {
      throw new UnauthorizedException("You are not authorized to confirm this payment");
    }
    if (payment.getStatus() != SettlementStatus.MARKED_PAID) {
      throw new IllegalStateException("Payment must be in MARKED_PAID status to be confirmed");
    }
    payment.setStatus(SettlementStatus.COMPLETED);
    payment.setSettledAt(now);
    return payment;
  }

  /** Only the two parties or an admin may record a payment. */
  public boolean canCreate(Long actorId, Long payerId, Long payeeId, boolean admin) {
    return isOneOf(actorId, payerId, payeeId, admin);
  }

  /** Either party or an admin may view a payment. */
  public boolean isParticipant(Payment payment, Long actorId, boolean admin) {
    return isOneOf(actorId, payment.getPayerId(), payment.getPayeeId(), admin);
  }

  /** Either of two users or an admin may view the payments shared between them. */
  public boolean canViewBetween(Long actorId, Long firstId, Long secondId, boolean admin) {
    return isOneOf(actorId, firstId, secondId, admin);
  }

  /**
   * Guards an update: throws {@link IllegalStateException} when the payment is COMPLETED (it is
   * immutable) and {@link UnauthorizedException} when the actor is neither party nor an admin. The
   * exception the caller should surface lives here, so the service does not re-derive the rule.
   */
  public void assertUpdateAllowed(Payment payment, Long actorId, boolean admin) {
    if (!canUpdate(payment, actorId, admin)) {
      if (payment.getStatus() == SettlementStatus.COMPLETED) {
        throw new IllegalStateException("Cannot update a completed payment");
      }
      throw new UnauthorizedException("You are not authorized to update this payment");
    }
  }

  /** A payment can be updated only while it is not completed, and only by a party or an admin. */
  public boolean canUpdate(Payment payment, Long actorId, boolean admin) {
    return payment.getStatus() != SettlementStatus.COMPLETED
        && isParticipant(payment, actorId, admin);
  }

  /** The payer (or an admin) can mark a payment paid. */
  public boolean isPayer(Payment payment, Long actorId, boolean admin) {
    return admin || actorId.equals(payment.getPayerId());
  }

  /** The payee (or an admin) can confirm a payment. */
  public boolean isPayee(Payment payment, Long actorId, boolean admin) {
    return admin || actorId.equals(payment.getPayeeId());
  }

  /** The single "one of these two users, or an admin" rule behind the actor predicates. */
  private boolean isOneOf(Long actorId, Long firstId, Long secondId, boolean admin) {
    return admin || actorId.equals(firstId) || actorId.equals(secondId);
  }
}
