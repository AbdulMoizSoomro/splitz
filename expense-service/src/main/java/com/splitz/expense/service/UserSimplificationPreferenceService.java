package com.splitz.expense.service;

import com.splitz.expense.dto.UserSimplificationPreferenceDTO;
import com.splitz.expense.model.UserSimplificationPreference;
import com.splitz.expense.repository.UserSimplificationPreferenceRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the account-level debt simplification opt-out invariant.
 *
 * <p>An account opt-out is a <em>hard override</em>, not an inherited default: while it is set the
 * user is excluded from netting in every group they belong to, including groups configured before
 * the opt-out existed. Opt-out is a consent guarantee (issue #67), so it must never be weakened by
 * a group's pre-existing configuration. The single home for that rule, so the plan flow and the
 * settings endpoints cannot disagree about who is excluded.
 */
@Service
@RequiredArgsConstructor
public class UserSimplificationPreferenceService {

  private final UserSimplificationPreferenceRepository preferenceRepository;

  @Transactional(readOnly = true)
  public UserSimplificationPreferenceDTO getPreference(Long userId) {
    return toDTO(readPreference(userId));
  }

  /** Reads a user's preference, falling back to the canonical opted-in default. */
  @Transactional(readOnly = true)
  public UserSimplificationPreference readPreference(Long userId) {
    return preferenceRepository
        .findByUserId(userId)
        .orElseGet(() -> UserSimplificationPreference.defaults(userId));
  }

  @Transactional
  public UserSimplificationPreferenceDTO setAccountOptOut(Long userId, boolean accountOptOut) {
    return toDTO(persist(userId, accountOptOut));
  }

  private UserSimplificationPreference persist(Long userId, boolean accountOptOut) {
    UserSimplificationPreference preference =
        preferenceRepository
            .findByUserId(userId)
            .orElseGet(() -> UserSimplificationPreference.defaults(userId));
    preference.setAccountOptOut(accountOptOut);
    return preferenceRepository.save(preference);
  }

  /**
   * The set of users that must be excluded from netting for a group: those the group itself opted
   * out, plus any member who opted out at the account level. Account overrides are resolved against
   * the group's members only, so an unrelated user's preference never leaks into another group's
   * plan.
   */
  @Transactional(readOnly = true)
  public Set<Long> effectiveOptOutUserIds(
      Set<Long> groupOptOutUserIds, Collection<Long> memberIds) {
    Set<Long> effective = new HashSet<>(groupOptOutUserIds);
    if (memberIds == null || memberIds.isEmpty()) {
      return effective;
    }
    Set<Long> members = new HashSet<>(memberIds);
    effective.addAll(preferenceRepository.findOptedOutUserIdsIn(members));
    return effective;
  }

  private UserSimplificationPreferenceDTO toDTO(UserSimplificationPreference preference) {
    return UserSimplificationPreferenceDTO.builder()
        .userId(preference.getUserId())
        .accountOptOut(preference.isAccountOptOut())
        .build();
  }
}
