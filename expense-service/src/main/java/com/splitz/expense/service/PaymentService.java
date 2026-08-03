package com.splitz.expense.service;

import com.splitz.expense.allocator.DebtPosition;
import com.splitz.expense.allocator.DebtPositionResolver;
import com.splitz.expense.allocator.SettlementAutoAllocator;
import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
  private final SettlementAutoAllocator settlementAutoAllocator;
  private final DebtPositionResolver debtPositionResolver;

  @Transactional
  public Payment createPayment(
      Long payerId,
      Long payeeId,
      BigDecimal amount,
      Long groupId,
      List<CreateFriendshipSettlementRequest.Allocation> explicitAllocations) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Payment amount must be positive");
    }
    if (payerId.equals(payeeId)) {
      throw new IllegalArgumentException("Payer and payee cannot be the same user");
    }

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(payerId)
        && !currentUserId.equals(payeeId)
        && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to create this payment");
    }

    SettlementStatus status = SettlementStatus.PENDING;
    LocalDateTime markedPaidAt = null;
    LocalDateTime settledAt = null;

    if (currentUserId.equals(payeeId)) {
      status = SettlementStatus.COMPLETED;
      settledAt = LocalDateTime.now();
    } else if (currentUserId.equals(payerId)) {
      status = SettlementStatus.MARKED_PAID;
      markedPaidAt = LocalDateTime.now();
    }

    Payment payment =
        Payment.builder()
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(amount)
            .status(status)
            .markedPaidAt(markedPaidAt)
            .settledAt(settledAt)
            .build();

    List<SettlementAllocation> allocations = new ArrayList<>();

    if (groupId != null) {
      // Group-bound payment
      allocations.add(SettlementAllocation.builder().groupId(groupId).amount(amount).build());
    } else if (explicitAllocations != null && !explicitAllocations.isEmpty()) {
      // Explicit manual allocations
      BigDecimal totalAllocated =
          explicitAllocations.stream()
              .map(CreateFriendshipSettlementRequest.Allocation::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (totalAllocated.compareTo(amount) != 0) {
        throw new IllegalArgumentException(
            "Total allocated amount ("
                + totalAllocated
                + ") must match payment amount ("
                + amount
                + ")");
      }

      for (CreateFriendshipSettlementRequest.Allocation allocationReq : explicitAllocations) {
        allocations.add(
            SettlementAllocation.builder()
                .groupId(allocationReq.getGroupId())
                .amount(allocationReq.getAmount())
                .build());
      }
    } else {
      allocations.addAll(resolveAndAllocate(payerId, payeeId, amount));
    }

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
    if (!currentUserId.equals(payment.getPayerId()) && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to mark this payment as paid");
    }

    if (payment.getStatus() != SettlementStatus.PENDING) {
      throw new IllegalStateException("Payment must be in PENDING status to be marked as paid");
    }

    payment.setStatus(SettlementStatus.MARKED_PAID);
    payment.setMarkedPaidAt(LocalDateTime.now());

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
    if (!currentUserId.equals(payment.getPayeeId()) && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to confirm this payment");
    }

    if (payment.getStatus() != SettlementStatus.MARKED_PAID) {
      throw new IllegalStateException("Payment must be in MARKED_PAID status to be confirmed");
    }

    payment.setStatus(SettlementStatus.COMPLETED);
    payment.setSettledAt(LocalDateTime.now());

    return paymentRepository.save(payment);
  }

  @Transactional(readOnly = true)
  public Payment getPaymentById(Long id) {
    Payment payment =
        paymentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(payment.getPayerId())
        && !currentUserId.equals(payment.getPayeeId())
        && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to view this payment");
    }

    return payment;
  }

  @Transactional(readOnly = true)
  public List<Payment> getPaymentsBetweenUsers(Long userId1, Long userId2) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(userId1)
        && !currentUserId.equals(userId2)
        && !splitzAuthorizer.isAdmin()) {
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
      List<CreateFriendshipSettlementRequest.Allocation> newAllocations) {
    Payment payment =
        paymentRepository
            .findByIdWithLock(paymentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

    if (payment.getStatus() == SettlementStatus.COMPLETED) {
      throw new IllegalStateException("Cannot update a completed payment");
    }

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(payment.getPayerId())
        && !currentUserId.equals(payment.getPayeeId())
        && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to update this payment");
    }

    if (newAmount != null && newAmount.compareTo(BigDecimal.ZERO) > 0) {
      payment.setAmount(newAmount);
    }

    // Clear existing allocations (orphanRemoval will delete from DB)
    payment.getAllocations().clear();

    BigDecimal amount = payment.getAmount();
    List<SettlementAllocation> allocations = new ArrayList<>();

    if (newAllocations != null && !newAllocations.isEmpty()) {
      BigDecimal totalAllocated =
          newAllocations.stream()
              .map(CreateFriendshipSettlementRequest.Allocation::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (totalAllocated.compareTo(amount) != 0) {
        throw new IllegalArgumentException(
            "Total allocated amount ("
                + totalAllocated
                + ") must match payment amount ("
                + amount
                + ")");
      }

      for (CreateFriendshipSettlementRequest.Allocation allocationReq : newAllocations) {
        allocations.add(
            SettlementAllocation.builder()
                .groupId(allocationReq.getGroupId())
                .amount(allocationReq.getAmount())
                .build());
      }
    } else {
      allocations.addAll(resolveAndAllocate(payment.getPayerId(), payment.getPayeeId(), amount));
    }

    for (SettlementAllocation allocation : allocations) {
      payment.addAllocation(allocation);
    }

    return paymentRepository.save(payment);
  }

  /**
   * Resolves the payer's {@code DebtPosition} once, then lets the configured allocator decide the
   * Settlement Allocations. Shared by create and update so the resolver→allocator choreography
   * lives in one place.
   */
  private List<SettlementAllocation> resolveAndAllocate(
      Long payerId, Long payeeId, BigDecimal amount) {
    DebtPosition position = debtPositionResolver.resolve(payerId, payeeId);
    return settlementAutoAllocator.allocate(position, amount);
  }

  public boolean isParticipant(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId).orElse(null);
    if (payment == null) {
      return false;
    }
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return currentUserId.equals(payment.getPayerId())
        || currentUserId.equals(payment.getPayeeId())
        || splitzAuthorizer.isAdmin();
  }

  public boolean isPayer(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId).orElse(null);
    if (payment == null) {
      return false;
    }
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return currentUserId.equals(payment.getPayerId()) || splitzAuthorizer.isAdmin();
  }

  public boolean isPayee(Long paymentId) {
    Payment payment = paymentRepository.findById(paymentId).orElse(null);
    if (payment == null) {
      return false;
    }
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return currentUserId.equals(payment.getPayeeId()) || splitzAuthorizer.isAdmin();
  }
}
