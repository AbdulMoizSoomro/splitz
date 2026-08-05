package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.dto.UpdateFriendshipSettlementRequest;
import com.splitz.expense.mapper.PaymentMapper;
import com.splitz.expense.model.Payment;
import com.splitz.expense.service.PaymentService;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FriendshipSettlementController {

  private final PaymentService paymentService;
  private final PaymentMapper paymentMapper;

  @PostMapping("/friendship-settlements")
  @PreAuthorize(
      "@splitzAuthorizer.isSelfOrAdmin(#request.payerId) ||"
          + " @splitzAuthorizer.isSelfOrAdmin(#request.payeeId)")
  public ResponseEntity<List<FriendshipSettlementDTO>> createSettlement(
      @Valid @RequestBody CreateFriendshipSettlementRequest request) {
    Payment payment =
        paymentService.createPayment(
            request.getPayerId(),
            request.getPayeeId(),
            request.getAmount(),
            request.getGroupId(),
            request.getAllocations());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Collections.singletonList(paymentMapper.toFriendshipSettlementDTO(payment)));
  }

  @GetMapping("/friendship-settlements/{id}")
  public ResponseEntity<FriendshipSettlementDTO> getSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        paymentMapper.toFriendshipSettlementDTO(paymentService.getPaymentById(id)));
  }

  @GetMapping("/users/{userId1}/friendships/{userId2}/settlements")
  @PreAuthorize("@splitzAuthorizer.isSelfOrAdmin(#userId1)")
  public ResponseEntity<List<FriendshipSettlementDTO>> getSettlementsBetweenUsers(
      @PathVariable("userId1") Long userId1, @PathVariable("userId2") Long userId2) {
    return ResponseEntity.ok(
        paymentMapper.toFriendshipSettlementDTOs(
            paymentService.getPaymentsBetweenUsers(userId1, userId2)));
  }

  @PutMapping("/friendship-settlements/{id}")
  public ResponseEntity<FriendshipSettlementDTO> updateSettlement(
      @PathVariable("id") Long id, @Valid @RequestBody UpdateFriendshipSettlementRequest request) {
    Payment updated =
        paymentService.updatePayment(id, request.getAmount(), request.getAllocations());
    return ResponseEntity.ok(paymentMapper.toFriendshipSettlementDTO(updated));
  }

  @PutMapping("/friendship-settlements/{id}/mark-paid")
  public ResponseEntity<FriendshipSettlementDTO> markAsPaid(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        paymentMapper.toFriendshipSettlementDTO(paymentService.markAsPaid(id)));
  }

  @PutMapping("/friendship-settlements/{id}/confirm")
  public ResponseEntity<FriendshipSettlementDTO> confirmSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        paymentMapper.toFriendshipSettlementDTO(paymentService.confirmPayment(id)));
  }
}
