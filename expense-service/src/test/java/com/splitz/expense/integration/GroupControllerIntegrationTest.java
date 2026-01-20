package com.splitz.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.security.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GroupControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JwtUtil jwtUtil;

  @Autowired private GroupRepository groupRepository;

  @Autowired private GroupMemberRepository groupMemberRepository;

  @MockBean private UserClient userClient;

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
  void createGetUpdateAddRemoveDelete_flow() throws Exception {
    // Create group as user 100
    CreateGroupRequest create = new CreateGroupRequest();
    create.setName("Roommates");
    create.setDescription("Monthly");

    var createResult =
        mockMvc
            .perform(
                post("/groups")
                    .header("Authorization", tokenFor(100L))
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Roommates"))
            .andReturn();

    String resp = createResult.getResponse().getContentAsString();
    var node = objectMapper.readTree(resp);
    long groupId = node.get("id").asLong();

    // Verify persisted group and membership
    Group persisted = groupRepository.findById(groupId).orElseThrow();
    assertThat(persisted.getCreatedBy()).isEqualTo(100L);
    assertThat(persisted.getMembers()).hasSize(1);

    // Get group as member
    mockMvc
        .perform(get("/groups/" + groupId).header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(groupId));

    // Update group name
    UpdateGroupRequest update = new UpdateGroupRequest();
    update.setName("UpdatedRoom");

    mockMvc
        .perform(
            put("/groups/" + groupId)
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("UpdatedRoom"));

    // Add member 200
    AddMemberRequest add = new AddMemberRequest();
    add.setUserId(200L);
    add.setRole(GroupRole.MEMBER);

    mockMvc
        .perform(
            post("/groups/" + groupId + "/members")
                .header("Authorization", tokenFor(100L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(add)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.members").isArray());

    assertThat(groupMemberRepository.existsByGroupIdAndUserId(groupId, 200L)).isTrue();

    // Remove member 200
    mockMvc
        .perform(
            delete("/groups/" + groupId + "/members/200").header("Authorization", tokenFor(100L)))
        .andExpect(status().isNoContent());

    assertThat(groupMemberRepository.findByGroupIdAndUserId(groupId, 200L)).isEmpty();

    // Delete group
    mockMvc
        .perform(delete("/groups/" + groupId).header("Authorization", tokenFor(100L)))
        .andExpect(status().isNoContent());

    // After delete, group should be inactive and not returned by user groups
    var groups = groupRepository.findDistinctByMembersUserIdAndActiveTrue(100L);
    assertThat(groups).isEmpty();
  }

  @Test
  void access_control_non_member_forbidden() throws Exception {
    // Create group as user 300
    Group g = Group.builder().name("Private").description("x").createdBy(300L).active(true).build();
    GroupMember gm = GroupMember.builder().userId(300L).role(GroupRole.ADMIN).build();
    g.addMember(gm);
    Group saved = groupRepository.save(g);

    // User 400 is not a member
    mockMvc
        .perform(get("/groups/" + saved.getId()).header("Authorization", tokenFor(400L)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listGroups_returnsOnlyUsersGroups() throws Exception {
    // Group 1: User 100 is member
    Group g1 = Group.builder().name("G1").createdBy(100L).active(true).build();
    g1.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    groupRepository.save(g1);

    // Group 2: User 100 is NOT member
    Group g2 = Group.builder().name("G2").createdBy(200L).active(true).build();
    g2.addMember(GroupMember.builder().userId(200L).role(GroupRole.ADMIN).build());
    groupRepository.save(g2);

    mockMvc
        .perform(get("/groups").header("Authorization", tokenFor(100L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("G1"));
  }

  @Test
  void updateGroup_memberForbidden() throws Exception {
    Group g = Group.builder().name("AdminOnly").createdBy(100L).active(true).build();
    g.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    g.addMember(GroupMember.builder().userId(200L).role(GroupRole.MEMBER).build());
    Group saved = groupRepository.save(g);

    UpdateGroupRequest update = new UpdateGroupRequest();
    update.setName("HackerName");

    // User 200 (MEMBER) tries to update
    mockMvc
        .perform(
            put("/groups/" + saved.getId())
                .header("Authorization", tokenFor(200L))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteGroup_memberForbidden() throws Exception {
    Group g = Group.builder().name("AdminOnly").createdBy(100L).active(true).build();
    g.addMember(GroupMember.builder().userId(100L).role(GroupRole.ADMIN).build());
    g.addMember(GroupMember.builder().userId(200L).role(GroupRole.MEMBER).build());
    Group saved = groupRepository.save(g);

    // User 200 (MEMBER) tries to delete
    mockMvc
        .perform(delete("/groups/" + saved.getId()).header("Authorization", tokenFor(200L)))
        .andExpect(status().isForbidden());
  }

  @TestConfiguration
  static class TestSecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
      return username -> {
        // Accept any numeric username and create a simple UserDetails
        if (username == null || username.isBlank()) {
          throw new UsernameNotFoundException("username empty");
        }
        return User.withUsername(username).password("").authorities(List.of()).build();
      };
    }
  }
}
