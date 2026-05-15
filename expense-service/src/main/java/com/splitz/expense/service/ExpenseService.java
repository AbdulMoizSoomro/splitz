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
  private final GroupService groupService;
  private final ActivityLogService activityLogService;

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

    List<ExpenseSplit> splits =
        calculateSplits(expense, request.getSplits(), request.getSplitType());
    expense.setSplits(splits);

    Expense savedExpense = expenseRepository.save(expense);
    activityLogService.logActivity(
        groupId,
        com.splitz.expense.model.ActivityLogType.EXPENSE_CREATED,
        currentUserId,
        savedExpense.getId(),
        savedExpense.getDescription(),
        null);

    return expenseMapper.toDTO(savedExpense);
  }

  private List<ExpenseSplit> calculateSplits(
      Expense expense, List<SplitRequest> splitRequests, SplitType splitType) {
    if (splitRequests == null || splitRequests.isEmpty()) {
      throw new IllegalArgumentException("At least one split is required");
    }

    BigDecimal totalAmount = expense.getAmount();

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

    StringBuilder diff = new StringBuilder();
    if (request.getDescription() != null
        && !request.getDescription().equals(expense.getDescription())) {
      diff.append("description: ")
          .append(expense.getDescription())
          .append(" -> ")
          .append(request.getDescription())
          .append("; ");
      expense.setDescription(request.getDescription());
    }
    if (request.getAmount() != null && request.getAmount().compareTo(expense.getAmount()) != 0) {
      diff.append("amount: ")
          .append(String.format("%.2f", expense.getAmount()))
          .append(" -> ")
          .append(String.format("%.2f", request.getAmount()))
          .append("; ");
      expense.setAmount(request.getAmount());
    }
    if (request.getCurrency() != null && !request.getCurrency().equals(expense.getCurrency())) {
      diff.append("currency: ")
          .append(expense.getCurrency())
          .append(" -> ")
          .append(request.getCurrency())
          .append("; ");
      expense.setCurrency(request.getCurrency());
    }
    if (request.getPaidBy() != null && !request.getPaidBy().equals(expense.getPaidBy())) {
      diff.append("paidBy changed; ");
      if (!groupMemberRepository.existsByGroupIdAndUserId(
          expense.getGroup().getId(), request.getPaidBy())) {
        throw new IllegalArgumentException("Payer must be a member of the group");
      }
      expense.setPaidBy(request.getPaidBy());
    }
    if (request.getCategoryId() != null
        && (expense.getCategory() == null
            || !request.getCategoryId().equals(expense.getCategory().getId()))) {
      Category category =
          categoryRepository
              .findById(request.getCategoryId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Category not found with id: " + request.getCategoryId()));
      diff.append("category: ")
          .append(expense.getCategory() != null ? expense.getCategory().getName() : "None")
          .append(" -> ")
          .append(category.getName())
          .append("; ");
      expense.setCategory(category);
    }
    if (request.getExpenseDate() != null
        && !request.getExpenseDate().equals(expense.getExpenseDate())) {
      diff.append("date changed; ");
      expense.setExpenseDate(request.getExpenseDate());
    }
    if (request.getNotes() != null && !request.getNotes().equals(expense.getNotes())) {
      diff.append("notes updated; ");
      expense.setNotes(request.getNotes());
    }
    if (request.getReceiptUrl() != null
        && !request.getReceiptUrl().equals(expense.getReceiptUrl())) {
      diff.append("receipt updated; ");
      expense.setReceiptUrl(request.getReceiptUrl());
    }

    if (request.getSplits() != null && !request.getSplits().isEmpty()) {
      SplitType splitType = request.getSplitType();
      if (splitType == null) {
        splitType =
            expense.getSplits().isEmpty()
                ? SplitType.EQUAL
                : expense.getSplits().get(0).getSplitType();
      }
      List<ExpenseSplit> newSplits = calculateSplits(expense, request.getSplits(), splitType);
      expense.getSplits().clear();
      expense.getSplits().addAll(newSplits);
      diff.append("splits: modified; ");
    } else if (request.getAmount() != null) {
      SplitType splitType =
          expense.getSplits().isEmpty()
              ? SplitType.EQUAL
              : expense.getSplits().get(0).getSplitType();
      List<SplitRequest> splitRequests =
          expense.getSplits().stream()
              .map(
                  s ->
                      SplitRequest.builder()
                          .userId(s.getUserId())
                          .splitValue(s.getSplitValue())
                          .build())
              .collect(Collectors.toList());
      List<ExpenseSplit> updatedSplits = calculateSplits(expense, splitRequests, splitType);
      expense.getSplits().clear();
      expense.getSplits().addAll(updatedSplits);
      diff.append("splits: recalculated; ");
    }

    expense.setLastModifiedBy(currentUserId);

    Expense savedExpense = expenseRepository.save(expense);

    activityLogService.logActivity(
        savedExpense.getGroup().getId(),
        com.splitz.expense.model.ActivityLogType.EXPENSE_UPDATED,
        currentUserId,
        id,
        savedExpense.getDescription(),
        diff.toString().trim());

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
        com.splitz.expense.model.ActivityLogType.EXPENSE_DELETED,
        currentUserId,
        id,
        expense.getDescription(),
        null);

    expenseRepository.delete(expense);
  }

  private void checkAuthorization(Expense expense, Long currentUserId) {
    if (splitzAuthorizer.isSelfOrAdmin(expense.getPaidBy())) {
      return;
    }

    if (groupService.canManageExpenses(expense.getGroup(), currentUserId, expense.getPaidBy())) {
      return;
    }

    throw new com.splitz.expense.exception.UnauthorizedException(
        "Only the expense creator or a group admin can modify/delete the expense");
  }
}
