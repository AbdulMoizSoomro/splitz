package com.splitz.expense.controller;

import com.splitz.expense.dto.GlobalActivityResponseDTO;
import com.splitz.expense.service.ActivityService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ActivityController {

  private final ActivityService activityService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @GetMapping("/activity")
  public ResponseEntity<GlobalActivityResponseDTO> getGlobalActivity() {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return ResponseEntity.ok(activityService.getGlobalActivity(currentUserId));
  }
}
