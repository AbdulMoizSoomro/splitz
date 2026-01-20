package com.splitz.expense.integration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.Settlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementRepository;
import com.splitz.security.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SettlementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    private Group group;
    private String payerToken;
    private String payeeToken;
    private Long payerId = 101L;
    private Long payeeId = 102L;

    @BeforeEach
    void setUp() {
        group
                = groupRepository.save(
                        Group.builder().name("Test Group").createdBy(payerId).active(true).build());
        groupMemberRepository.save(
                GroupMember.builder().group(group).userId(payerId).role(GroupRole.ADMIN).build());
        groupMemberRepository.save(
                GroupMember.builder().group(group).userId(payeeId).role(GroupRole.MEMBER).build());

        payerToken = tokenFor(payerId);
        payeeToken = tokenFor(payeeId);
    }

    private String tokenFor(Long userId) {
        var user
                = org.springframework.security.core.userdetails.User.withUsername(userId.toString())
                        .password("")
                        .authorities(java.util.List.of())
                        .build();
        return "Bearer " + jwtUtil.generateToken(user);
    }

    @AfterEach
    void tearDown() {
        settlementRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
    }

    @Test
    void testSettlementLifecycle() throws Exception {
        // 1. Create Settlement
        CreateSettlementRequest request
                = CreateSettlementRequest.builder()
                        .groupId(group.getId())
                        .payerId(payerId)
                        .payeeId(payeeId)
                        .amount(new BigDecimal("50.00"))
                        .build();

        String response
                = mockMvc
                        .perform(
                                post("/settlements")
                                        .header("Authorization", payerToken)
                                        .contentType(APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Long settlementId = objectMapper.readTree(response).get("id").asLong();

        // 2. Mark as Paid (by Payer)
        mockMvc
                .perform(
                        put("/settlements/" + settlementId + "/mark-paid").header("Authorization", payerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MARKED_PAID"));

        // 3. Confirm (by Payee)
        mockMvc
                .perform(
                        put("/settlements/" + settlementId + "/confirm").header("Authorization", payeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Verify in DB
        Settlement settlement = settlementRepository.findById(settlementId).orElseThrow();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getSettledAt()).isNotNull();
    }

    @Test
    void testUnauthorizedMarkAsPaid() throws Exception {
        CreateSettlementRequest request
                = CreateSettlementRequest.builder()
                        .groupId(group.getId())
                        .payerId(payerId)
                        .payeeId(payeeId)
                        .amount(new BigDecimal("50.00"))
                        .build();

        String response
                = mockMvc
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
}
