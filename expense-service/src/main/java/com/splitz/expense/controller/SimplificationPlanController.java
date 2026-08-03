package com.splitz.expense.controller;

import com.splitz.expense.dto.DebtSimplificationPlanDTO;
import com.splitz.expense.service.DebtSimplificationPlanService;
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
@RequestMapping("/groups/{groupId}/simplification-plan")
@RequiredArgsConstructor
@Tag(name = "Debt Simplification", description = "Suggested Settlement Plan endpoints")
@SecurityRequirement(name = "bearerAuth")
public class SimplificationPlanController {

  private final DebtSimplificationPlanService planService;

  @GetMapping
  @Operation(
      summary = "Get suggested settlement plan",
      description =
          "Computes a read-only debt simplification plan (Suggested Settlement Plan) for the"
              + " group, honouring governance opt-outs and scope settings")
  @PreAuthorize("@security.isGroupMember(#groupId)")
  public ResponseEntity<DebtSimplificationPlanDTO> getPlan(@PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(planService.computePlan(groupId));
  }
}
