package com.splitz.expense.controller;

import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.BulkAddMembersRequest;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.service.GroupService;
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
public class GroupController {

  private final GroupService groupService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @PostMapping
  public ResponseEntity<GroupDTO> createGroup(@Valid @RequestBody CreateGroupRequest request) {
    GroupDTO result = groupService.createGroup(request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping
  public ResponseEntity<List<GroupDTO>> getGroupsForCurrentUser() {
    return ResponseEntity.ok(groupService.getGroupsForUser(splitzAuthorizer.getCurrentUserId()));
  }

  @GetMapping("/{groupId}")
  public ResponseEntity<GroupDTO> getGroup(@PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(groupService.getGroup(groupId, splitzAuthorizer.getCurrentUserId()));
  }

  @PutMapping("/{groupId}")
  public ResponseEntity<GroupDTO> updateGroup(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody UpdateGroupRequest request) {
    return ResponseEntity.ok(
        groupService.updateGroup(groupId, request, splitzAuthorizer.getCurrentUserId()));
  }

  @DeleteMapping("/{groupId}")
  public ResponseEntity<Void> deleteGroup(@PathVariable("groupId") Long groupId) {
    groupService.deleteGroup(groupId, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{groupId}/members")
  public ResponseEntity<GroupDTO> addMember(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody AddMemberRequest request) {
    GroupDTO result = groupService.addMember(groupId, request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @PostMapping("/{groupId}/members/bulk")
  public ResponseEntity<GroupDTO> bulkAddMembers(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody BulkAddMembersRequest request) {
    GroupDTO result =
        groupService.bulkAddMembers(groupId, request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{groupId}/potential-members")
  public ResponseEntity<List<UserResponse>> getPotentialMembers(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(
        groupService.getPotentialMembers(groupId, splitzAuthorizer.getCurrentUserId()));
  }

  @DeleteMapping("/{groupId}/members/{memberUserId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable("groupId") Long groupId, @PathVariable("memberUserId") Long memberUserId) {
    groupService.removeMember(groupId, memberUserId, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{groupId}/members/{memberUserId}/role")
  public ResponseEntity<GroupDTO> updateMemberRole(
      @PathVariable("groupId") Long groupId,
      @PathVariable("memberUserId") Long memberUserId,
      @Valid @RequestBody UpdateMemberRoleRequest request) {
    GroupDTO result =
        groupService.updateMemberRole(
            groupId, memberUserId, request, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.ok(result);
  }
}
