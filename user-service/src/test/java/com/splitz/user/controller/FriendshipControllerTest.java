package com.splitz.user.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitz.user.config.CryptoConfig;
import com.splitz.user.config.SecurityConfig;
import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.model.FriendshipStatus;
import com.splitz.user.model.User;
import com.splitz.user.security.JwtRequestFilter;
import com.splitz.user.security.JwtUtil;
import com.splitz.user.security.SecurityExpressions;
import com.splitz.user.service.FriendshipService;
import com.splitz.user.service.UserService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(FriendshipController.class)
@Import({ SecurityConfig.class, CryptoConfig.class, SecurityExpressions.class })
public class FriendshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FriendshipService friendshipService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtRequestFilter jwtRequestFilter;

    @BeforeEach
    void setUp() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("user1");
        when(userService.loadUserByUsername(anyString())).thenReturn(user);

        // Let the mocked JWT filter pass the request through the chain
        org.mockito.Mockito.doAnswer(invocation -> {
            try {
                HttpServletRequest req = invocation.getArgument(0);
                HttpServletResponse res = invocation.getArgument(1);
                FilterChain chain = invocation.getArgument(2);
                chain.doFilter(req, res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(jwtRequestFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class),
                any(FilterChain.class));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void sendFriendRequest_ShouldReturnCreated() throws Exception {
        FriendshipDTO friendshipDTO = new FriendshipDTO();
        friendshipDTO.setId(1L);
        friendshipDTO.setStatus(FriendshipStatus.PENDING);

        when(friendshipService.sendFriendRequest(eq(1L), eq(2L))).thenReturn(friendshipDTO);

        mockMvc.perform(post("/users/1/friends")
                .param("friendId", "2")
                .with(csrf()))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void getFriends_ShouldReturnList() throws Exception {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(2L);
        userDTO.setUsername("friend");

        when(friendshipService.getAcceptedFriends(any())).thenReturn(List.of(userDTO));

        mockMvc.perform(get("/users/1/friends")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].username").value("friend"));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void getPendingRequests_ShouldReturnList() throws Exception {
        FriendshipDTO friendshipDTO = new FriendshipDTO();
        friendshipDTO.setId(1L);
        friendshipDTO.setStatus(FriendshipStatus.PENDING);

        when(friendshipService.getPendingRequests(any())).thenReturn(List.of(friendshipDTO));

        mockMvc.perform(get("/users/1/friends/requests")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void acceptFriendRequest_ShouldReturnAccepted() throws Exception {
        FriendshipDTO friendshipDTO = new FriendshipDTO();
        friendshipDTO.setId(1L);
        friendshipDTO.setStatus(FriendshipStatus.ACCEPTED);

        when(friendshipService.acceptFriendRequest(any(), any())).thenReturn(friendshipDTO);

        mockMvc.perform(put("/users/1/friends/100/accept")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void rejectFriendRequest_ShouldReturnRejected() throws Exception {
        FriendshipDTO friendshipDTO = new FriendshipDTO();
        friendshipDTO.setId(1L);
        friendshipDTO.setStatus(FriendshipStatus.REJECTED);

        when(friendshipService.rejectFriendRequest(any(), any())).thenReturn(friendshipDTO);

        mockMvc.perform(put("/users/1/friends/100/reject")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void removeFriend_ShouldReturnNoContent() throws Exception {
        doNothing().when(friendshipService).removeFriend(eq(1L), eq(2L));

        mockMvc.perform(delete("/users/1/friends/2")
                .with(csrf()))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isNoContent());
    }
}
