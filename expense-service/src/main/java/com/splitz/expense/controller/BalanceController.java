package com.splitz.expense.controller;

import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.service.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Balances", description = "Balance calculation endpoints")
@SecurityRequirement(name = "bearerAuth")
public class BalanceController {

  private final BalanceService balanceService;

  @GetMapping("/groups/{id}/balances")
  @Operation(
      summary = "Get group balances",
      description = "Returns balance per member and simplified debts for a group")
  @PreAuthorize("@security.isGroupMember(#id)")
  public ResponseEntity<GroupBalanceResponseDTO> getGroupBalances(@PathVariable("id") Long id) {
    return ResponseEntity.ok(balanceService.getGroupBalances(id));
  }

  @GetMapping("/users/{id}/balances")
  @Operation(
      summary = "Get user balances",
      description = "Returns user's balances across all groups")
  @PreAuthorize("isAuthenticated() and (hasRole('ADMIN') or #id == principal.id)")
  public ResponseEntity<UserBalanceResponseDTO> getUserBalances(@PathVariable("id") Long id) {
    return ResponseEntity.ok(balanceService.getUserBalances(id));
  }
}
