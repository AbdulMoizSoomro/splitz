package com.splitz.expense.service;

import com.splitz.expense.dto.GlobalActivityResponseDTO;
import com.splitz.expense.mapper.ExpenseMapper;
import com.splitz.expense.mapper.PaymentMapper;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.PaymentRepository;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

  private final ExpenseRepository expenseRepository;
  private final PaymentRepository paymentRepository;
  private final ExpenseMapper expenseMapper;
  private final PaymentMapper paymentMapper;

  @Override
  @Transactional(readOnly = true)
  public GlobalActivityResponseDTO getGlobalActivity(Long userId) {
    return GlobalActivityResponseDTO.builder()
        .expenses(
            expenseRepository.findAllByInvolvedUserId(userId).stream()
                .map(expenseMapper::toDTO)
                .collect(Collectors.toList()))
        .settlements(
            paymentRepository.findByPayerIdOrPayeeId(userId, userId).stream()
                .map(paymentMapper::toSettlementDTO)
                .collect(Collectors.toList()))
        .build();
  }
}
