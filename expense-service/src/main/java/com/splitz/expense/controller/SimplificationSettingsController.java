package com.splitz.expense.controller;

import com.splitz.expense.dto.GroupSimplificationSettingsDTO;
import com.splitz.expense.dto.UpdateSimplificationSettingsRequest;
import com.splitz.expense.dto.UserOptOutRequest;
import com.splitz.expense.service.GroupSimplificationSettingsService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}/simplification-settings")
@RequiredArgsConstructor
public class SimplificationSettingsController {

  private final GroupSimplificationSettingsService settingsService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @GetMapping
  @PreAuthorize("@security.isGroupMember(#groupId)")
  public ResponseEntity<GroupSimplificationSettingsDTO> getSettings(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(settingsService.getSettings(groupId));
  }

  @PutMapping
  @PreAuthorize("@security.isGroupAdmin(#groupId)")
  public ResponseEntity<GroupSimplificationSettingsDTO> updateSettings(
      @PathVariable("groupId") Long groupId,
      @RequestBody UpdateSimplificationSettingsRequest request) {
    return ResponseEntity.ok(settingsService.updateSettings(groupId, request));
  }

  @PostMapping("/opt-out")
  @PreAuthorize("@security.isGroupMember(#groupId)")
  public ResponseEntity<GroupSimplificationSettingsDTO> toggleOptOut(
      @PathVariable("groupId") Long groupId, @RequestBody UserOptOutRequest request) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return ResponseEntity.ok(settingsService.toggleOptOut(groupId, currentUserId, request));
  }
}
