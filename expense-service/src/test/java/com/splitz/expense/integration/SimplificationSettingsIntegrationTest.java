package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.UpdateSimplificationSettingsRequest;
import com.splitz.expense.dto.UserOptOutRequest;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.GroupSimplificationSettingsRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SimplificationSettingsIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMemberRepository groupMemberRepository;
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
    groupMemberRepository.deleteAll();
    groupRepository.deleteAll();
    when(userClient.existsById(anyLong())).thenReturn(true);
  }

  @AfterEach
  void after() {
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

  @Test
  void getSettings_returnsDefaults_whenNoSettingsExist() throws Exception {
    Group g = createGroupWithMembers(100L, 200L);

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-settings")
                .header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groupId").value(g.getId()))
        .andExpect(jsonPath("$.simplificationEnabled").value(true))
        .andExpect(jsonPath("$.optOutUserIds").isArray())
        .andExpect(jsonPath("$.optOutUserIds").isEmpty());
  }

  @Test
  void getSettings_nonMember_forbidden() throws Exception {
    Group g = createGroupWithMembers(100L, null);

    mockMvc
        .perform(
            get("/groups/" + g.getId() + "/simplification-settings")
                .header("Authorization", tokenFor(999L)))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateSettings_adminCanDisable() throws Exception {
    Group g = createGroupWithMembers(100L, 200L);

    UpdateSimplificationSettingsRequest request = new UpdateSimplificationSettingsRequest();
    request.setSimplificationEnabled(false);

    mockMvc
        .perform(
            put("/groups/" + g.getId() + "/simplification-settings")
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.simplificationEnabled").value(false));

    GroupSimplificationSettings persisted =
        settingsRepository.findByGroupId(g.getId()).orElseThrow();
    assertThat(persisted.isSimplificationEnabled()).isFalse();
  }

  @Test
  void updateSettings_memberForbidden() throws Exception {
    Group g = createGroupWithMembers(100L, 200L);

    UpdateSimplificationSettingsRequest request = new UpdateSimplificationSettingsRequest();
    request.setSimplificationEnabled(false);

    mockMvc
        .perform(
            put("/groups/" + g.getId() + "/simplification-settings")
                .header("Authorization", tokenFor(200L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void optOut_memberCanOptOut() throws Exception {
    Group g = createGroupWithMembers(100L, 200L);

    UserOptOutRequest request = new UserOptOutRequest();
    request.setOptOut(true);

    mockMvc
        .perform(
            post("/groups/" + g.getId() + "/simplification-settings/opt-out")
                .header("Authorization", tokenFor(200L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optOutUserIds[0]").value(200));
  }

  @Test
  void optOut_memberCanOptBackIn() throws Exception {
    Group g = createGroupWithMembers(100L, 200L);

    // First opt out
    UserOptOutRequest optOut = new UserOptOutRequest();
    optOut.setOptOut(true);
    mockMvc
        .perform(
            post("/groups/" + g.getId() + "/simplification-settings/opt-out")
                .header("Authorization", tokenFor(200L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(optOut)))
        .andExpect(status().isOk());

    // Then opt back in
    UserOptOutRequest optIn = new UserOptOutRequest();
    optIn.setOptOut(false);
    mockMvc
        .perform(
            post("/groups/" + g.getId() + "/simplification-settings/opt-out")
                .header("Authorization", tokenFor(200L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(optIn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.optOutUserIds").isEmpty());
  }

  @Test
  void optOut_nonMember_forbidden() throws Exception {
    Group g = createGroupWithMembers(100L, null);

    UserOptOutRequest request = new UserOptOutRequest();
    request.setOptOut(true);

    mockMvc
        .perform(
            post("/groups/" + g.getId() + "/simplification-settings/opt-out")
                .header("Authorization", tokenFor(999L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }
}
