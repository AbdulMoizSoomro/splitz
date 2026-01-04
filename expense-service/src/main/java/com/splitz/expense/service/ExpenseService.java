package com.splitz.expense.service;

import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.ExpenseMapper;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.CategoryRepository;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
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

  @Transactional
  public ExpenseDTO createExpense(Long groupId, CreateExpenseRequest request) {
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

    return expenseMapper.toDTO(expenseRepository.save(expense));
  }

  @Transactional(readOnly = true)
  public ExpenseDTO getExpense(Long id) {
    Expense expense =
        expenseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
    return expenseMapper.toDTO(expense);
  }

  @Transactional(readOnly = true)
  public List<ExpenseDTO> getExpensesByGroup(Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }
    return expenseRepository.findByGroupId(groupId).stream()
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
    if (expense.getPaidBy().equals(currentUserId)) {
      return;
    }

    GroupMember member =
        groupMemberRepository
            .findByGroupIdAndUserId(expense.getGroup().getId(), currentUserId)
            .orElseThrow(() -> new IllegalArgumentException("User is not a member of the group"));

    if (member.getRole() != GroupRole.ADMIN) {
      throw new IllegalArgumentException(
          "Only the expense creator or a group admin can modify/delete the expense");
    }
  }
}
