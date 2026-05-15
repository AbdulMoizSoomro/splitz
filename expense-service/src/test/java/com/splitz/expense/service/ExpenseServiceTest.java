package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.calculator.SplitCalculator;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.ExpenseMapper;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.CategoryRepository;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

  @Mock private ExpenseRepository expenseRepository;

  @Mock private GroupRepository groupRepository;

  @Mock private GroupMemberRepository groupMemberRepository;

  @Mock private CategoryRepository categoryRepository;

  @Mock private ExpenseMapper expenseMapper;

  @Spy
  private SplitCalculator splitCalculator =
      new SplitCalculator(
          java.util.List.of(
              new com.splitz.expense.calculator.EqualSplitStrategy(),
              new com.splitz.expense.calculator.ExactSplitStrategy(),
              new com.splitz.expense.calculator.PercentageSplitStrategy(),
              new com.splitz.expense.calculator.SharesSplitStrategy(),
              new com.splitz.expense.calculator.AdjustmentSplitStrategy()),
          new com.splitz.expense.calculator.RemainderHandler());

  @Mock private SharedSecurityAuthorizer splitzAuthorizer;

  @Mock private GroupService groupService;

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
            .splits(
                new java.util.ArrayList<>(
                    List.of(
                        ExpenseSplit.builder()
                            .userId(100L)
                            .splitType(SplitType.EQUAL)
                            .shareAmount(new BigDecimal("60.00"))
                            .build())))
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
    lenient().when(splitzAuthorizer.isAdmin()).thenReturn(false);
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(false);
  }

  @Test
  void createExpense_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .categoryId(1L)
            .splitType(SplitType.EQUAL)
            .splits(List.of(SplitRequest.builder().userId(100L).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request, 100L);

    assertNotNull(result);
    assertEquals("Dinner", result.getDescription());
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_EqualSplit_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .splitType(SplitType.EQUAL)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build(),
                    SplitRequest.builder().userId(102L).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_ExactSplit_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .splitType(SplitType.EXACT)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("30.00")).build(),
                    SplitRequest.builder().userId(101L).splitValue(new BigDecimal("20.00")).build(),
                    SplitRequest.builder()
                        .userId(102L)
                        .splitValue(new BigDecimal("10.00"))
                        .build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_ExactSplit_InvalidSum_ThrowsException() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .splitType(SplitType.EXACT)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("30.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("20.00"))
                        .build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.createExpense(1L, request, 100L));
  }

  @Test
  void createExpense_PercentageSplit_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.PERCENTAGE)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("50.00")).build(),
                    SplitRequest.builder().userId(101L).splitValue(new BigDecimal("30.00")).build(),
                    SplitRequest.builder()
                        .userId(102L)
                        .splitValue(new BigDecimal("20.00"))
                        .build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_PercentageSplit_InvalidSum_ThrowsException() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.PERCENTAGE)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("50.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("40.00"))
                        .build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.createExpense(1L, request, 100L));
  }

  @Test
  void createExpense_SharesSplit_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.SHARES)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("2")).build(),
                    SplitRequest.builder().userId(101L).splitValue(new BigDecimal("1")).build(),
                    SplitRequest.builder().userId(102L).splitValue(new BigDecimal("1")).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_SharesSplit_ZeroShares_ThrowsException() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.SHARES)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("0")).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.createExpense(1L, request, 100L));
  }

  @Test
  void createExpense_AdjustmentSplit_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.ADJUSTMENT)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("10.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("-10.00"))
                        .build(),
                    SplitRequest.builder().userId(102L).splitValue(new BigDecimal("0.00")).build(),
                    SplitRequest.builder().userId(103L).splitValue(null).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.createExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void createExpense_AdjustmentSplit_InvalidSum_ThrowsException() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.ADJUSTMENT)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("10.00")).build(),
                    SplitRequest.builder().userId(101L).splitValue(new BigDecimal("5.00")).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.createExpense(1L, request, 100L));
  }

  @Test
  void createExpense_EqualSplit_Rounding_Success() {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Rounding Test")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.EQUAL)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build(),
                    SplitRequest.builder().userId(102L).build()))
            .build();

    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class)))
        .thenAnswer(
            invocation -> {
              Expense savedExpense = invocation.getArgument(0);
              assertEquals(3, savedExpense.getSplits().size());
              BigDecimal sum =
                  savedExpense.getSplits().stream()
                      .map(ExpenseSplit::getShareAmount)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              assertEquals(new BigDecimal("100.00"), sum);
              // First person should get 33.34, others 33.33
              assertEquals(
                  new BigDecimal("33.34"), savedExpense.getSplits().get(0).getShareAmount());
              assertEquals(
                  new BigDecimal("33.33"), savedExpense.getSplits().get(1).getShareAmount());
              assertEquals(
                  new BigDecimal("33.33"), savedExpense.getSplits().get(2).getShareAmount());
              return savedExpense;
            });
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    expenseService.createExpense(1L, request, 100L);
  }

  @Test
  void createExpense_GroupNotFound_ThrowsException() {
    CreateExpenseRequest request = CreateExpenseRequest.builder().build();
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(groupRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> expenseService.createExpense(1L, request, 100L));
  }

  @Test
  void createExpense_NotGroupMember_ThrowsException() {
    CreateExpenseRequest request = CreateExpenseRequest.builder().paidBy(100L).build();
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    // Creator 101L is member
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 101L)).thenReturn(true);
    // Payer 100L is NOT member
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(false);

    assertThrows(
        IllegalArgumentException.class, () -> expenseService.createExpense(1L, request, 101L));
  }

  @Test
  void createExpense_RequesterNotMember_ThrowsException() {
    CreateExpenseRequest request = CreateExpenseRequest.builder().build();
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 101L)).thenReturn(false);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);

    assertThrows(
        UnauthorizedException.class, () -> expenseService.createExpense(1L, request, 101L));
  }

  @Test
  void getExpense_Success() {
    ExpenseSplit split =
        ExpenseSplit.builder()
            .id(1L)
            .expense(expense)
            .userId(100L)
            .shareAmount(new BigDecimal("60.00"))
            .build();
    expense.setSplits(List.of(split));

    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.getExpense(1L, 100L);

    assertNotNull(result);
    assertEquals(1L, result.getId());
  }

  @Test
  void getExpensesByGroup_Success() {
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(groupRepository.existsById(1L)).thenReturn(true);
    when(expenseRepository.findByGroupId(1L)).thenReturn(List.of(expense));
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    List<ExpenseDTO> result = expenseService.getExpensesByGroup(1L, 100L);

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
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);
    // Mocking current user as creator (100L)
    when(expenseRepository.save(any(Expense.class))).thenReturn(expense);
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    ExpenseDTO result = expenseService.updateExpense(1L, request, 100L);

    assertNotNull(result);
    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void updateExpense_WithSplits_Success() {
    UpdateExpenseRequest request =
        UpdateExpenseRequest.builder()
            .description("Updated Dinner")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.EQUAL)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build()))
            .build();

    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(expenseRepository.save(any(Expense.class)))
        .thenAnswer(
            invocation -> {
              Expense savedExpense = invocation.getArgument(0);
              assertEquals(new BigDecimal("100.00"), savedExpense.getAmount());
              assertEquals(2, savedExpense.getSplits().size());
              assertEquals(100L, savedExpense.getLastModifiedBy());
              return savedExpense;
            });
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    expenseService.updateExpense(1L, request, 100L);

    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void updateExpense_AmountOnly_RecalculatesSplits() {
    // Current expense is 60.00, let's say it has 2 equal splits of 30.00
    ExpenseSplit split1 =
        ExpenseSplit.builder()
            .userId(100L)
            .splitType(SplitType.EQUAL)
            .shareAmount(new BigDecimal("30.00"))
            .build();
    ExpenseSplit split2 =
        ExpenseSplit.builder()
            .userId(101L)
            .splitType(SplitType.EQUAL)
            .shareAmount(new BigDecimal("30.00"))
            .build();
    expense.setSplits(new java.util.ArrayList<>(List.of(split1, split2)));

    UpdateExpenseRequest request =
        UpdateExpenseRequest.builder().amount(new BigDecimal("100.00")).build();

    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);
    when(expenseRepository.save(any(Expense.class)))
        .thenAnswer(
            invocation -> {
              Expense savedExpense = invocation.getArgument(0);
              assertEquals(new BigDecimal("100.00"), savedExpense.getAmount());
              assertEquals(2, savedExpense.getSplits().size());
              // Each split should now be 50.00
              assertEquals(
                  new BigDecimal("50.00"), savedExpense.getSplits().get(0).getShareAmount());
              assertEquals(
                  new BigDecimal("50.00"), savedExpense.getSplits().get(1).getShareAmount());
              return savedExpense;
            });
    when(expenseMapper.toDTO(any(Expense.class))).thenReturn(expenseDTO);

    expenseService.updateExpense(1L, request, 100L);

    verify(expenseRepository).save(any(Expense.class));
  }

  @Test
  void updateExpense_AdminSuccess() {
    UpdateExpenseRequest request =
        UpdateExpenseRequest.builder().description("Admin Update").build();

    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    // Current user 101L is not creator (100L) but is admin (handled by groupService)
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);
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
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> expenseService.updateExpense(1L, request, 100L));
  }

  @Test
  void updateExpense_NotAuthorized_ThrowsException() {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

    assertThrows(
        UnauthorizedException.class, () -> expenseService.updateExpense(1L, request, 101L));
  }

  @Test
  void updateExpense_UserNotMember_ThrowsException() {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().build();
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));

    assertThrows(
        UnauthorizedException.class, () -> expenseService.updateExpense(1L, request, 101L));
  }

  @Test
  void deleteExpense_Success() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    lenient().when(groupService.canManageExpenses(any(), any(), any())).thenReturn(true);

    expenseService.deleteExpense(1L, 100L);

    verify(expenseRepository).delete(expense);
  }

  @Test
  void deleteExpense_Collaborative_Success() {
    when(expenseRepository.findById(1L)).thenReturn(Optional.of(expense));
    // User 101L is not creator (100L) but group allows members to manage expenses
    when(groupService.canManageExpenses(group, 101L, 100L)).thenReturn(true);

    expenseService.deleteExpense(1L, 101L);

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

    assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpense(1L, 100L));
  }

  @Test
  void getExpensesByGroup_GroupNotFound_ThrowsException() {
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
    when(groupRepository.existsById(1L)).thenReturn(false);

    assertThrows(
        ResourceNotFoundException.class, () -> expenseService.getExpensesByGroup(1L, 100L));
  }

  @Test
  void getExpensesByGroupIds_Success() {
    List<Long> groupIds = Arrays.asList(1L, 2L);
    Group group2 = Group.builder().id(2L).name("Group 2").build();
    when(groupMemberRepository.findByUserId(100L))
        .thenReturn(
            List.of(
                GroupMember.builder().group(group).build(),
                GroupMember.builder().group(group2).build()));
    when(expenseRepository.findByGroupIdIn(groupIds)).thenReturn(List.of(expense));
    when(expenseMapper.toDTO(expense)).thenReturn(expenseDTO);

    List<ExpenseDTO> result = expenseService.getExpensesByGroupIds(groupIds, 100L);

    assertFalse(result.isEmpty());
    assertEquals(1, result.size());
  }

  @Test
  void getExpensesByGroupIds_PartialAccess_ThrowsUnauthorizedException() {
    List<Long> groupIds = Arrays.asList(1L, 2L);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(groupMemberRepository.findByUserId(100L))
        .thenReturn(List.of(GroupMember.builder().group(group).build())); // Only belongs to group 1

    assertThrows(
        com.splitz.expense.exception.UnauthorizedException.class,
        () -> expenseService.getExpensesByGroupIds(groupIds, 100L));
  }
}
