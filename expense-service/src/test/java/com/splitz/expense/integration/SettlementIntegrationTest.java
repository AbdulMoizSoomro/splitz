package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SettlementIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JwtUtil jwtUtil;

  @Autowired private GroupRepository groupRepository;

  @Autowired private GroupMemberRepository groupMemberRepository;

  @Autowired private PaymentRepository paymentRepository;

  private Group group;
  private String payerToken;
  private String payeeToken;
  private String strangerToken;
  private Long payerId = 101L;
  private Long payeeId = 102L;
  private Long strangerId = 103L;

  @BeforeEach
  void setUp() {
    group =
        groupRepository.save(
            Group.builder().name("Test Group").createdBy(payerId).active(true).build());
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(payerId).role(GroupRole.ADMIN).build());
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(payeeId).role(GroupRole.MEMBER).build());

    payerToken = tokenFor(payerId);
    payeeToken = tokenFor(payeeId);
    strangerToken = tokenFor(strangerId);
  }

  private String tokenFor(Long userId) {
    var user =
        org.springframework.security.core.userdetails.User.withUsername(userId.toString())
            .password("")
            .authorities(java.util.List.of())
            .build();
    return "Bearer " + jwtUtil.generateToken(user);
  }

  @AfterEach
  void tearDown() {
    paymentRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  void testSettlementLifecycle() throws Exception {
    // 1. Create Settlement (by Payer)
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .groupId(group.getId())
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(new BigDecimal("50.00"))
            .build();

    String response =
        mockMvc
            .perform(
                post("/settlements")
                    .header("Authorization", payerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("MARKED_PAID"))
            .andExpect(jsonPath("$.markedPaidAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long settlementId = objectMapper.readTree(response).get("id").asLong();

    // 2. Confirm (by Payee)
    mockMvc
        .perform(
            put("/settlements/" + settlementId + "/confirm").header("Authorization", payeeToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // 3. Verify in DB
    Payment settlement = paymentRepository.findById(settlementId).orElseThrow();
    assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
    assertThat(settlement.getSettledAt()).isNotNull();
  }

  @Test
  void testGetSettlementByNonParticipantReturnsForbidden() throws Exception {
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .groupId(group.getId())
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(new BigDecimal("50.00"))
            .build();

    String response =
        mockMvc
            .perform(
                post("/settlements")
                    .header("Authorization", payerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long settlementId = objectMapper.readTree(response).get("id").asLong();

    mockMvc
        .perform(get("/settlements/" + settlementId).header("Authorization", strangerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void testGetNonExistentSettlementReturnsNotFound() throws Exception {
    mockMvc
        .perform(get("/settlements/999999").header("Authorization", payerToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void testUnauthorizedMarkAsPaid() throws Exception {
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .groupId(group.getId())
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(new BigDecimal("50.00"))
            .build();

    String response =
        mockMvc
            .perform(
                post("/settlements")
                    .header("Authorization", payerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long settlementId = objectMapper.readTree(response).get("id").asLong();

    // Payee tries to mark as paid
    mockMvc
        .perform(
            put("/settlements/" + settlementId + "/mark-paid").header("Authorization", payeeToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void testGetSettlementsByGroup() throws Exception {
    // 1. Create a settlement
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .groupId(group.getId())
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(new BigDecimal("30.00"))
            .build();

    mockMvc
        .perform(
            post("/settlements")
                .header("Authorization", payerToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // 2. Fetch group settlements
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/groups/" + group.getId() + "/settlements")
                .header("Authorization", payerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].payerId").value(payerId))
        .andExpect(jsonPath("$[0].payeeId").value(payeeId))
        .andExpect(jsonPath("$[0].amount").value(30.00))
        .andExpect(jsonPath("$[0].groupId").value(group.getId()));
  }

  @Test
  void testDirectPaymentLifecycle() throws Exception {
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .payerId(payerId)
            .payeeId(payeeId)
            .amount(new BigDecimal("25.00"))
            .build();

    String response =
        mockMvc
            .perform(
                post("/payments/direct")
                    .header("Authorization", payerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("DIRECT"))
            .andExpect(jsonPath("$.status").value("MARKED_PAID"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long paymentId = objectMapper.readTree(response).get("id").asLong();

    Payment payment = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(payment.getType()).isEqualTo(com.splitz.expense.model.PaymentType.DIRECT);
    assertThat(payment.getGroupId()).isNull();
  }
}
