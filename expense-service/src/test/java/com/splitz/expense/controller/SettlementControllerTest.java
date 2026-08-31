package com.splitz.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.mapper.PaymentMapper;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.service.PaymentService;
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

@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private PaymentService paymentService;

  @MockBean private PaymentMapper paymentMapper;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @MockBean private SharedSecurityAuthorizer splitzAuthorizer;

  private SettlementDTO settlementDTO;
  private Payment payment;

  @BeforeEach
  void setUp() {
    settlementDTO =
        SettlementDTO.builder()
            .id(1L)
            .groupId(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    payment =
        Payment.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(splitzAuthorizer.getCurrentUserId()).thenReturn(101L);
  }

  @Test
  @WithMockUser(username = "101")
  void createSettlement_Success() throws Exception {
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .groupId(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .build();

    when(paymentService.createPayment(any(), any(), any(), any(), any())).thenReturn(payment);
    when(paymentMapper.toSettlementDTO(any())).thenReturn(settlementDTO);

    mockMvc
        .perform(
            post("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @WithMockUser(username = "101")
  void getSettlement_Success() throws Exception {
    when(paymentService.getPaymentById(eq(1L))).thenReturn(payment);
    when(paymentMapper.toSettlementDTO(any())).thenReturn(settlementDTO);

    mockMvc
        .perform(get("/settlements/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));
  }

  @Test
  @WithMockUser(username = "101")
  void markAsPaid_Success() throws Exception {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(101L);
    settlementDTO.setStatus(SettlementStatus.MARKED_PAID);
    payment.setStatus(SettlementStatus.MARKED_PAID);
    when(paymentService.markAsPaid(eq(1L))).thenReturn(payment);
    when(paymentMapper.toSettlementDTO(any())).thenReturn(settlementDTO);

    mockMvc
        .perform(put("/settlements/1/mark-paid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MARKED_PAID"));
  }

  @Test
  @WithMockUser(username = "102")
  void confirmSettlement_Success() throws Exception {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(102L);
    settlementDTO.setStatus(SettlementStatus.COMPLETED);
    payment.setStatus(SettlementStatus.COMPLETED);
    when(paymentService.confirmPayment(eq(1L))).thenReturn(payment);
    when(paymentMapper.toSettlementDTO(any())).thenReturn(settlementDTO);

    mockMvc
        .perform(put("/settlements/1/confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  @WithMockUser(username = "101")
  void createSettlement_WithAllocations_Success() throws Exception {
    CreateSettlementRequest request =
        CreateSettlementRequest.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .allocations(
                List.of(
                    CreateSettlementRequest.Allocation.builder()
                        .groupId(1L)
                        .amount(new BigDecimal("30.00"))
                        .build(),
                    CreateSettlementRequest.Allocation.builder()
                        .groupId(2L)
                        .amount(new BigDecimal("20.00"))
                        .build()))
            .build();

    when(paymentService.createPayment(any(), any(), any(), any(), any())).thenReturn(payment);
    when(paymentMapper.toSettlementDTO(any())).thenReturn(settlementDTO);

    mockMvc
        .perform(
            post("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @WithMockUser(username = "101")
  void getSettlementsBetweenUsers_Success() throws Exception {
    when(paymentService.getPaymentsBetweenUsers(101L, 102L)).thenReturn(java.util.List.of(payment));
    when(paymentMapper.toSettlementDTOs(any())).thenReturn(java.util.List.of(settlementDTO));

    mockMvc
        .perform(get("/users/101/friendships/102/settlements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  @WithMockUser(username = "101")
  void getSettlementsByGroup_Success() throws Exception {
    when(paymentService.getPaymentsByGroup(1L)).thenReturn(java.util.List.of(payment));
    when(paymentMapper.toSettlementDTOs(any())).thenReturn(java.util.List.of(settlementDTO));

    mockMvc
        .perform(get("/groups/1/settlements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1));
  }

  @Test
  @WithMockUser(username = "101")
  void updateSettlement_Success() throws Exception {
    com.splitz.expense.dto.UpdateSettlementRequest request =
        com.splitz.expense.dto.UpdateSettlementRequest.builder()
            .amount(new BigDecimal("75.00"))
            .build();

    payment.setAmount(new BigDecimal("75.00"));
    settlementDTO.setAmount(new BigDecimal("75.00"));
    when(paymentService.updatePayment(eq(1L), any(), any())).thenReturn(payment);
    when(paymentMapper.toSettlementDTO(any())).thenReturn(settlementDTO);

    mockMvc
        .perform(
            put("/settlements/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(75.00));
  }

  @Test
  @WithMockUser(username = "999")
  void markAsPaid_ByNonPayer_ReturnsForbiddenFromServiceGuard() throws Exception {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(999L);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(paymentService.markAsPaid(1L))
        .thenThrow(new com.splitz.expense.exception.UnauthorizedException("not authorized"));

    mockMvc.perform(put("/settlements/1/mark-paid")).andExpect(status().isForbidden());

    verify(paymentService).markAsPaid(1L);
  }

  @Test
  @WithMockUser(username = "999")
  void confirmSettlement_ByNonPayee_ReturnsForbiddenFromServiceGuard() throws Exception {
    when(splitzAuthorizer.getCurrentUserId()).thenReturn(999L);
    when(splitzAuthorizer.isAdmin()).thenReturn(false);
    when(paymentService.confirmPayment(1L))
        .thenThrow(new com.splitz.expense.exception.UnauthorizedException("not authorized"));

    mockMvc.perform(put("/settlements/1/confirm")).andExpect(status().isForbidden());

    verify(paymentService).confirmPayment(1L);
  }
}
