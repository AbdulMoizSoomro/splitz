package com.splitz.expense.service;

import com.splitz.expense.activity.ExpenseChange;
import com.splitz.expense.activity.ExpenseChange.SplitChange;
import com.splitz.expense.activity.ExpenseDiffCalculator;
import com.splitz.expense.calculator.ExpenseSplitEngine;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.mapper.ExpenseMapper;
import com.splitz.expense.model.ActivityLogType;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.repository.CategoryRepository;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
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
  private final ExpenseSplitEngine expenseSplitEngine;
  private final SharedSecurityAuthorizer splitzAuthorizer;
  private final GroupGovernance groupGovernance;
  private final ActivityLogService activityLogService;
  private final ExpenseDiffCalculator expenseDiffCalculator;

  @Transactional
  public ExpenseDTO createExpense(Long groupId, CreateExpenseRequest request, Long currentUserId) {
    groupGovernance.assertIsMember(groupId, currentUserId);
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

    List<ExpenseSplit> splits =
        expenseSplitEngine.applyInitialSplits(expense, request.getSplits(), request.getSplitType());
    expense.setSplits(splits);

    Expense savedExpense = expenseRepository.save(expense);
    activityLogService.logActivity(
        groupId,
        ActivityLogType.EXPENSE_CREATED,
        currentUserId,
        savedExpense.getId(),
        savedExpense.getDescription(),
        null);

    return expenseMapper.toDTO(savedExpense);
  }

  @Transactional(readOnly = true)
  public ExpenseDTO getExpense(Long id, Long currentUserId) {
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

    groupGovernance.assertIsMember(expense.getGroup().getId(), currentUserId);

    return expenseMapper.toDTO(expense);
  }

  @Transactional(readOnly = true)
  public List<ExpenseDTO> getExpensesByGroup(Long groupId, Long currentUserId) {
    groupGovernance.assertIsMember(groupId, currentUserId);
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

    SplitChange splitChange =
        request.getSplits() != null && !request.getSplits().isEmpty()
            ? SplitChange.MODIFIED
            : request.getAmount() != null ? SplitChange.RECALCULATED : SplitChange.NONE;

    Category resolvedCategory = null;
    if (request.getCategoryId() != null
        && (expense.getCategory() == null
            || !request.getCategoryId().equals(expense.getCategory().getId()))) {
      resolvedCategory =
          categoryRepository
              .findById(request.getCategoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Category not found with id: " + request.getCategoryId()));
    }

    ExpenseChange change = ExpenseChange.fromRequest(request, resolvedCategory, splitChange);
    String details = expenseDiffCalculator.calculate(expense, change);

    if (change.description() != null) {
      expense.setDescription(change.description());
    }
    if (change.amount() != null) {
      expense.setAmount(change.amount());
    }
    if (change.currency() != null) {
      expense.setCurrency(change.currency());
    }
    if (change.paidBy() != null && !change.paidBy().equals(expense.getPaidBy())) {
      if (!groupMemberRepository.existsByGroupIdAndUserId(
          expense.getGroup().getId(), change.paidBy())) {
        throw new IllegalArgumentException("Payer must be a member of the group");
      }
      expense.setPaidBy(change.paidBy());
    }
    if (resolvedCategory != null) {
      expense.setCategory(resolvedCategory);
    }
    if (change.expenseDate() != null) {
      expense.setExpenseDate(change.expenseDate());
    }
    if (change.notes() != null) {
      expense.setNotes(change.notes());
    }
    if (change.receiptUrl() != null) {
      expense.setReceiptUrl(change.receiptUrl());
    }

    expenseSplitEngine.reconcileSplits(
        expense, splitChange, request.getSplits(), request.getSplitType());

    expense.setLastModifiedBy(currentUserId);

    Expense savedExpense = expenseRepository.save(expense);

    activityLogService.logActivity(
        savedExpense.getGroup().getId(),
        ActivityLogType.EXPENSE_UPDATED,
        currentUserId,
        id,
        savedExpense.getDescription(),
        details);

    return expenseMapper.toDTO(savedExpense);
  }

  @Transactional
  public void deleteExpense(Long id, Long currentUserId) {
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));

    checkAuthorization(expense, currentUserId);

    activityLogService.logActivity(
        expense.getGroup().getId(),
        ActivityLogType.EXPENSE_DELETED,
        currentUserId,
        id,
        expense.getDescription(),
        null);

    expenseRepository.delete(expense);
  }

  private void checkAuthorization(Expense expense, Long currentUserId) {
    groupGovernance.assertCanEditExpense(
        expense.getGroup().getId(), currentUserId, expense.getPaidBy());
  }
}
