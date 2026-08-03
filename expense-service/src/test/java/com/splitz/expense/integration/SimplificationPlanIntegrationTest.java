package com.splitz.expense.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SimplificationPlanIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private GroupSimplificationSettingsRepository settingsRepository;

  @MockBean private UserClient userClient;

  private String tokenFor(long userId) {
    var user =
        User.withUsername(String.valueOf(userId)).password("").authorities(List.of()).build();
    return "Bearer " + jwtUtil.generateToken(user);
  }

  @BeforeEach
  void before() {
    settingsRepository.deleteAll();
    expenseRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
    when(userClient.existsById(any())).thenReturn(true);
    when(userClient.getUsersByIds(any()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(100L).username("alice").build(),
                UserResponse.builder().id(200L).username("bob").build(),
                UserResponse.builder().id(300L).username("charlie").build()));
  }

  @AfterEach
  void after() {
    settingsRepository.deleteAll();
    expenseRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  private Group createGroupWithMembers(Long ownerId, Long... memberIds) {
    Group g = Group.builder().name("TestGroup").createdBy(ownerId).active(true).build();
    g.addMember(GroupMember.builder().userId(ownerId).role(GroupRole.ADMIN).build());
    for (Long mid : memberIds) {
      g.addMember(GroupMember.builder().userId(mid).role(GroupRole.MEMBER).build());
    }
    return groupRepository.save(g);
  }

  private void createEqualExpense(Group group, Long paidBy, BigDecimal total, Long[] splitUsers) {
    BigDecimal share =
        total.divide(BigDecimal.valueOf(splitUsers.length), 2, java.math.RoundingMode.HALF_UP);
    Expense expense =
        Expense.builder()
            .group(group)
            .description("Test Expense")
            .amount(total)
            .paidBy(paidBy)
            .currency("EUR")
            .splits(new ArrayList<>())
            .build();
    for (Long uid : splitUsers) {
      expense
          .getSplits()
          .add(
              ExpenseSplit.builder()
                  .expense(expense)
                  .userId(uid)
                  .splitType(SplitType.EQUAL)
                  .shareAmount(share)
                  .build());
    }
    expenseRepository.save(expense);
  }

  @Test
  void getPlan_returnsSimplifiedPlanWithTransactions() throws Exception {
    Group g = createGroupWithMembers(100L, 200L, 300L);
    // Alice paid 60.00 split equally among alice, bob, charlie (20 each).
    // Net: alice +40, bob -20, charlie -20 → 2 simplified transactions.
    createEqualExpense(g, 100L, new BigDecimal("60.00"), new Long[] {100L, 200L, 300L});

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("INTRA_GROUP"))
        .andExpect(jsonPath("$.status").value("PROPOSED"))
        .andExpect(jsonPath("$.simplificationEnabled").value(true))
        .andExpect(jsonPath("$.simplifiedTransactionCount").value(2))
        .andExpect(jsonPath("$.transactions[0].fromUsername").exists())
        .andExpect(jsonPath("$.transactions[0].toUsername").value("alice"));
  }

  @Test
  void getPlan_nonMemberForbidden() throws Exception {
    Group g = createGroupWithMembers(100L, 200L);

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(999L))
                .contentType(APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getPlan_nonExistentGroupForbiddenBySecurity() throws Exception {
    // @PreAuthorize runs before the method body, so a non-existent group (which has no
    // members) fails the membership check first → 403, not 404. This is correct: the
    // service-level 404 is covered by DebtSimplificationPlanServiceTest.
    mockMvc
        .perform(
            get("/groups/999999/simplification-plan")
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }
}
