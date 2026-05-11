package com.splitz.expense.service;

import com.splitz.expense.calculator.SplitCalculator;
import com.splitz.expense.calculator.SplitResult;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.ExpenseMapper;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.CategoryRepository;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final GroupRepository groupRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final CategoryRepository categoryRepository;
  private final ExpenseMapper expenseMapper;
  private final SplitCalculator splitCalculator;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @Transactional
  public ExpenseDTO createExpense(Long groupId, CreateExpenseRequest request, Long currentUserId) {
    if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)
        && !splitzAuthorizer.isAdmin()) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "Only group members can create expenses");
    }
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Group not found with id: " + groupId));

    if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getPaidBy())) {
      throw new IllegalArgumentException("Payer must be a member of the group");
    }

    Category category = null;
    if (request.getCategoryId() != null) {
      category =
          categoryRepository
              .findById(request.getCategoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Category not found with id: " + request.getCategoryId()));
    }

    Expense expense =
        Expense.builder()
            .group(group)
            .description(request.getDescription())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paidBy(request.getPaidBy())
            .category(category)
            .expenseDate(request.getExpenseDate())
            .notes(request.getNotes())
            .receiptUrl(request.getReceiptUrl())
            .build();

    List<ExpenseSplit> splits = calculateSplits(expense, request);
    expense.setSplits(splits);

    return expenseMapper.toDTO(expenseRepository.save(expense));
  }

  private List<ExpenseSplit> calculateSplits(Expense expense, CreateExpenseRequest request) {
    List<SplitRequest> splitRequests = request.getSplits();

    if (splitRequests == null || splitRequests.isEmpty()) {
      throw new IllegalArgumentException("At least one split is required");
    }

    BigDecimal totalAmount = expense.getAmount();
    SplitType splitType = request.getSplitType();

    List<SplitResult> results =
        splitCalculator.calculate(totalAmount, splitType, splitRequests, expense.getCurrency());
    return results.stream()
        .map(
            r ->
                ExpenseSplit.builder()
                    .expense(expense)
                    .userId(r.userId())
                    .splitType(r.splitType())
                    .splitValue(r.splitValue())
                    .shareAmount(r.shareAmount())
                    .build())
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public ExpenseDTO getExpense(Long id, Long currentUserId) {
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

    if (!groupMemberRepository.existsByGroupIdAndUserId(expense.getGroup().getId(), currentUserId)
        && !splitzAuthorizer.isAdmin()) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "Only group members can view this expense");
    }

    return expenseMapper.toDTO(expense);
  }

  @Transactional(readOnly = true)
  public List<ExpenseDTO> getExpensesByGroup(Long groupId, Long currentUserId) {
    if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)
        && !splitzAuthorizer.isAdmin()) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "Only group members can view group expenses");
    }
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }
    return expenseRepository.findByGroupId(groupId).stream()
        .map(expenseMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<ExpenseDTO> getExpensesByGroupIds(List<Long> groupIds, Long currentUserId) {
    if (!splitzAuthorizer.isAdmin()) {
      List<Long> userGroupIds =
          groupMemberRepository.findByUserId(currentUserId).stream()
              .map(gm -> gm.getGroup().getId())
              .toList();

      if (!userGroupIds.containsAll(groupIds)) {
        throw new com.splitz.expense.exception.UnauthorizedException(
            "You are not authorized to view expenses for one or more of the requested groups");
      }
    }

    if (groupIds.isEmpty()) {
      return java.util.Collections.emptyList();
    }

    return expenseRepository.findByGroupIdIn(groupIds).stream()
        .map(expenseMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Transactional
  public ExpenseDTO updateExpense(Long id, UpdateExpenseRequest request, Long currentUserId) {
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

    checkAuthorization(expense, currentUserId);

    if (request.getDescription() != null) {
      expense.setDescription(request.getDescription());
    }
    if (request.getAmount() != null) {
      expense.setAmount(request.getAmount());
    }
    if (request.getCurrency() != null) {
      expense.setCurrency(request.getCurrency());
    }
    if (request.getCategoryId() != null) {
      Category category =
          categoryRepository
              .findById(request.getCategoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Category not found with id: " + request.getCategoryId()));
      expense.setCategory(category);
    }
    if (request.getExpenseDate() != null) {
      expense.setExpenseDate(request.getExpenseDate());
    }
    if (request.getNotes() != null) {
      expense.setNotes(request.getNotes());
    }
    if (request.getReceiptUrl() != null) {
      expense.setReceiptUrl(request.getReceiptUrl());
    }

    return expenseMapper.toDTO(expenseRepository.save(expense));
  }

  @Transactional
  public void deleteExpense(Long id, Long currentUserId) {
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

    checkAuthorization(expense, currentUserId);

    expenseRepository.delete(expense);
  }

  private void checkAuthorization(Expense expense, Long currentUserId) {
    if (expense.getPaidBy().equals(currentUserId) || splitzAuthorizer.isAdmin()) {
      return;
    }

    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(expense.getGroup().getId(), currentUserId)
            .orElseThrow(
                () ->
                    new com.splitz.expense.exception.UnauthorizedException(
                        "User is not a member of the group"));

    if (member.getRole() != GroupRole.ADMIN) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "Only the expense creator or a group admin can modify/delete the expense");
    }
  }
}
