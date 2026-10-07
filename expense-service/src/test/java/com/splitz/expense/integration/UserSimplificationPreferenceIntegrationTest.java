package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.UserOptOutRequest;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.UserSimplificationPreferenceRepository;
import com.splitz.security.JwtUtil;
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

  @MockBean private UserClient userClient;

  private static final Long ALICE = 100L;
  private static final Long BOB = 200L;

  private String tokenFor(long userId) {
    var user =
        User.withUsername(String.valueOf(userId)).password("").authorities(List.of()).build();
    return "Bearer " + jwtUtil.generateToken(user);
  }

  @BeforeEach
  void before() {
    preferenceRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
    when(userClient.existsById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
  }

  @AfterEach
  void after() {
    preferenceRepository.deleteAll();
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
