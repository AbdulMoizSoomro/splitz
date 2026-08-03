package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.*;
import com.splitz.expense.model.*;
import com.splitz.expense.repository.*;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CollaborativeExpenseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private GroupRepository groupRepository;

  @Autowired private GroupMemberRepository groupMemberRepository;

  @Autowired private ExpenseRepository expenseRepository;

  @Autowired private ActivityLogRepository activityLogRepository;

  @Autowired private JwtUtil jwtUtil;

  private String ownerToken;
  private String memberToken;
  private Long ownerId = 1L;
  private Long memberId = 2L;
  private Group group;

  @BeforeEach
  void setUp() {
    ownerToken =
        "Bearer "
            + jwtUtil.generateToken(
                ownerId.toString(), ownerId, Collections.singletonList("ROLE_USER"));
    memberToken =
        "Bearer "
            + jwtUtil.generateToken(
                memberId.toString(), memberId, Collections.singletonList("ROLE_USER"));

    group =
        Group.builder()
            .name("Test Group")
            .createdBy(ownerId)
            .allowMembersToEditExpenses(true)
            .build();
    group = groupRepository.save(group);

    GroupMember owner =
        GroupMember.builder().group(group).userId(ownerId).role(GroupRole.ADMIN).build();
    group.addMember(owner);
    groupMemberRepository.save(owner);

    GroupMember member =
        GroupMember.builder().group(group).userId(memberId).role(GroupRole.MEMBER).build();
    group.addMember(member);
    groupMemberRepository.save(member);

    group = groupRepository.save(group);

    // Other user is not in the group
  }

  @Test
  void updateExpense_AsPayer_Success() throws Exception {
    Expense expense = createExpense(ownerId, "Original Description", new BigDecimal("100.00"));

    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder()
            .description("Updated Description")
            .amount(new BigDecimal("150.00"))
            .build();

    mockMvc
        .perform(
            put("/groups/" + group.getId() + "/expenses/" + expense.getId())
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk());

    Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
    assertThat(updated.getDescription()).isEqualTo("Updated Description");
    assertThat(updated.getAmount()).isEqualByComparingTo("150.00");
    assertThat(updated.getLastModifiedBy()).isEqualTo(ownerId);

    // Check activity log
    List<ActivityLog> logs = activityLogRepository.findByGroupIdOrderByTimestampDesc(group.getId());
    assertThat(logs).filteredOn(l -> l.getType() == ActivityLogType.EXPENSE_UPDATED).hasSize(1);
    assertThat(logs.get(0).getDetails())
        .contains("description: Original Description -> Updated Description");
    assertThat(logs.get(0).getDetails()).contains("amount: 100.00 -> 150.00");
  }

  @Test
  void updateExpense_AsMember_Collaborative_Success() throws Exception {
    Expense expense = createExpense(ownerId, "Owner Expense", new BigDecimal("100.00"));

    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder().description("Member Updated Description").build();

    mockMvc
        .perform(
            put("/groups/" + group.getId() + "/expenses/" + expense.getId())
                .header("Authorization", memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk());

    Expense updated = expenseRepository.findById(expense.getId()).orElseThrow();
    assertThat(updated.getDescription()).isEqualTo("Member Updated Description");
    assertThat(updated.getLastModifiedBy()).isEqualTo(memberId);
  }

  @Test
  void updateExpense_AsMember_NonCollaborative_Forbidden() throws Exception {
    group.setAllowMembersToEditExpenses(false);
    groupRepository.save(group);

    Expense expense = createExpense(ownerId, "Owner Expense", new BigDecimal("100.00"));

    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder().description("Member Attempted Update").build();

    mockMvc
        .perform(
            put("/groups/" + group.getId() + "/expenses/" + expense.getId())
                .header("Authorization", memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteExpense_AsAdmin_Success() throws Exception {
    Expense expense = createExpense(memberId, "Member Expense", new BigDecimal("50.00"));

    mockMvc
        .perform(
            delete("/groups/" + group.getId() + "/expenses/" + expense.getId())
                .header("Authorization", ownerToken))
        .andExpect(status().isNoContent());

    assertThat(expenseRepository.findById(expense.getId())).isEmpty();

    // Check activity log
    List<ActivityLog> logs = activityLogRepository.findByGroupIdOrderByTimestampDesc(group.getId());
    assertThat(logs).filteredOn(l -> l.getType() == ActivityLogType.EXPENSE_DELETED).hasSize(1);
  }

  @Test
  void getGroupActivity_Success() throws Exception {
    createExpenseViaApi(ownerId, "Expense 1", new BigDecimal("10.00"));
    createExpenseViaApi(memberId, "Expense 2", new BigDecimal("20.00"));

    mockMvc
        .perform(get("/groups/" + group.getId() + "/activity").header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
        .andExpect(
            jsonPath(
                "$[*].entityName",
                org.hamcrest.Matchers.containsInAnyOrder("Expense 1", "Expense 2")))
        .andExpect(
            jsonPath(
                "$[*].type",
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("EXPENSE_CREATED"))));
  }

  private void createExpenseViaApi(Long paidBy, String description, BigDecimal amount)
      throws Exception {
    com.splitz.expense.dto.CreateExpenseRequest request =
        com.splitz.expense.dto.CreateExpenseRequest.builder()
            .description(description)
            .amount(amount)
            .paidBy(paidBy)
            .currency("EUR")
            .splitType(SplitType.EQUAL)
            .splits(List.of(com.splitz.expense.dto.SplitRequest.builder().userId(paidBy).build()))
            .build();

    mockMvc
        .perform(
            post("/groups/" + group.getId() + "/expenses")
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  private Expense createExpense(Long paidBy, String description, BigDecimal amount) {
    Expense expense =
        Expense.builder()
            .group(group)
            .description(description)
            .amount(amount)
            .paidBy(paidBy)
            .currency("EUR")
            .build();

    expense.setSplits(
        new ArrayList<>(
            Arrays.asList(
                ExpenseSplit.builder()
                    .expense(expense)
                    .userId(ownerId)
                    .shareAmount(amount.divide(new BigDecimal("2")))
                    .splitType(SplitType.EQUAL)
                    .build(),
                ExpenseSplit.builder()
                    .expense(expense)
                    .userId(memberId)
                    .shareAmount(amount.divide(new BigDecimal("2")))
                    .splitType(SplitType.EQUAL)
                    .build())));

    return expenseRepository.save(expense);
  }
}
