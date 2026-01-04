package com.splitz.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitz.expense.dto.BalanceDTO;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.security.SecurityExpressions;
import com.splitz.expense.service.BalanceService;
import com.splitz.security.JwtRequestFilter;
import com.splitz.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class BalanceControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private BalanceService balanceService;

  @MockBean(name = "security")
  private SecurityExpressions securityExpressions;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @MockBean private JwtUtil jwtUtil;

  @BeforeEach
  void setUp() throws ServletException, IOException {
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
  @WithMockUser(username = "101")
  void getGroupBalances_ShouldReturnBalances() throws Exception {
    GroupBalanceResponseDTO response =
        GroupBalanceResponseDTO.builder()
            .groupId(1L)
            .balances(
                Arrays.asList(
                    new BalanceDTO(101L, new BigDecimal("40.00")),
                    new BalanceDTO(102L, new BigDecimal("-20.00")),
                    new BalanceDTO(103L, new BigDecimal("-20.00"))))
            .simplifiedDebts(
                Arrays.asList(
                    new DebtDTO(102L, 101L, new BigDecimal("20.00")),
                    new DebtDTO(103L, 101L, new BigDecimal("20.00"))))
            .build();

    when(balanceService.getGroupBalances(1L)).thenReturn(response);

    mockMvc
        .perform(get("/groups/1/balances"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groupId").value(1L))
        .andExpect(jsonPath("$.balances[0].userId").value(101L))
        .andExpect(jsonPath("$.balances[0].balance").value(40.00))
        .andExpect(jsonPath("$.simplifiedDebts[0].from").value(102L))
        .andExpect(jsonPath("$.simplifiedDebts[0].amount").value(20.00));
  }

  @Test
  @WithMockUser(username = "101")
  void getUserBalances_ShouldReturnUserBalances() throws Exception {
    UserBalanceResponseDTO response =
        UserBalanceResponseDTO.builder()
            .userId(101L)
            .totalBalance(new BigDecimal("15.00"))
            .groupBalances(
                Arrays.asList(
                    new UserBalanceResponseDTO.GroupBalanceDTO(
                        1L, "Group 1", new BigDecimal("25.00")),
                    new UserBalanceResponseDTO.GroupBalanceDTO(
                        2L, "Group 2", new BigDecimal("-10.00"))))
            .build();

    when(balanceService.getUserBalances(101L)).thenReturn(response);

    mockMvc
        .perform(get("/users/101/balances"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(101L))
        .andExpect(jsonPath("$.totalBalance").value(15.00))
        .andExpect(jsonPath("$.groupBalances[0].groupId").value(1L))
        .andExpect(jsonPath("$.groupBalances[0].balance").value(25.00));
  }
}
