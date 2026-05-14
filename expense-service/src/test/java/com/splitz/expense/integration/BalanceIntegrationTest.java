package com.splitz.expense.integration;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementRepository;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BalanceIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private SettlementRepository settlementRepository;

  @MockBean private UserClient userClient;

  private Group group;

  private String tokenFor(long userId) {
    var user =
        User.withUsername(String.valueOf(userId)).password("").authorities(List.of()).build();
    return "Bearer " + jwtUtil.generateToken(user);
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
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(102L).role(GroupRole.MEMBER).build());

    when(userClient.existsById(anyLong())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    cleanup();
  }

  private void cleanup() {
    settlementRepository.deleteAll();
    expenseRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  void getGroupBalances_ReturnsEnrichedData() throws Exception {
    // Create an expense
    CreateExpenseRequest expenseRequest =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("90.00"))
            .paidBy(100L)
            .splitType(SplitType.EQUAL)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build(),
                    SplitRequest.builder().userId(102L).build()))
            .build();

    mockMvc
        .perform(
            post("/groups/" + group.getId() + "/expenses")
                .header("Authorization", tokenFor(100L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(expenseRequest)))
        .andExpect(status().isCreated());

    // Mock UserClient responses
    UserResponse u100 =
        UserResponse.builder().id(100L).username("alice").email("alice@example.com").build();
    UserResponse u101 =
        UserResponse.builder().id(101L).username("bob").email("bob@example.com").build();
    UserResponse u102 =
        UserResponse.builder().id(102L).username("charlie").email("charlie@example.com").build();

    when(userClient.getUsersByIds(anyList())).thenReturn(Arrays.asList(u100, u101, u102));

    // Get balances
    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/balances").header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balances[?(@.userId==100)].username").value("alice"))
        .andExpect(jsonPath("$.balances[?(@.userId==101)].username").value("bob"))
        .andExpect(jsonPath("$.balances[?(@.userId==102)].username").value("charlie"))
        .andExpect(jsonPath("$.simplifiedDebts[0].fromUsername").exists())
        .andExpect(jsonPath("$.simplifiedDebts[0].toUsername").exists());
  }

  @Test
  void getUserBalances_ReturnsEnrichedData() throws Exception {
    UserResponse u100 =
        UserResponse.builder().id(100L).username("alice").email("alice@example.com").build();
    when(userClient.getUserById(100L)).thenReturn(Optional.of(u100));
    when(userClient.getUsersByIds(anyList())).thenReturn(List.of(u100));

    mockMvc
        .perform(get("/users/100/balances").header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(100L))
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.email").value("alice@example.com"));
  }

  @Test
  void deleteExpense_UpdatesBalances() throws Exception {
    // 1. Create an expense (Alice pays 90, shared with Bob and Charlie)
    CreateExpenseRequest expenseRequest =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("90.00"))
            .paidBy(100L)
            .splitType(SplitType.EQUAL)
            .splits(
                Arrays.asList(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build(),
                    SplitRequest.builder().userId(102L).build()))
            .build();

    String response =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(expenseRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long expenseId = objectMapper.readTree(response).get("id").asLong();

    // 2. Verify balances (Alice: +60, Bob: -30, Charlie: -30)
    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/balances").header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balances[?(@.userId==100)].balance").value(60.0))
        .andExpect(jsonPath("$.balances[?(@.userId==101)].balance").value(-30.0));

    // 3. Delete expense
    mockMvc
        .perform(
            delete("/groups/" + group.getId() + "/expenses/" + expenseId)
                .header("Authorization", tokenFor(100L)))
        .andExpect(status().isNoContent());

    // 4. Verify balances are back to zero
    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/balances").header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balances[?(@.userId==100)].balance").value(0.0))
        .andExpect(jsonPath("$.balances[?(@.userId==101)].balance").value(0.0));
  }
}
