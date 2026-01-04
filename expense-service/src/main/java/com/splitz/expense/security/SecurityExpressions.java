package com.splitz.expense.security;

import com.splitz.expense.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

  private final GroupMemberRepository groupMemberRepository;

  public boolean isGroupMember(Long groupId) {
    Long currentUserId = getCurrentUserId();
    if (currentUserId == null) {
      return false;
    }
    return groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId);
  }

  public boolean isOwnerOrAdmin(Long userId) {
    Long currentUserId = getCurrentUserId();
    if (currentUserId == null) {
      return false;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean isAdmin =
        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

    return isAdmin || currentUserId.equals(userId);
  }

  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      return null;
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
