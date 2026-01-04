package com.splitz.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.service.ExpenseService;
import com.splitz.security.JwtRequestFilter;
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

@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ExpenseService expenseService;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  private ExpenseDTO expenseDTO;

  @BeforeEach
  void setUp() {
    expenseDTO =
        ExpenseDTO.builder()
            .id(1L)
            .groupId(1L)
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .build();
  }

  @Test
  void createExpense_Success() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .paidBy(100L)
            .build();

    when(expenseService.createExpense(eq(1L), any(CreateExpenseRequest.class)))
        .thenReturn(expenseDTO);

    mockMvc
        .perform(
            post("/groups/1/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1L))
        .andExpect(jsonPath("$.description").value("Dinner"));
  }

  @Test
  void createExpense_InvalidRequest_ReturnsBadRequest() throws Exception {
    CreateExpenseRequest request =
        CreateExpenseRequest.builder()
            .description("") // Invalid: blank
            .amount(new BigDecimal("-10.00")) // Invalid: negative
            .build();

    mockMvc
        .perform(
            post("/groups/1/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getExpense_Success() throws Exception {
    when(expenseService.getExpense(1L)).thenReturn(expenseDTO);

    mockMvc
        .perform(get("/expenses/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1L));
  }

  @Test
  void getExpense_NotFound_ReturnsNotFound() throws Exception {
    when(expenseService.getExpense(1L))
        .thenThrow(new com.splitz.expense.exception.ResourceNotFoundException("Expense not found"));

    mockMvc.perform(get("/expenses/1")).andExpect(status().isNotFound());
  }

  @Test
  void getExpensesByGroup_Success() throws Exception {
    when(expenseService.getExpensesByGroup(1L)).thenReturn(List.of(expenseDTO));

    mockMvc
        .perform(get("/groups/1/expenses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1L));
  }

  @Test
  @WithMockUser(username = "100")
  void updateExpense_Success() throws Exception {
    UpdateExpenseRequest request =
        UpdateExpenseRequest.builder().description("Updated Dinner").build();

    when(expenseService.updateExpense(eq(1L), any(UpdateExpenseRequest.class), eq(100L)))
        .thenReturn(expenseDTO);

    mockMvc
        .perform(
            put("/expenses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "100")
  void updateExpense_NotFound_ReturnsNotFound() throws Exception {
    UpdateExpenseRequest request = UpdateExpenseRequest.builder().description("Updated").build();
    when(expenseService.updateExpense(eq(1L), any(UpdateExpenseRequest.class), eq(100L)))
        .thenThrow(new com.splitz.expense.exception.ResourceNotFoundException("Expense not found"));

    mockMvc
        .perform(
            put("/expenses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "100")
  void deleteExpense_Success() throws Exception {
    mockMvc.perform(delete("/expenses/1")).andExpect(status().isNoContent());
  }
}
