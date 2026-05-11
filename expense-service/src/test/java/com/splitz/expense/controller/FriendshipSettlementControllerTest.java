package com.splitz.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.service.FriendshipSettlementService;
import com.splitz.security.JwtRequestFilter;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FriendshipSettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class FriendshipSettlementControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private FriendshipSettlementService friendshipSettlementService;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @MockBean private SharedSecurityAuthorizer splitzAuthorizer;

  private FriendshipSettlementDTO settlementDTO;

  @BeforeEach
  void setUp() {
    settlementDTO =
        FriendshipSettlementDTO.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();
  }

  @Test
  @WithMockUser(username = "101")
  void createSettlement_Success() throws Exception {
    CreateFriendshipSettlementRequest request =
        CreateFriendshipSettlementRequest.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .build();

    when(friendshipSettlementService.createSettlements(
            any(CreateFriendshipSettlementRequest.class)))
        .thenReturn(List.of(settlementDTO));

    mockMvc
        .perform(
            post("/friendship-settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }

  @Test
  @WithMockUser(username = "101")
  void getSettlement_Success() throws Exception {
    when(friendshipSettlementService.getSettlementById(1L)).thenReturn(settlementDTO);

    mockMvc
        .perform(get("/friendship-settlements/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  @WithMockUser(username = "101")
  void getSettlementsBetweenUsers_Success() throws Exception {
    when(friendshipSettlementService.getSettlementsBetweenUsers(101L, 102L))
        .thenReturn(List.of(settlementDTO));

    mockMvc
        .perform(get("/users/101/friendships/102/settlements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  @WithMockUser(username = "101")
  void markAsPaid_Success() throws Exception {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(101L);
    settlementDTO.setStatus(SettlementStatus.MARKED_PAID);
    when(friendshipSettlementService.markAsPaid(eq(1L))).thenReturn(settlementDTO);

    mockMvc
        .perform(put("/friendship-settlements/1/mark-paid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MARKED_PAID"));
  }

  @Test
  @WithMockUser(username = "102")
  void confirmSettlement_Success() throws Exception {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(102L);
    settlementDTO.setStatus(SettlementStatus.COMPLETED);
    when(friendshipSettlementService.confirmSettlement(eq(1L))).thenReturn(settlementDTO);

    mockMvc
        .perform(put("/friendship-settlements/1/confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }
}
