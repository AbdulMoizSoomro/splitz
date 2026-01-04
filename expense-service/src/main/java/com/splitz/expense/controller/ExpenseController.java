package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.service.ExpenseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExpenseController {

  private final ExpenseService expenseService;

  @PostMapping("/groups/{groupId}/expenses")
  public ResponseEntity<ExpenseDTO> createExpense(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody CreateExpenseRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(expenseService.createExpense(groupId, request));
  }

  @GetMapping("/expenses/{id}")
  public ResponseEntity<ExpenseDTO> getExpense(@PathVariable("id") Long id) {
    return ResponseEntity.ok(expenseService.getExpense(id));
  }

  @GetMapping("/groups/{groupId}/expenses")
  public ResponseEntity<List<ExpenseDTO>> getExpensesByGroup(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(expenseService.getExpensesByGroup(groupId));
  }

  @PutMapping("/expenses/{id}")
  public ResponseEntity<ExpenseDTO> updateExpense(
      @PathVariable("id") Long id, @Valid @RequestBody UpdateExpenseRequest request) {
    return ResponseEntity.ok(expenseService.updateExpense(id, request, currentUserId()));
  }

  @DeleteMapping("/expenses/{id}")
  public ResponseEntity<Void> deleteExpense(@PathVariable("id") Long id) {
    expenseService.deleteExpense(id, currentUserId());
    return ResponseEntity.noContent().build();
  }

  private Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getName() == null) {
      throw new AccessDeniedException("No authenticated user found");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException ex) {
      throw new AccessDeniedException("Authenticated username must be a numeric user id");
    }
  }
}
