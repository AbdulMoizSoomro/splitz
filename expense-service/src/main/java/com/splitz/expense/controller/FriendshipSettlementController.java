package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.service.FriendshipSettlementService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
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
public class FriendshipSettlementController {

  private final FriendshipSettlementService friendshipSettlementService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @PostMapping("/friendship-settlements")
  @PreAuthorize(
      "@splitzAuthorizer.isSelfOrAdmin(#request.payerId) || @splitzAuthorizer.isSelfOrAdmin(#request.payeeId)")
  public ResponseEntity<FriendshipSettlementDTO> createSettlement(
      @Valid @RequestBody CreateFriendshipSettlementRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(friendshipSettlementService.createSettlement(request));
  }

  @GetMapping("/friendship-settlements/{id}")
  @PreAuthorize(
      "@splitzAuthorizer.isAdmin() || @friendshipSettlementService.isInvolved(#id, @splitzAuthorizer.currentUserId)")
  public ResponseEntity<FriendshipSettlementDTO> getSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(friendshipSettlementService.getSettlementById(id));
  }

  @GetMapping("/users/{userId1}/friendships/{userId2}/settlements")
  @PreAuthorize(
      "@splitzAuthorizer.isSelfOrAdmin(#userId1) || @splitzAuthorizer.isSelfOrAdmin(#userId2)")
  public ResponseEntity<List<FriendshipSettlementDTO>> getSettlementsBetweenUsers(
      @PathVariable("userId1") Long userId1, @PathVariable("userId2") Long userId2) {
    return ResponseEntity.ok(
        friendshipSettlementService.getSettlementsBetweenUsers(userId1, userId2));
  }

  @PutMapping("/friendship-settlements/{id}/mark-paid")
  @PreAuthorize(
      "@splitzAuthorizer.isAdmin() || @friendshipSettlementService.isPayer(#id, @splitzAuthorizer.currentUserId)")
  public ResponseEntity<FriendshipSettlementDTO> markAsPaid(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        friendshipSettlementService.markAsPaid(id, splitzAuthorizer.getCurrentUserId()));
  }

  @PutMapping("/friendship-settlements/{id}/confirm")
  @PreAuthorize(
      "@splitzAuthorizer.isAdmin() || @friendshipSettlementService.isPayee(#id, @splitzAuthorizer.currentUserId)")
  public ResponseEntity<FriendshipSettlementDTO> confirmSettlement(@PathVariable("id") Long id) {
    return ResponseEntity.ok(
        friendshipSettlementService.confirmSettlement(id, splitzAuthorizer.getCurrentUserId()));
  }
}
