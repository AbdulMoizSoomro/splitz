package com.splitz.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.allocator.DebtPosition;
import com.splitz.expense.allocator.DebtPositionResolver;
import com.splitz.expense.allocator.GroupDebt;
import com.splitz.expense.allocator.SettlementAutoAllocator;
import com.splitz.expense.lifecycle.PaymentLifecycle;
import com.splitz.expense.lifecycle.PaymentLifecycle.InitialState;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementAllocation;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private SettlementAllocationRepository settlementAllocationRepository;
  @Mock private SharedSecurityAuthorizer splitzAuthorizer;
  @Mock private SettlementAutoAllocator settlementAutoAllocator;
  @Mock private DebtPositionResolver debtPositionResolver;
  @Mock private PaymentLifecycle paymentLifecycle;

  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    paymentService =
        new PaymentService(
            paymentRepository,
            settlementAllocationRepository,
            splitzAuthorizer,
            settlementAutoAllocator,
            debtPositionResolver,
            paymentLifecycle);
  }

  @Test
  void shouldDelegateToAutoAllocatorOnCreatePaymentWithoutGroupIdOrExplicitAllocations() {
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal amount = new BigDecimal("100.00");

    when(splitzAuthorizer.getCurrentUserId()).thenReturn(payerId);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(paymentLifecycle.canCreate(payerId, payerId, payeeId, false)).thenReturn(true);
    when(paymentLifecycle.initialState(any(), any(), any(), any(LocalDateTime.class)))
        .thenReturn(InitialState.builder().status(SettlementStatus.MARKED_PAID).build());

    List<SettlementAllocation> expectedAllocations =
        List.of(SettlementAllocation.builder().groupId(10L).amount(amount).build());
    DebtPosition position =
        DebtPosition.builder()
            .payerId(payerId)
            .payeeId(payeeId)
            .debts(List.of(GroupDebt.builder().groupId(10L).owedAmount(amount).build()))
            .build();
    when(debtPositionResolver.resolve(payerId, payeeId)).thenReturn(position);
    when(settlementAutoAllocator.allocate(position, amount)).thenReturn(expectedAllocations);

    Payment savedPayment =
        Payment.builder()
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(amount)
            .status(SettlementStatus.MARKED_PAID)
            .build();
    when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

    Payment result = paymentService.createPayment(payerId, payeeId, amount, null, null);

    verify(debtPositionResolver).resolve(payerId, payeeId);
    verify(settlementAutoAllocator).allocate(position, amount);
    assertThat(result).isNotNull();
  }

  @Test
  void shouldDelegateToAutoAllocatorOnUpdatePaymentWithoutExplicitAllocations() {
    Long paymentId = 100L;
    Long payerId = 1L;
    Long payeeId = 2L;
    BigDecimal newAmount = new BigDecimal("150.00");

    Payment existingPayment =
        Payment.builder()
            .id(paymentId)
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(new BigDecimal("100.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(paymentRepository.findByIdWithLock(paymentId)).thenReturn(Optional.of(existingPayment));
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(payerId);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);

    DebtPosition position =
        DebtPosition.builder()
            .payerId(payerId)
            .payeeId(payeeId)
            .debts(List.of(GroupDebt.builder().groupId(10L).owedAmount(newAmount).build()))
            .build();

    List<SettlementAllocation> expectedAllocations =
        List.of(SettlementAllocation.builder().groupId(10L).amount(newAmount).build());
    when(debtPositionResolver.resolve(payerId, payeeId)).thenReturn(position);
    when(settlementAutoAllocator.allocate(position, newAmount)).thenReturn(expectedAllocations);

    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Payment result = paymentService.updatePayment(paymentId, newAmount, null);

    verify(debtPositionResolver).resolve(payerId, payeeId);
    verify(settlementAutoAllocator).allocate(position, newAmount);
    assertThat(result).isNotNull();
  }

  @Test
  void markAsPaidEnforcesActorCheckOnTheLoadedPaymentWithASingleFetch() {
    Long paymentId = 100L;
    Long payerId = 1L;
    Long otherUserId = 9L;
    Payment pending =
        Payment.builder()
            .id(paymentId)
            .payerId(payerId)
            .payeeId(2L)
            .amount(new BigDecimal("100.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(paymentRepository.findByIdWithLock(paymentId)).thenReturn(Optional.of(pending));
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(otherUserId);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(paymentLifecycle.markAsPaid(any(), any(), anyBoolean(), any(LocalDateTime.class)))
        .thenThrow(new com.splitz.expense.exception.UnauthorizedException("not authorized"));

    assertThatThrownBy(() -> paymentService.markAsPaid(paymentId))
        .isInstanceOf(com.splitz.expense.exception.UnauthorizedException.class);

    verify(paymentLifecycle).markAsPaid(eq(pending), eq(otherUserId), eq(false), any());
    verify(paymentRepository, times(1)).findByIdWithLock(paymentId);
    verify(paymentRepository, never()).findById(any());
  }

  @Test
  void confirmPaymentEnforcesActorCheckOnTheLoadedPayment() {
    Long paymentId = 100L;
    Long payeeId = 2L;
    Long otherUserId = 9L;
    Payment markedPaid =
        Payment.builder()
            .id(paymentId)
            .payerId(1L)
            .payeeId(payeeId)
            .amount(new BigDecimal("100.00"))
            .status(SettlementStatus.MARKED_PAID)
            .build();

    when(paymentRepository.findByIdWithLock(paymentId)).thenReturn(Optional.of(markedPaid));
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(otherUserId);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(paymentLifecycle.confirm(any(), any(), anyBoolean(), any(LocalDateTime.class)))
        .thenThrow(new com.splitz.expense.exception.UnauthorizedException("not authorized"));

    assertThatThrownBy(() -> paymentService.confirmPayment(paymentId))
        .isInstanceOf(com.splitz.expense.exception.UnauthorizedException.class);

    verify(paymentLifecycle).confirm(eq(markedPaid), eq(otherUserId), eq(false), any());
  }

  @Test
  void updatePaymentEnforcesActorCheckOnTheLoadedPayment() {
    Long paymentId = 100L;
    Long otherUserId = 9L;
    Payment pending =
        Payment.builder()
            .id(paymentId)
            .payerId(1L)
            .payeeId(2L)
            .amount(new BigDecimal("100.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(paymentRepository.findByIdWithLock(paymentId)).thenReturn(Optional.of(pending));
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(otherUserId);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    org.mockito.Mockito.doThrow(
            new com.splitz.expense.exception.UnauthorizedException("not authorized"))
        .when(paymentLifecycle)
        .assertUpdateAllowed(any(), any(), anyBoolean());

    assertThatThrownBy(
            () -> paymentService.updatePayment(paymentId, new BigDecimal("150.00"), null))
        .isInstanceOf(com.splitz.expense.exception.UnauthorizedException.class);

    verify(paymentLifecycle).assertUpdateAllowed(eq(pending), eq(otherUserId), eq(false));
  }

  @Test
  void getPaymentByIdEnforcesActorCheckOnTheLoadedPayment() {
    Long paymentId = 100L;
    Long otherUserId = 9L;
    Payment pending =
        Payment.builder()
            .id(paymentId)
            .payerId(1L)
            .payeeId(2L)
            .amount(new BigDecimal("100.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(pending));
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(otherUserId);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(paymentLifecycle.isParticipant(any(), any(), anyBoolean())).thenReturn(false);

    assertThatThrownBy(() -> paymentService.getPaymentById(paymentId))
        .isInstanceOf(com.splitz.expense.exception.UnauthorizedException.class);

    verify(paymentLifecycle).isParticipant(eq(pending), eq(otherUserId), eq(false));
  }
}
