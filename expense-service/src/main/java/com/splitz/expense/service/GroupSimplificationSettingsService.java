package com.splitz.expense.service;

import com.splitz.expense.dto.GroupSimplificationSettingsDTO;
import com.splitz.expense.dto.UpdateSimplificationSettingsRequest;
import com.splitz.expense.dto.UserOptOutRequest;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.SimplificationScope;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupSimplificationSettingsService {

  private final GroupSimplificationSettingsRepository settingsRepository;

  @Transactional(readOnly = true)
  public GroupSimplificationSettingsDTO getSettings(Long groupId) {
    return toDTO(readSettings(groupId));
  }

  /**
   * Reads a group's simplification settings, returning the canonical {@link
   * GroupSimplificationSettings#defaults(Long)} when none are persisted. The single home for the
   * read (persisted-or-default) that the plan flow delegates to.
   */
  @Transactional(readOnly = true)
  public GroupSimplificationSettings readSettings(Long groupId) {
    return settingsRepository
        .findByGroupId(groupId)
        .orElseGet(() -> GroupSimplificationSettings.defaults(groupId));
  }

  @Transactional
  public GroupSimplificationSettingsDTO updateSettings(
      Long groupId, UpdateSimplificationSettingsRequest request) {
    GroupSimplificationSettings settings = getOrCreateSettings(groupId);
    settings.setSimplificationEnabled(request.isSimplificationEnabled());
    if (request.getSimplificationScope() != null && !request.getSimplificationScope().isBlank()) {
      settings.setSimplificationScope(
          SimplificationScope.valueOf(request.getSimplificationScope()));
    }
    settings = settingsRepository.save(settings);
    return toDTO(settings);
  }

  @Transactional
  public GroupSimplificationSettingsDTO toggleOptOut(
      Long groupId, Long userId, UserOptOutRequest request) {
    GroupSimplificationSettings settings = getOrCreateSettings(groupId);
    if (request.isOptOut()) {
      settings.getOptOutUserIds().add(userId);
    } else {
      settings.getOptOutUserIds().remove(userId);
    }
    settings = settingsRepository.save(settings);
    return toDTO(settings);
  }

  private GroupSimplificationSettings getOrCreateSettings(Long groupId) {
    return settingsRepository
        .findByGroupId(groupId)
        .orElseGet(() -> settingsRepository.save(GroupSimplificationSettings.defaults(groupId)));
  }

  private GroupSimplificationSettingsDTO toDTO(GroupSimplificationSettings settings) {
    return GroupSimplificationSettingsDTO.builder()
        .groupId(settings.getGroupId())
        .simplificationEnabled(settings.isSimplificationEnabled())
        .simplificationScope(settings.getSimplificationScope().name())
        .optOutUserIds(new HashSet<>(settings.getOptOutUserIds()))
        .build();
  }
}
