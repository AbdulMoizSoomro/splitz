package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.service.SettlementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SettlementController {

  private final SettlementService settlementService;
  private final com.splitz.security.authorization.SharedSecurityAuthorizer splitzAuthorizer;

  @PostMapping("/settlements")
  public ResponseEntity<SettlementDTO> createSettlement(
      @Valid @RequestBody CreateSettlementRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(settlementService.createSettlement(request, splitzAuthorizer.getCurrentUserId()));
  }

  @GetMapping("/settlements/{id}")
  public ResponseEntity<SettlementDTO> getSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        settlementService.getSettlementById(id, splitzAuthorizer.getCurrentUserId()));
  }

  @GetMapping("/groups/{groupId}/settlements")
  public ResponseEntity<List<SettlementDTO>> getSettlementsByGroup(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(
        settlementService.getSettlementsByGroup(groupId, splitzAuthorizer.getCurrentUserId()));
  }

  @PutMapping("/settlements/{id}/mark-paid")
  public ResponseEntity<SettlementDTO> markAsPaid(@PathVariable("id") Long id) {
    return ResponseEntity.ok(settlementService.markAsPaid(id, splitzAuthorizer.getCurrentUserId()));
  }

  @PutMapping("/settlements/{id}/confirm")
  public ResponseEntity<SettlementDTO> confirmSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        settlementService.confirmSettlement(id, splitzAuthorizer.getCurrentUserId()));
  }
}
