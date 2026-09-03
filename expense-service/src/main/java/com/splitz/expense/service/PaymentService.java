package com.splitz.expense.service;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.lifecycle.PaymentLifecycle;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.PaymentType;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final SharedSecurityAuthorizer splitzAuthorizer;
  private final PaymentLifecycle paymentLifecycle;
  private final GroupGovernance groupGovernance;

  @Transactional
  public Payment createGroupPayment(
      Long groupId, Long payerId, Long payeeId, BigDecimal amount, String notes) {
    if (groupId == null) {
      throw new IllegalArgumentException("Group ID is required for in-group payments");
    }
    validateAmountAndParties(payerId, payeeId, amount);

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    boolean admin = splitzAuthorizer.isAdmin();
    if (!paymentLifecycle.canCreate(currentUserId, payerId, payeeId, admin)) {
      throw new UnauthorizedException("You are not authorized to create this payment");
    }

    groupGovernance.assertIsMember(groupId, payerId);
    groupGovernance.assertIsMember(groupId, payeeId);

    PaymentLifecycle.InitialState initialState =
        paymentLifecycle.initialState(currentUserId, payerId, payeeId, LocalDateTime.now());

    Payment payment =
        Payment.builder()
            .type(PaymentType.GROUP)
            .groupId(groupId)
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(amount)
            .notes(notes)
            .status(initialState.getStatus())
            .markedPaidAt(initialState.getMarkedPaidAt())
            .settledAt(initialState.getSettledAt())
            .build();

    return paymentRepository.save(payment);
  }

  @Transactional
  public Payment createDirectPayment(Long payerId, Long payeeId, BigDecimal amount, String notes) {
    validateAmountAndParties(payerId, payeeId, amount);

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    boolean admin = splitzAuthorizer.isAdmin();
    if (!paymentLifecycle.canCreate(currentUserId, payerId, payeeId, admin)) {
      throw new UnauthorizedException("You are not authorized to create this payment");
    }

    PaymentLifecycle.InitialState initialState =
        paymentLifecycle.initialState(currentUserId, payerId, payeeId, LocalDateTime.now());

    Payment payment =
        Payment.builder()
            .type(PaymentType.DIRECT)
            .groupId(null)
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(amount)
            .notes(notes)
            .status(initialState.getStatus())
            .markedPaidAt(initialState.getMarkedPaidAt())
            .settledAt(initialState.getSettledAt())
            .build();

    return paymentRepository.save(payment);
  }

  @Transactional
  public Payment createPayment(
      Long payerId,
      Long payeeId,
      BigDecimal amount,
      Long groupId,
      List<CreateSettlementRequest.Allocation> explicitAllocations) {
    if (groupId != null) {
      return createGroupPayment(groupId, payerId, payeeId, amount, null);
    }
    if (explicitAllocations != null && !explicitAllocations.isEmpty()) {
      Long firstGroupId = explicitAllocations.get(0).getGroupId();
      if (firstGroupId != null) {
        return createGroupPayment(firstGroupId, payerId, payeeId, amount, null);
      }
    }
    return createDirectPayment(payerId, payeeId, amount, null);
  }

  @Transactional
  public Payment markAsPaid(Long paymentId) {
    Payment payment =
        paymentRepository
            .findByIdWithLock(paymentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    paymentLifecycle.markAsPaid(
        payment, currentUserId, splitzAuthorizer.isAdmin(), LocalDateTime.now());

    return paymentRepository.save(payment);
  }

  @Transactional
  public Payment confirmPayment(Long paymentId) {
    Payment payment =
        paymentRepository
            .findByIdWithLock(paymentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    paymentLifecycle.confirm(
        payment, currentUserId, splitzAuthorizer.isAdmin(), LocalDateTime.now());

    return paymentRepository.save(payment);
  }

  @Transactional(readOnly = true)
  public Payment getPaymentById(Long id) {
    Payment payment =
        paymentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!paymentLifecycle.isParticipant(payment, currentUserId, splitzAuthorizer.isAdmin())) {
      throw new UnauthorizedException("You are not authorized to view this payment");
    }

    return payment;
  }

  @Transactional(readOnly = true)
  public List<Payment> getPaymentsBetweenUsers(Long userId1, Long userId2) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!paymentLifecycle.canViewBetween(
        currentUserId, userId1, userId2, splitzAuthorizer.isAdmin())) {
      throw new UnauthorizedException("You are not authorized to view these payments");
    }
    return paymentRepository.findBetweenUsers(userId1, userId2);
  }

  @Transactional(readOnly = true)
  public List<Payment> getDirectPaymentsBetweenUsers(Long userId1, Long userId2) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!paymentLifecycle.canViewBetween(
        currentUserId, userId1, userId2, splitzAuthorizer.isAdmin())) {
      throw new UnauthorizedException("You are not authorized to view these payments");
    }
    return paymentRepository.findDirectBetweenUsers(userId1, userId2);
  }

  @Transactional(readOnly = true)
  public List<Payment> getPaymentsByGroup(Long groupId) {
    return paymentRepository.findByGroupId(groupId);
  }

  @Transactional
  public Payment updatePayment(
      Long paymentId,
      BigDecimal newAmount,
      List<CreateSettlementRequest.Allocation> newAllocations) {
    Payment payment =
        paymentRepository
            .findByIdWithLock(paymentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    paymentLifecycle.assertUpdateAllowed(payment, currentUserId, splitzAuthorizer.isAdmin());

    if (newAmount != null && newAmount.compareTo(BigDecimal.ZERO) > 0) {
      payment.setAmount(newAmount);
    }

    if (newAllocations != null && !newAllocations.isEmpty()) {
      Long newGroupId = newAllocations.get(0).getGroupId();
      if (newGroupId != null) {
        groupGovernance.assertIsMember(newGroupId, payment.getPayerId());
        groupGovernance.assertIsMember(newGroupId, payment.getPayeeId());
        payment.setType(PaymentType.GROUP);
        payment.setGroupId(newGroupId);
      }
    }

    return paymentRepository.save(payment);
  }

  private void validateAmountAndParties(Long payerId, Long payeeId, BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Payment amount must be positive");
    }
    if (payerId.equals(payeeId)) {
      throw new IllegalArgumentException("Payer and payee cannot be the same user");
    }
  }
}
