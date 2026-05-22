package com.splitz.expense.controller;

import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.service.MembershipService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class MembershipController {

  private final MembershipService membershipService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @PostMapping("/{groupId}/members")
  public ResponseEntity<GroupDTO> addMember(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody AddMemberRequest request) {
    GroupDTO result =
        membershipService.addMember(groupId, request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @PostMapping("/{groupId}/members/bulk")
  public ResponseEntity<GroupDTO> bulkAddMembers(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody BulkAddMembersRequest request) {
    GroupDTO result =
        membershipService.bulkAddMembers(groupId, request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{groupId}/potential-members")
  public ResponseEntity<List<UserResponse>> getPotentialMembers(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(
        membershipService.getPotentialMembers(groupId, splitzAuthorizer.getCurrentUserId()));
  }

  @DeleteMapping("/{groupId}/members/{memberUserId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable("groupId") Long groupId, @PathVariable("memberUserId") Long memberUserId) {
    membershipService.removeMember(groupId, memberUserId, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{groupId}/members/{memberUserId}/role")
  public ResponseEntity<GroupDTO> updateMemberRole(
      @PathVariable("groupId") Long groupId,
      @PathVariable("memberUserId") Long memberUserId,
      @Valid @RequestBody UpdateMemberRoleRequest request) {
    GroupDTO result =
        membershipService.updateMemberRole(
            groupId, memberUserId, request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.ok(result);
  }
}
