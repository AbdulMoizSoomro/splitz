package com.splitz.expense.security;

import com.splitz.expense.governance.GroupGovernance;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

  private final GroupGovernance groupGovernance;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  public boolean isGroupMember(Long groupId) {
    try {
      Long currentUserId = splitzAuthorizer.getCurrentUserId();
      return groupGovernance.isMember(groupId, currentUserId);
    } catch (Exception e) {
      return false;
    }
  }
}
