package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ExpenseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private ExpenseRepository expenseRepository;

  @MockBean private UserClient userClient;

  private Group group;

  private String tokenFor(long userId) {
    return tokenFor(userId, false);
  }

  private String tokenFor(long userId, boolean isAdmin) {
    List<String> roles = isAdmin ? List.of("ROLE_ADMIN") : List.of("ROLE_USER");
    return "Bearer " + jwtUtil.generateToken(String.valueOf(userId), userId, roles);
  }

  @BeforeEach
  void setUp() {
    cleanup();
    group = Group.builder().name("Test Group").createdBy(100L).active(true).build();
    group = groupRepository.save(group);

    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(100L).role(GroupRole.ADMIN).build());
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(101L).role(GroupRole.MEMBER).build());

    when(userClient.existsById(anyLong())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    cleanup();
  }

  private void cleanup() {
    expenseRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  void updateExpense_Owner_Success() throws Exception {
    Expense expense = createTestExpense(100L);
    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder().description("Updated Lunch").build();

    mockMvc
        .perform(
            put("/expenses/" + expense.getId())
                .header("Authorization", tokenFor(100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Updated Lunch"));
  }

  @Test
  void getExpensesByGroupIds_Success() throws Exception {
    // Group 1 already exists from setUp
    createTestExpense(100L);

    // Create Group 2
    Group group2 = Group.builder().name("Group 2").createdBy(100L).active(true).build();
    group2 = groupRepository.save(group2);
    groupMemberRepository.save(
        GroupMember.builder().group(group2).userId(100L).role(GroupRole.ADMIN).build());

    Expense e2 =
        Expense.builder()
            .group(group2)
            .description("Group 2 Expense")
            .amount(new BigDecimal("20.00"))
            .paidBy(100L)
            .currency("USD")
            .build();
    expenseRepository.save(e2);

    mockMvc
        .perform(
            get("/groups/expenses/bulk")
                .param("groupIds", group.getId().toString(), group2.getId().toString())
                .header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.description == 'Test')]").exists())
        .andExpect(jsonPath("$[?(@.description == 'Group 2 Expense')]").exists());
  }

  @Test
  void updateExpense_GroupAdmin_Success() throws Exception {
    // 100L is group admin, 101L is owner
    Expense expense = createTestExpense(101L);
    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder().description("Admin Update").build();

    mockMvc
        .perform(
            put("/expenses/" + expense.getId())
                .header("Authorization", tokenFor(100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk());
  }

  @Test
  void updateExpense_SystemAdmin_Success() throws Exception {
    // 999L is not in group but is system admin
    Expense expense = createTestExpense(101L);
    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder().description("System Admin Update").build();

    mockMvc
        .perform(
            put("/expenses/" + expense.getId())
                .header("Authorization", tokenFor(999L, true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk());
  }

  @Test
  void updateExpense_NonOwnerNonAdmin_Forbidden() throws Exception {
    // Disable collaborative editing for this test
    group.setAllowMembersToEditExpenses(false);
    groupRepository.save(group);

    // 101L is member but not owner, 100L is owner
    Expense expense = createTestExpense(100L);
    UpdateExpenseRequest updateRequest =
        UpdateExpenseRequest.builder().description("Illegal Update").build();

    mockMvc
        .perform(
            put("/expenses/" + expense.getId())
                .header("Authorization", tokenFor(101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());
  }

  private Expense createTestExpense(Long paidBy) {
    Expense expense =
        Expense.builder()
            .group(group)
            .description("Test")
            .amount(new BigDecimal("10.00"))
            .paidBy(paidBy)
            .currency("USD")
            .build();
    return expenseRepository.save(expense);
  }

  @Test
  void createExpense_ExactSplit_EndToEnd() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Lunch")
            .amount(new BigDecimal("30.00"))
            .paidBy(100L)
            .currency("USD")
            .splitType(SplitType.EXACT)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("10.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("20.00"))
                        .build()))
            .build();

    MvcResult result =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").value("Lunch"))
            .andExpect(jsonPath("$.amount").value(30.00))
            .andReturn();

    ExpenseDTO response =
        objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseDTO.class);

    // Verify persistence
    Expense savedExpense = expenseRepository.findById(response.getId()).orElseThrow();
    assertThat(savedExpense.getDescription()).isEqualTo("Lunch");
    assertThat(savedExpense.getSplits()).hasSize(2);

    ExpenseSplit split1 =
        savedExpense.getSplits().stream()
            .filter(s -> s.getUserId().equals(100L))
            .findFirst()
            .orElseThrow();
    assertThat(split1.getShareAmount()).isEqualByComparingTo("10.00");
    assertThat(split1.getSplitType()).isEqualTo(SplitType.EXACT);

    ExpenseSplit split2 =
        savedExpense.getSplits().stream()
            .filter(s -> s.getUserId().equals(101L))
            .findFirst()
            .orElseThrow();
    assertThat(split2.getShareAmount()).isEqualByComparingTo("20.00");
  }

  @Test
  void createExpense_EqualSplit_EndToEnd_WithRounding() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Rounding Test")
            .amount(new BigDecimal("10.00"))
            .paidBy(100L)
            .currency("USD")
            .splitType(SplitType.EQUAL)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build(),
                    SplitRequest.builder().userId(102L).build())) // 10 / 3 = 3.33 + 3.33 + 3.34
            .build();

    // Need to add 102L to group
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(102L).role(GroupRole.MEMBER).build());

    mockMvc
        .perform(
            post("/groups/" + group.getId() + "/expenses")
                .header("Authorization", tokenFor(100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    List<Expense> expenses = expenseRepository.findByGroupId(group.getId());
    assertThat(expenses).hasSize(1);
    Expense savedExpense = expenses.get(0);

    BigDecimal totalSplits =
        savedExpense.getSplits().stream()
            .map(ExpenseSplit::getShareAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(totalSplits).isEqualByComparingTo("10.00");
  }
}
