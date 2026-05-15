package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.List;
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
public class SplitStrategyIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private ExpenseRepository expenseRepository;

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
    group = Group.builder().name("Split Test Group").createdBy(100L).active(true).build();
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
    expenseRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  void createExpense_EqualSplit() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Lunch")
            .amount(new BigDecimal("30.00"))
            .paidBy(100L)
            .splitType(SplitType.EQUAL)
            .splits(
                List.of(
                    SplitRequest.builder().userId(100L).build(),
                    SplitRequest.builder().userId(101L).build(),
                    SplitRequest.builder().userId(102L).build()))
            .build();

    var result =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    ExpenseDTO response =
        objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseDTO.class);
    assertThat(response.getSplits()).hasSize(3);
    assertThat(response.getSplits().get(0).getShareAmount()).isEqualByComparingTo("10.00");
    assertThat(response.getSplits().get(1).getShareAmount()).isEqualByComparingTo("10.00");
    assertThat(response.getSplits().get(2).getShareAmount()).isEqualByComparingTo("10.00");
  }

  @Test
  void createExpense_ExactSplit() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Gas")
            .amount(new BigDecimal("50.00"))
            .paidBy(100L)
            .splitType(SplitType.EXACT)
            .splits(
                List.of(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("20.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("30.00"))
                        .build()))
            .build();

    var result =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    ExpenseDTO response =
        objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseDTO.class);
    assertThat(response.getSplits()).hasSize(2);
    assertThat(response.getSplits().get(0).getShareAmount()).isEqualByComparingTo("20.00");
    assertThat(response.getSplits().get(1).getShareAmount()).isEqualByComparingTo("30.00");
  }

  @Test
  void createExpense_PercentageSplit() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Gift")
            .amount(new BigDecimal("100.00"))
            .paidBy(100L)
            .splitType(SplitType.PERCENTAGE)
            .splits(
                List.of(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("25.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("75.00"))
                        .build()))
            .build();

    var result =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    ExpenseDTO response =
        objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseDTO.class);
    assertThat(response.getSplits()).hasSize(2);
    assertThat(response.getSplits().get(0).getShareAmount()).isEqualByComparingTo("25.00");
    assertThat(response.getSplits().get(1).getShareAmount()).isEqualByComparingTo("75.00");
  }

  @Test
  void createExpense_SharesSplit() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Drinks")
            .amount(new BigDecimal("40.00"))
            .paidBy(100L)
            .splitType(SplitType.SHARES)
            .splits(
                List.of(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("1")).build(),
                    SplitRequest.builder().userId(101L).splitValue(new BigDecimal("3")).build()))
            .build();

    var result =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    ExpenseDTO response =
        objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseDTO.class);
    assertThat(response.getSplits()).hasSize(2);
    assertThat(response.getSplits().get(0).getShareAmount()).isEqualByComparingTo("10.00");
    assertThat(response.getSplits().get(1).getShareAmount()).isEqualByComparingTo("30.00");
  }

  @Test
  void createExpense_AdjustmentSplit() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Pizza")
            .amount(new BigDecimal("30.00"))
            .paidBy(100L)
            .splitType(SplitType.ADJUSTMENT)
            .splits(
                List.of(
                    SplitRequest.builder().userId(100L).splitValue(new BigDecimal("5.00")).build(),
                    SplitRequest.builder()
                        .userId(101L)
                        .splitValue(new BigDecimal("-5.00"))
                        .build()))
            .build();

    var result =
        mockMvc
            .perform(
                post("/groups/" + group.getId() + "/expenses")
                    .header("Authorization", tokenFor(100L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    ExpenseDTO response =
        objectMapper.readValue(result.getResponse().getContentAsString(), ExpenseDTO.class);
    assertThat(response.getSplits()).hasSize(2);
    // Base share for 2 people of 30.00 is 15.00.
    // User 100: 15.00 + 5.00 = 20.00
    // User 101: 15.00 - 5.00 = 10.00
    assertThat(response.getSplits().get(0).getShareAmount()).isEqualByComparingTo("20.00");
    assertThat(response.getSplits().get(1).getShareAmount()).isEqualByComparingTo("10.00");
  }
}
