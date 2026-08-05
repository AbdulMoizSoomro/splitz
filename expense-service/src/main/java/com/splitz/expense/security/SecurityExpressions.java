package com.splitz.expense.security;

import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Thin SpEL adapter that bridges <code>@PreAuthorize</code> expressions to the GroupGovernance
 * seam. Only authorization failures (missing identity, insufficient authority) are swallowed as
 * denial (<code>false</code>); infrastructure and not-found exceptions propagate so they surface as
 * 500s/404s rather than masquerading as 403s. A global admin satisfies the membership checks, in
 * line with {@link GroupGovernance#assertIsMember}.
 */
@Component("security")
@RequiredArgsConstructor
public class SecurityExpressions {

  private final GroupGovernance groupGovernance;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  public boolean isGroupMember(Long groupId) {
    try {
      Long currentUserId = splitzAuthorizer.getCurrentUserId();
      if (splitzAuthorizer.isAdmin()) {
        return true;
      }
      return groupGovernance.isMember(groupId, currentUserId);
    } catch (AccessDeniedException | UnauthorizedException e) {
      return false;
    }
  }

  public boolean isGroupAdmin(Long groupId) {
    try {
      Long currentUserId = splitzAuthorizer.getCurrentUserId();
      groupGovernance.assertCanManageGroup(groupId, currentUserId);
      return true;
    } catch (AccessDeniedException | UnauthorizedException e) {
      return false;
    }
  }
}
