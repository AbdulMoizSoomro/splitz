package com.splitz.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.activity.ExpenseActivityLogEngine;
import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.GroupMemberDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.governance.GroupGovernance;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.service.GroupService;
import com.splitz.security.JwtRequestFilter;
import com.splitz.security.JwtUtil;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class GroupControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private GroupService groupService;

  @MockBean private ExpenseActivityLogEngine expenseActivityLogEngine;

  @MockBean private GroupGovernance groupGovernance;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @MockBean private JwtUtil jwtUtil;

  @MockBean private SharedSecurityAuthorizer splitzAuthorizer;

  @BeforeEach
  void setUp() throws ServletException, IOException {
    when(splitzAuthorizer.getCurrentUserId())
        .thenAnswer(
            invocation -> {
              String name =
                  org.springframework.security.core.context.SecurityContextHolder.getContext()
                      .getAuthentication()
                      .getName();
              return Long.parseLong(name);
            });
    doAnswer(
            invocation -> {
              ServletRequest request = invocation.getArgument(0);
              ServletResponse response = invocation.getArgument(1);
              FilterChain chain = invocation.getArgument(2);
              chain.doFilter(request, response);
              return null;
            })
        .when(jwtRequestFilter)
        .doFilter(any(), any(), any());
  }

  @Test
  @WithMockUser(username = "1")
  void createGroup_ShouldReturnCreatedGroup() throws Exception {
    CreateGroupRequest request = new CreateGroupRequest();
    request.setName("Roommates");
    request.setDescription("desc");

    GroupDTO response =
        GroupDTO.builder()
            .id(10L)
            .name("Roommates")
            .members(List.of(GroupMemberDTO.builder().userId(1L).role(GroupRole.ADMIN).build()))
            .build();

    when(groupService.createGroup(any(CreateGroupRequest.class), Mockito.eq(1L)))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10L))
        .andExpect(jsonPath("$.members[0].userId").value(1L));
  }

  @Test
  @WithMockUser(username = "2")
  void getGroups_ShouldReturnGroupsForUser() throws Exception {
    GroupDTO group = GroupDTO.builder().id(1L).name("Trip").build();
    when(groupService.getGroupsForUser(2L)).thenReturn(List.of(group));

    mockMvc
        .perform(get("/groups"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Trip"));
  }

  @Test
  @WithMockUser(username = "4")
  void updateGroup_ShouldReturnUpdatedGroup() throws Exception {
    UpdateGroupRequest request = new UpdateGroupRequest();
    request.setName("Updated");

    GroupDTO response = GroupDTO.builder().id(7L).name("Updated").build();
    when(groupService.updateGroup(Mockito.eq(7L), any(UpdateGroupRequest.class), Mockito.eq(4L)))
        .thenReturn(response);

    mockMvc
        .perform(
            put("/groups/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
  }

  @Test
  @WithMockUser(username = "5")
  void deleteGroup_ShouldReturnNoContent() throws Exception {
    mockMvc.perform(delete("/groups/9")).andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(username = "1")
  void getGroupActivity_ShouldReturnActivityLogs() throws Exception {
    ActivityLogDTO dto =
        ActivityLogDTO.builder()
            .id(1L)
            .groupId(1L)
            .type(com.splitz.expense.model.ActivityLogType.EXPENSE_CREATED)
            .entityName("Dinner")
            .build();

    when(expenseActivityLogEngine.getGroupActivity(1L)).thenReturn(List.of(dto));

    mockMvc
        .perform(get("/groups/1/activity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].entityName").value("Dinner"));

    org.mockito.Mockito.verify(groupGovernance).assertIsMember(1L, 1L);
  }

  @Test
  @WithMockUser(username = "7")
  void getGroupActivity_NonMember_IsForbidden() throws Exception {
    org.mockito.Mockito.doThrow(new UnauthorizedException("You are not a member of this group"))
        .when(groupGovernance)
        .assertIsMember(1L, 7L);

    mockMvc.perform(get("/groups/1/activity")).andExpect(status().isForbidden());

    org.mockito.Mockito.verifyNoInteractions(expenseActivityLogEngine);
  }
}
