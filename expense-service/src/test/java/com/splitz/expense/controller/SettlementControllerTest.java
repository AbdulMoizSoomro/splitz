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
import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.service.SettlementService;
import com.splitz.security.JwtRequestFilter;
import java.math.BigDecimal;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementService settlementService;

    @MockBean
    private JwtRequestFilter jwtRequestFilter;

    private SettlementDTO settlementDTO;

    @BeforeEach
    void setUp() {
        settlementDTO
                = SettlementDTO.builder()
                        .id(1L)
                        .groupId(1L)
                        .payerId(101L)
                        .payeeId(102L)
                        .amount(new BigDecimal("50.00"))
                        .status(SettlementStatus.PENDING)
                        .build();
    }

    @Test
    @WithMockUser(username = "101")
    void createSettlement_Success() throws Exception {
        CreateSettlementRequest request
                = CreateSettlementRequest.builder()
                        .groupId(1L)
                        .payerId(101L)
                        .payeeId(102L)
                        .amount(new BigDecimal("50.00"))
                        .build();

        when(settlementService.createSettlement(any(CreateSettlementRequest.class)))
                .thenReturn(settlementDTO);

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
        when(settlementService.getSettlementById(1L)).thenReturn(settlementDTO);

        mockMvc
                .perform(get("/settlements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "101")
    void markAsPaid_Success() throws Exception {
        settlementDTO.setStatus(SettlementStatus.MARKED_PAID);
        when(settlementService.markAsPaid(eq(1L), eq(101L))).thenReturn(settlementDTO);

        mockMvc
                .perform(put("/settlements/1/mark-paid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MARKED_PAID"));
    }

    @Test
    @WithMockUser(username = "102")
    void confirmSettlement_Success() throws Exception {
        settlementDTO.setStatus(SettlementStatus.COMPLETED);
        when(settlementService.confirmSettlement(eq(1L), eq(102L))).thenReturn(settlementDTO);

        mockMvc
                .perform(put("/settlements/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
