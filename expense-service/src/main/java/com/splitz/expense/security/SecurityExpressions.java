package com.splitz.expense.security;

import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

  private final GroupMemberRepository groupMemberRepository;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  public boolean isGroupMember(Long groupId) {
    try {
      Long currentUserId = splitzAuthorizer.getCurrentUserId();
      return groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId);
    } catch (Exception e) {
      return false;
    }
  }
}
