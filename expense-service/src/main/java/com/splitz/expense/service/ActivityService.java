package com.splitz.expense.service;

import com.splitz.expense.dto.GlobalActivityResponseDTO;

public interface ActivityService {
  GlobalActivityResponseDTO getGlobalActivity(Long userId);
}
