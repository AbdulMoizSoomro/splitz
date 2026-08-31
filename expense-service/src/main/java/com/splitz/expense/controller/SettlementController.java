package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.dto.UpdateSettlementRequest;
import com.splitz.expense.mapper.PaymentMapper;
import com.splitz.expense.model.Payment;
import com.splitz.expense.service.PaymentService;
import jakarta.validation.Valid;
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
public class SettlementController {

  private final PaymentService paymentService;
  private final PaymentMapper paymentMapper;

  @PostMapping({"/settlements", "/friendship-settlements"})
  @PreAuthorize(
      "(#request.groupId != null && @security.isGroupMember(#request.groupId)) ||"
          + " @splitzAuthorizer.isSelfOrAdmin(#request.payerId) ||"
          + " @splitzAuthorizer.isSelfOrAdmin(#request.payeeId)")
  public ResponseEntity<SettlementDTO> createSettlement(
      @Valid @RequestBody CreateSettlementRequest request) {
    Payment payment =
        paymentService.createPayment(
            request.getPayerId(),
            request.getPayeeId(),
            request.getAmount(),
            request.getGroupId(),
            request.getAllocations());
    return ResponseEntity.status(HttpStatus.CREATED).body(paymentMapper.toSettlementDTO(payment));
  }

  @GetMapping({"/settlements/{id}", "/friendship-settlements/{id}"})
  public ResponseEntity<SettlementDTO> getSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(paymentMapper.toSettlementDTO(paymentService.getPaymentById(id)));
  }

  @GetMapping("/groups/{groupId}/settlements")
  @PreAuthorize("@security.isGroupMember(#groupId)")
  public ResponseEntity<List<SettlementDTO>> getSettlementsByGroup(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(
        paymentMapper.toSettlementDTOs(paymentService.getPaymentsByGroup(groupId)));
  }

  @GetMapping("/users/{userId1}/friendships/{userId2}/settlements")
  @PreAuthorize("@splitzAuthorizer.isSelfOrAdmin(#userId1)")
  public ResponseEntity<List<SettlementDTO>> getSettlementsBetweenUsers(
      @PathVariable("userId1") Long userId1, @PathVariable("userId2") Long userId2) {
    return ResponseEntity.ok(
        paymentMapper.toSettlementDTOs(paymentService.getPaymentsBetweenUsers(userId1, userId2)));
  }

  @PutMapping({"/settlements/{id}", "/friendship-settlements/{id}"})
  public ResponseEntity<SettlementDTO> updateSettlement(
      @PathVariable("id") Long id, @Valid @RequestBody UpdateSettlementRequest request) {
    Payment updated =
        paymentService.updatePayment(id, request.getAmount(), request.getAllocations());
    return ResponseEntity.ok(paymentMapper.toSettlementDTO(updated));
  }

  @PutMapping({"/settlements/{id}/mark-paid", "/friendship-settlements/{id}/mark-paid"})
  public ResponseEntity<SettlementDTO> markAsPaid(@PathVariable("id") Long id) {
    return ResponseEntity.ok(paymentMapper.toSettlementDTO(paymentService.markAsPaid(id)));
  }

  @PutMapping({"/settlements/{id}/confirm", "/friendship-settlements/{id}/confirm"})
  public ResponseEntity<SettlementDTO> confirmSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(paymentMapper.toSettlementDTO(paymentService.confirmPayment(id)));
  }
}
