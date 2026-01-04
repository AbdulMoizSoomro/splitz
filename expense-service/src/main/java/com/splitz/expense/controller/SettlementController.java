package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.service.SettlementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

  @PostMapping("/settlements")
  public ResponseEntity<SettlementDTO> createSettlement(
      @Valid @RequestBody CreateSettlementRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(settlementService.createSettlement(request));
  }

  @GetMapping("/settlements/{id}")
  public ResponseEntity<SettlementDTO> getSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(settlementService.getSettlementById(id));
  }

  @GetMapping("/groups/{groupId}/settlements")
  public ResponseEntity<List<SettlementDTO>> getSettlementsByGroup(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(settlementService.getSettlementsByGroup(groupId));
  }

  @PutMapping("/settlements/{id}/mark-paid")
  public ResponseEntity<SettlementDTO> markAsPaid(@PathVariable("id") Long id) {
    return ResponseEntity.ok(settlementService.markAsPaid(id, currentUserId()));
  }

  @PutMapping("/settlements/{id}/confirm")
  public ResponseEntity<SettlementDTO> confirmSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(settlementService.confirmSettlement(id, currentUserId()));
  }

  private Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      throw new AccessDeniedException("No authenticated user found");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException ex) {
      throw new AccessDeniedException("Authenticated username must be a numeric user id");
    }
  }
}
