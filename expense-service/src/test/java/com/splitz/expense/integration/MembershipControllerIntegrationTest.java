package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.UpdateMemberRoleRequest;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementAllocationRepository;
import com.splitz.expense.service.BalanceService;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class MembershipControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;

  @MockBean private UserClient userClient;
  @MockBean private BalanceService balanceService;
  @MockBean private SettlementAllocationRepository settlementAllocationRepository;

  private String tokenFor(long userId) {
    var user =
        User.withUsername(String.valueOf(userId)).password("").authorities(List.of()).build();
    return "Bearer " + jwtUtil.generateToken(user);
  }

  @BeforeEach
  void before() {
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
    when(userClient.existsById(anyLong())).thenReturn(true);
  }

  @AfterEach
  void after() {
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  void addMember_AsAdmin_succeeds() throws Exception {
    Group g = Group.builder().name("G1").createdBy(100L).active(true).build();
    g.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    Group saved = groupRepository.save(g);

    AddMemberRequest req = new AddMemberRequest();
    req.setUserId(200L);
    req.setRole(GroupRole.MEMBER);

    mockMvc
        .perform(
            post("/groups/" + saved.getId() + "/members")
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.members").isArray());

    assertThat(groupMemberRepository.existsByGroupIdAndUserId(saved.getId(), 200L)).isTrue();
  }

  @Test
  void removeMember_WithNonZeroBalance_fails() throws Exception {
    Group g = Group.builder().name("G1").createdBy(100L).active(true).build();
    g.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    g.addMember(GroupMember.builder().userId(200L).role(GroupRole.MEMBER).build());
    Group saved = groupRepository.save(g);

    // Mock non-zero balance of $5.50
    when(balanceService.calculateUserBalanceInGroup(200L, saved.getId()))
        .thenReturn(new BigDecimal("5.50"));

    mockMvc
        .perform(
            delete("/groups/" + saved.getId() + "/members/200")
                .header("Authorization", tokenFor(100L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid Request State"));
  }

  @Test
  void removeMember_WithZeroBalanceAndNoPendingSettlements_succeeds() throws Exception {
    Group g = Group.builder().name("G1").createdBy(100L).active(true).build();
    g.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    g.addMember(GroupMember.builder().userId(200L).role(GroupRole.MEMBER).build());
    Group saved = groupRepository.save(g);

    // Mock zero balance and no active settlements
    when(balanceService.calculateUserBalanceInGroup(200L, saved.getId()))
        .thenReturn(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    when(settlementAllocationRepository.hasActiveSettlementsForUserInGroup(
            eq(200L), eq(saved.getId()), any()))
        .thenReturn(false);

    mockMvc
        .perform(
            delete("/groups/" + saved.getId() + "/members/200")
                .header("Authorization", tokenFor(100L)))
        .andExpect(status().isNoContent());

    assertThat(groupMemberRepository.findByGroupIdAndUserId(saved.getId(), 200L)).isEmpty();
  }

  @Test
  void updateMemberRole_OwnerDemotesAdmin_succeeds() throws Exception {
    Group g = Group.builder().name("G1").createdBy(100L).active(true).build();
    g.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    g.addMember(GroupMember.builder().userId(200L).role(GroupRole.ADMIN).build());
    Group saved = groupRepository.save(g);

    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setRole(GroupRole.MEMBER);

    mockMvc
        .perform(
            put("/groups/" + saved.getId() + "/members/200/role")
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[?(@.userId==200)].role").value("MEMBER"));
  }
}
