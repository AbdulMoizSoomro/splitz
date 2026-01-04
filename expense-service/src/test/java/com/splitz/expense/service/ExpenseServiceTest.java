package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

  @Mock private ExpenseRepository expenseRepository;

  @Mock private GroupRepository groupRepository;

  @Mock private GroupMemberRepository groupMemberRepository;

  @Mock private CategoryRepository categoryRepository;

  @Mock private ExpenseMapper expenseMapper;

  @InjectMocks private ExpenseService expenseService;

  private Group group;
  private Category category;
  private Expense expense;
  private ExpenseDTO expenseDTO;

  @BeforeEach
  void setUp() {
    group = Group.builder().id(1L).name("Test Group").build();
    category = Category.builder().id(1L).name("Food").build();
    expense =
        Expense.builder()
            .id(1L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .category(category)
            .build();
    expenseDTO =
        ExpenseDTO.builder()
            .id(1L)
            .groupId(1L)
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .categoryId(1L)
            .build();
  }

  @Test
  void createExpense_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .categoryId(1L)
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request);

    assertNotNull(result);
    assertEquals("Dinner", result.getDescription());
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_GroupNotFound_ThrowsException() {
    CreateExpenseRequest request = CreateExpenseRequest.builder().build();
    when(groupRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> expenseService.createExpense(1L, request));
  }

  @Test
  void createExpense_NotGroupMember_ThrowsException() {
    CreateExpenseRequest request = CreateExpenseRequest.builder().paidBy(100L).build();
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> expenseService.createExpense(1L, request));
  }

  @Test
  void getExpense_Success() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.getExpense(1L);

    assertNotNull(result);
    assertEquals(1L, result.getId());
  }

  @Test
  void getExpensesByGroup_Success() {
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(expenseRepository.findByGroupId(1L)).thenReturn(List.of(expense));
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    List<ExpenseDTO> result = expenseService.getExpensesByGroup(1L);

    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
  }

  @Test
  void updateExpense_Success() {
    UpdateExpenseRequest request =
        UpdateExpenseRequest.builder()
            .description("Updated Dinner")
            .amount(new BigDecimal("70.00"))
            .build();

    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    // Mocking current user as creator (100L)
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.updateExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void updateExpense_AdminSuccess() {
    UpdateExpenseRequest request =
        UpdateExpenseRequest.builder().description("Admin Update").build();

    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    // Current user 101L is not creator (100L) but is admin
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 101L))
        .thenReturn(Optional.of(GroupMember.builder().role(GroupRole.ADMIN).build()));
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.updateExpense(1L, request, 101L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void updateExpense_NotFound_ThrowsException() {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> expenseService.updateExpense(1L, request, 100L));
  }

  @Test
  void updateExpense_CategoryNotFound_ThrowsException() {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().categoryId(99L).build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> expenseService.updateExpense(1L, request, 100L));
  }

  @Test
  void updateExpense_NotAuthorized_ThrowsException() {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

    // Current user 101L is not creator (100L) and not admin
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 101L))
        .thenReturn(Optional.of(GroupMember.builder().role(GroupRole.MEMBER).build()));

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.updateExpense(1L, request, 101L));
  }

  @Test
  void updateExpense_UserNotMember_ThrowsException() {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    when(groupMemberRepository.findByGroupIdAndUserId(1L, 101L)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.updateExpense(1L, request, 101L));
  }

  @Test
  void deleteExpense_Success() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

    expenseService.deleteExpense(1L, 100L);

    verify(expenseRepository).delete(expense);
  }

  @Test
  void deleteExpense_NotFound_ThrowsException() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> expenseService.deleteExpense(1L, 100L));
  }

  @Test
  void getExpense_NotFound_ThrowsException() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpense(1L));
  }

  @Test
  void getExpensesByGroup_GroupNotFound_ThrowsException() {
    when(groupRepository.existsById(1L)).thenReturn(false);

    assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpensesByGroup(1L));
  }
}
