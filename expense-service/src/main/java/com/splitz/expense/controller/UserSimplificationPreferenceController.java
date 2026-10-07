package com.splitz.expense.controller;

import com.splitz.expense.dto.UserOptOutRequest;
import com.splitz.expense.dto.UserSimplificationPreferenceDTO;
import com.splitz.expense.service.UserSimplificationPreferenceService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account-level debt simplification preference. Deliberately scoped to the authenticated user with
 * no group in the path and no admin gate: an account opt-out is a consent decision about one's own
 * debts, so no other user (admin or not) can read or change it.
 */
@RestController
@RequestMapping("/simplification-preferences/me")
@RequiredArgsConstructor
@Tag(
    name = "Debt Simplification",
    description = "Account-level debt simplification preference endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserSimplificationPreferenceController {

  private final UserSimplificationPreferenceService preferenceService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get my debt simplification preference",
      description =
          "Returns whether the authenticated user has opted out of debt netting account-wide. An"
              + " opt-out is a hard override applied in every group they belong to")
  public ResponseEntity<UserSimplificationPreferenceDTO> getMyPreference() {
    return ResponseEntity.ok(preferenceService.getPreference(splitzAuthorizer.getCurrentUserId()));
  }

  @PostMapping("/opt-out")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Opt in or out of account-wide debt netting",
      description =
          "Sets the authenticated user's account-level opt-out. Opting out excludes them from"
              + " suggested settlement plans in every group, including groups configured earlier")
  public ResponseEntity<UserSimplificationPreferenceDTO> toggleMyOptOut(
      @RequestBody UserOptOutRequest request) {
    return ResponseEntity.ok(
        preferenceService.setAccountOptOut(
            splitzAuthorizer.getCurrentUserId(), request.isOptOut()));
  }
}
