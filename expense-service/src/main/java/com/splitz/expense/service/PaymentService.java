package com.splitz.expense.service;

import com.splitz.expense.allocator.AllocationEngine;
import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.lifecycle.PaymentLifecycle;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final SettlementAllocationRepository settlementAllocationRepository;
  private final SharedSecurityAuthorizer splitzAuthorizer;
  private final AllocationEngine allocationEngine;
  private final PaymentLifecycle paymentLifecycle;

  @Transactional
  public Payment createPayment(
      Long payerId,
      Long payeeId,
      BigDecimal amount,
      Long groupId,
      List<CreateSettlementRequest.Allocation> explicitAllocations) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Payment amount must be positive");
    }
    if (payerId.equals(payeeId)) {
      throw new IllegalArgumentException("Payer and payee cannot be the same user");
    }

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    boolean admin = splitzAuthorizer.isAdmin();
    if (!paymentLifecycle.canCreate(currentUserId, payerId, payeeId, admin)) {
      throw new UnauthorizedException("You are not authorized to create this payment");
    }

    PaymentLifecycle.InitialState initialState =
        paymentLifecycle.initialState(currentUserId, payerId, payeeId, LocalDateTime.now());

    Payment payment =
        Payment.builder()
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(amount)
            .status(initialState.getStatus())
            .markedPaidAt(initialState.getMarkedPaidAt())
            .settledAt(initialState.getSettledAt())
            .build();

    List<SettlementAllocation> allocations =
        allocationEngine.resolveAllocations(payerId, payeeId, amount, groupId, explicitAllocations);

    for (SettlementAllocation allocation : allocations) {
      payment.addAllocation(allocation);
    }

    return paymentRepository.save(payment);
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
  public List<Payment> getPaymentsByGroup(Long groupId) {
    List<SettlementAllocation> allocations = settlementAllocationRepository.findByGroupId(groupId);
    return allocations.stream()
        .map(SettlementAllocation::getPayment)
        .distinct()
        .collect(Collectors.toList());
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

    // Clear existing allocations (orphanRemoval will delete from DB)
    payment.getAllocations().clear();

    BigDecimal amount = payment.getAmount();
    List<SettlementAllocation> allocations =
        allocationEngine.resolveAllocations(
            payment.getPayerId(), payment.getPayeeId(), amount, null, newAllocations);

    for (SettlementAllocation allocation : allocations) {
      payment.addAllocation(allocation);
    }

    return paymentRepository.save(payment);
  }
}
