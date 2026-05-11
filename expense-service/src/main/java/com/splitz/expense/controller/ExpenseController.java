package com.splitz.expense.controller;

import com.splitz.expense.dto.CreateExpenseRequest;
import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.dto.UpdateExpenseRequest;
import com.splitz.expense.service.ExpenseService;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExpenseController {

  private final ExpenseService expenseService;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @PostMapping("/groups/{groupId}/expenses")
  public ResponseEntity<ExpenseDTO> createExpense(
      @PathVariable("groupId") Long groupId, @Valid @RequestBody CreateExpenseRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(expenseService.createExpense(groupId, request, splitzAuthorizer.getCurrentUserId()));
  }

  @PutMapping("/groups/{groupId}/expenses/{expenseId}")
  public ResponseEntity<ExpenseDTO> updateExpense(
      @PathVariable("groupId") Long groupId,
      @PathVariable("expenseId") Long expenseId,
      @Valid @RequestBody UpdateExpenseRequest request) {
    return ResponseEntity.ok(
        expenseService.updateExpense(expenseId, request, splitzAuthorizer.getCurrentUserId()));
  }

  @DeleteMapping("/groups/{groupId}/expenses/{expenseId}")
  public ResponseEntity<Void> deleteExpense(
      @PathVariable("groupId") Long groupId, @PathVariable("expenseId") Long expenseId) {
    expenseService.deleteExpense(expenseId, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/expenses/{id}")
  public ResponseEntity<ExpenseDTO> getExpense(@PathVariable("id") Long id) {
    return ResponseEntity.ok(expenseService.getExpense(id, splitzAuthorizer.getCurrentUserId()));
  }

  @GetMapping("/groups/{groupId}/expenses")
  public ResponseEntity<List<ExpenseDTO>> getExpensesByGroup(
      @PathVariable("groupId") Long groupId) {
    return ResponseEntity.ok(
        expenseService.getExpensesByGroup(groupId, splitzAuthorizer.getCurrentUserId()));
  }

  @GetMapping("/groups/expenses/bulk")
  public ResponseEntity<List<ExpenseDTO>> getExpensesByGroupIds(
      @RequestParam("groupIds") List<Long> groupIds) {
    return ResponseEntity.ok(
        expenseService.getExpensesByGroupIds(groupIds, splitzAuthorizer.getCurrentUserId()));
  }

  @PutMapping("/expenses/{id}")
  public ResponseEntity<ExpenseDTO> updateExpense(
      @PathVariable("id") Long id, @Valid @RequestBody UpdateExpenseRequest request) {
    return ResponseEntity.ok(
        expenseService.updateExpense(id, request, splitzAuthorizer.getCurrentUserId()));
  }

  @DeleteMapping("/expenses/{id}")
  public ResponseEntity<Void> deleteExpense(@PathVariable("id") Long id) {
    expenseService.deleteExpense(id, splitzAuthorizer.getCurrentUserId());
    return ResponseEntity.noContent().build();
  }
}
