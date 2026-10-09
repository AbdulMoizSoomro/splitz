package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.dto.UpdateSimplificationSettingsRequest;
import com.splitz.expense.dto.UserOptOutRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.SplitType;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
import com.splitz.expense.repository.UserSimplificationPreferenceRepository;
import com.splitz.security.JwtUtil;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the account-level debt simplification preference: the REST surface, that it is scoped to
 * the authenticated user, and the hard-override invariant reaching a group's suggested plan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UserSimplificationPreferenceIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
  @Autowired private UserSimplificationPreferenceRepository preferenceRepository;
  @Autowired private ExpenseRepository expenseRepository;
  @Autowired private GroupSimplificationSettingsRepository settingsRepository;

  @MockBean private UserClient userClient;

  private static final Long ALICE = 100L;
  private static final Long BOB = 200L;
  private static final Long CHARLIE = 300L;

  private String tokenFor(long userId) {
    var user =
        User.withUsername(String.valueOf(userId)).password("").authorities(List.of()).build();
    return "Bearer " + jwtUtil.generateToken(user);
  }

  @BeforeEach
  void before() {
    preferenceRepository.deleteAll();
    expenseRepository.deleteAll();
    settingsRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
    when(userClient.existsById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
  }

  @AfterEach
  void after() {
    preferenceRepository.deleteAll();
    expenseRepository.deleteAll();
    settingsRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
  }

  private Group createGroupWithMembers(Long ownerId, Long memberId) {
    Group g = Group.builder().name("TestGroup").createdBy(ownerId).active(true).build();
    g.addMember(GroupMember.builder().userId(ownerId).role(GroupRole.ADMIN).build());
    if (memberId != null) {
      g.addMember(GroupMember.builder().userId(memberId).role(GroupRole.MEMBER).build());
    }
    return groupRepository.save(g);
  }

  private Group createGroupWithDebt() throws Exception {
    return createGroupWithDebt("50.00");
  }

  private Group createGroupWithDebt(String secondDebt) throws Exception {
    Group group = createGroupWithMembers(ALICE, BOB);
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(CHARLIE).role(GroupRole.MEMBER).build());
    when(userClient.getUsersByIds(anyList()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(ALICE).username("alice").build(),
                UserResponse.builder().id(BOB).username("bob").build(),
                UserResponse.builder().id(CHARLIE).username("charlie").build()));
    // Bob owes Alice 100; Charlie owes Bob 50. Bob's net balance alone hides half his debt.
    createDebt(group, ALICE, BOB, "100.00");
    createDebt(group, BOB, CHARLIE, secondDebt);
    return group;
  }

  private void createDebt(Group group, Long payer, Long debtor, String amount) throws Exception {
    CreateExpenseRequest expense =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal(amount))
            .paidBy(payer)
            .splitType(SplitType.EXACT)
            .splits(
                List.of(
                    SplitRequest.builder()
                        .userId(debtor)
                        .splitValue(new BigDecimal(amount))
                        .build()))
            .build();
    mockMvc
        .perform(
            post("/groups/" + group.getId() + "/expenses")
                .header("Authorization", tokenFor(payer))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(expense)))
        .andExpect(status().isCreated());
  }

  private void setAccountOptOut(Long userId, boolean optedOut) throws Exception {
    UserOptOutRequest request = new UserOptOutRequest();
    request.setOptOut(optedOut);
    mockMvc
        .perform(
            post("/simplification-preferences/me/opt-out")
                .header("Authorization", tokenFor(userId))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  private void setSimplification(Group group, boolean enabled, String scope) throws Exception {
    mockMvc
        .perform(
            put("/groups/" + group.getId() + "/simplification-settings")
                .header("Authorization", tokenFor(ALICE))
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateSimplificationSettingsRequest(enabled, scope))))
        .andExpect(status().isOk());
  }

  @Test
  void accountOptOut_preservesOriginalDebtInGroupBalances() throws Exception {
    Group group = createGroupWithDebt();
    setAccountOptOut(BOB, true);

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/balances").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.simplifiedDebts.length()").value(2))
        .andExpect(jsonPath("$.simplifiedDebts[?(@.from==200 && @.to==100)].amount").value(100.0))
        .andExpect(jsonPath("$.simplifiedDebts[?(@.from==300 && @.to==200)].amount").value(50.0));
  }

  @Test
  void accountOptOut_preventsDebtTransfersThroughMemberInPlan() throws Exception {
    Group group = createGroupWithDebt();
    setAccountOptOut(BOB, true);

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds[0]").value(BOB))
        .andExpect(jsonPath("$.transactions").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"50.00", "100.00"})
  void accountOptOut_preservesCounterpartiesEvenWithZeroNetBalance(String secondDebt)
      throws Exception {
    Group group = createGroupWithDebt(secondDebt);
    setAccountOptOut(BOB, true);

    mockMvc
        .perform(get("/users/200/counterparties").header("Authorization", tokenFor(BOB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.userId==100)].balance").value(-100.0))
        .andExpect(jsonPath("$[?(@.userId==300)].balance").value(Double.parseDouble(secondDebt)));
    mockMvc
        .perform(get("/users/100/counterparties").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].userId").value(BOB))
        .andExpect(jsonPath("$[0].balance").value(100.0))
        .andExpect(jsonPath("$[0].groups[0].id").value(group.getId()));
  }

  @Test
  void accountOptOut_preservesDebtsInBothDirections() throws Exception {
    Group group = createGroupWithDebt();
    createDebt(group, CHARLIE, BOB, "100.00");
    setAccountOptOut(BOB, true);

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/balances").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.simplifiedDebts.length()").value(2))
        .andExpect(jsonPath("$.simplifiedDebts[?(@.from==200 && @.to==100)].amount").value(100.0))
        .andExpect(jsonPath("$.simplifiedDebts[?(@.from==200 && @.to==300)].amount").value(50.0));
  }

  @Test
  void accountOptOut_stillSimplifiesDebtsBetweenEligibleMembers() throws Exception {
    Group group = createGroupWithDebt();
    groupMemberRepository.save(
        GroupMember.builder().group(group).userId(400L).role(GroupRole.MEMBER).build());
    createDebt(group, CHARLIE, 400L, "20.00");
    setAccountOptOut(BOB, true);

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].fromUserId").value(400L))
        .andExpect(jsonPath("$.transactions[0].toUserId").value(CHARLIE))
        .andExpect(jsonPath("$.transactions[0].amount").value(20.0));
    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/balances").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.simplifiedDebts.length()").value(3));
  }

  @Test
  void optingBackIn_restoresNetting() throws Exception {
    Group group = createGroupWithDebt();
    setAccountOptOut(BOB, true);
    setAccountOptOut(BOB, false);

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds").isEmpty())
        .andExpect(jsonPath("$.transactions.length()").value(2));
    mockMvc
        .perform(get("/users/100/counterparties").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.userId==200)].balance").value(50.0))
        .andExpect(jsonPath("$[?(@.userId==300)].balance").value(50.0));
  }

  @Test
  void optingBackIn_doesNotOverrideGroupOptOut() throws Exception {
    Group group = createGroupWithDebt();
    setAccountOptOut(BOB, true);
    mockMvc
        .perform(
            post("/groups/" + group.getId() + "/simplification-settings/opt-out")
                .header("Authorization", tokenFor(BOB))
                .contentType(APPLICATION_JSON)
                .content("{\"optOut\":true}"))
        .andExpect(status().isOk());
    setAccountOptOut(BOB, false);

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds[0]").value(BOB))
        .andExpect(jsonPath("$.transactions").isEmpty());
    mockMvc
        .perform(get("/users/100/counterparties").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(100.0));
  }

  @Test
  void disabledNetting_preservesDebtsAndReportsAccountOptOut() throws Exception {
    Group group = createGroupWithDebt();
    setAccountOptOut(BOB, true);
    setSimplification(group, false, "INTRA_GROUP");

    mockMvc
        .perform(
            get("/groups/" + group.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds[0]").value(BOB))
        .andExpect(jsonPath("$.transactions").isEmpty());
    mockMvc
        .perform(get("/users/100/counterparties").header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].balance").value(100.0));
  }

  @Test
  void crossGroupPlan_preservesAccountOptOutAcrossContributingGroups() throws Exception {
    Group first = createGroupWithMembers(ALICE, BOB);
    Group second = createGroupWithMembers(ALICE, BOB);
    for (Group group : List.of(first, second)) {
      groupMemberRepository.save(
          GroupMember.builder().group(group).userId(CHARLIE).role(GroupRole.MEMBER).build());
    }
    when(userClient.getUsersByIds(anyList())).thenReturn(List.of());
    createDebt(first, ALICE, BOB, "100.00");
    createDebt(second, BOB, CHARLIE, "50.00");
    setAccountOptOut(BOB, true);
    setSimplification(first, true, "CROSS_GROUP");

    mockMvc
        .perform(
            get("/groups/" + first.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("CROSS_GROUP"))
        .andExpect(jsonPath("$.optedOutUserIds[0]").value(BOB))
        .andExpect(jsonPath("$.transactions").isEmpty());
    mockMvc
        .perform(get("/users/200/counterparties").header("Authorization", tokenFor(BOB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.userId==100)].balance").value(-100.0))
        .andExpect(jsonPath("$[?(@.userId==300)].balance").value(50.0));
  }

  @Test
  void crossGroupPlan_doesNotSubtractDebtsOutsideMembersCurrentMemberships() throws Exception {
    Group anchor = createGroupWithMembers(ALICE, BOB);
    groupMemberRepository.save(
        GroupMember.builder().group(anchor).userId(CHARLIE).role(GroupRole.MEMBER).build());
    Group historical = createGroupWithMembers(BOB, ALICE);
    groupMemberRepository.save(
        GroupMember.builder().group(historical).userId(400L).role(GroupRole.MEMBER).build());
    when(userClient.getUsersByIds(anyList())).thenReturn(List.of());
    createDebt(anchor, CHARLIE, ALICE, "100.00");
    createDebt(historical, BOB, ALICE, "100.00");
    createDebt(historical, ALICE, 400L, "100.00");

    // Alice's historical pairwise debts cancel, so the Settled Membership Invariant permits
    // leaving.
    mockMvc
        .perform(
            delete("/groups/" + historical.getId() + "/members/" + ALICE)
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isNoContent());
    setAccountOptOut(BOB, true);
    setSimplification(anchor, true, "CROSS_GROUP");

    mockMvc
        .perform(
            get("/groups/" + anchor.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactions.length()").value(1))
        .andExpect(jsonPath("$.transactions[0].fromUserId").value(ALICE))
        .andExpect(jsonPath("$.transactions[0].toUserId").value(CHARLIE))
        .andExpect(jsonPath("$.transactions[0].amount").value(100.0));
  }

  @Test
  void getPreference_defaultsToOptedIn() throws Exception {
    mockMvc
        .perform(get("/simplification-preferences/me").header("Authorization", tokenFor(BOB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(BOB))
        .andExpect(jsonPath("$.accountOptOut").value(false));
  }

  @Test
  void getPreference_unauthenticated_unauthorized() throws Exception {
    mockMvc.perform(get("/simplification-preferences/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void toggleOptOut_persistsAccountLevelOverride() throws Exception {
    UserOptOutRequest request = new UserOptOutRequest();
    request.setOptOut(true);

    mockMvc
        .perform(
            post("/simplification-preferences/me/opt-out")
                .header("Authorization", tokenFor(BOB))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountOptOut").value(true));

    assertThat(preferenceRepository.findByUserId(BOB).orElseThrow().isAccountOptOut()).isTrue();
  }

  @Test
  void toggleOptOut_optBackIn_clearsOverride() throws Exception {
    UserOptOutRequest optOut = new UserOptOutRequest();
    optOut.setOptOut(true);
    mockMvc
        .perform(
            post("/simplification-preferences/me/opt-out")
                .header("Authorization", tokenFor(BOB))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(optOut)))
        .andExpect(status().isOk());

    UserOptOutRequest optIn = new UserOptOutRequest();
    optIn.setOptOut(false);
    mockMvc
        .perform(
            post("/simplification-preferences/me/opt-out")
                .header("Authorization", tokenFor(BOB))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(optIn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountOptOut").value(false));

    assertThat(preferenceRepository.findByUserId(BOB).orElseThrow().isAccountOptOut()).isFalse();
  }

  @Test
  void toggleOptOut_doesNotTouchAnotherUsersPreference() throws Exception {
    preferenceRepository.save(
        com.splitz.expense.model.UserSimplificationPreference.builder()
            .userId(ALICE)
            .accountOptOut(true)
            .build());

    UserOptOutRequest request = new UserOptOutRequest();
    request.setOptOut(true);
    mockMvc
        .perform(
            post("/simplification-preferences/me/opt-out")
                .header("Authorization", tokenFor(BOB))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    // Alice's preference is untouched: an account opt-out is scoped to the caller.
    assertThat(preferenceRepository.findByUserId(ALICE).orElseThrow().isAccountOptOut()).isTrue();
    assertThat(preferenceRepository.findByUserId(BOB).orElseThrow().isAccountOptOut()).isTrue();
  }

  @Test
  void accountOptOut_excludesMemberFromGroupPlan() throws Exception {
    Group g = createGroupWithMembers(ALICE, BOB);

    preferenceRepository.save(
        com.splitz.expense.model.UserSimplificationPreference.builder()
            .userId(BOB)
            .accountOptOut(true)
            .build());

    // Bob owes Alice; with netting on the plan would net the pair, but Bob's override removes him.
    when(userClient.getUsersByIds(anyList()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(ALICE).username("alice").build(),
                UserResponse.builder().id(BOB).username("bob").build()));

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds[0]").value(BOB))
        .andExpect(jsonPath("$.transactions").isEmpty());
  }

  @Test
  void accountOptOut_doesNotAffectNonMembers() throws Exception {
    Group g = createGroupWithMembers(ALICE, null);

    // A user who is not in this group opts out account-wide.
    preferenceRepository.save(
        com.splitz.expense.model.UserSimplificationPreference.builder()
            .userId(999L)
            .accountOptOut(true)
            .build());

    when(userClient.getUsersByIds(anyList()))
        .thenReturn(List.of(UserResponse.builder().id(ALICE).username("alice").build()));

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds").isEmpty());
  }

  @Test
  void noAccountOptOut_preservesGroupBehaviour() throws Exception {
    Group g = createGroupWithMembers(ALICE, BOB);

    when(userClient.getUsersByIds(anyList()))
        .thenReturn(
            List.of(
                UserResponse.builder().id(ALICE).username("alice").build(),
                UserResponse.builder().id(BOB).username("bob").build()));

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-plan")
                .header("Authorization", tokenFor(ALICE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optedOutUserIds").isEmpty())
        .andExpect(jsonPath("$.simplificationEnabled").value(true));
  }
}
