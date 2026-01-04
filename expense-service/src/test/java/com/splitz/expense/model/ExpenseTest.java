package com.splitz.expense.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpenseTest {

  @Test
  void testExpenseBuilder() {
    Group group = Group.builder().id(1L).name("Test Group").build();
    Category category = Category.builder().id(1L).name("Food").build();
    LocalDate now = LocalDate.now();

    Expense expense =
        Expense.builder()
            .id(1L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .currency("EUR")
            .paidBy(100L)
            .category(category)
            .expenseDate(now)
            .notes("Pizza night")
            .receiptUrl("http://example.com/receipt.jpg")
            .build();

    assertNotNull(expense);
    assertEquals(1L, expense.getId());
    assertEquals(group, expense.getGroup());
    assertEquals("Dinner", expense.getDescription());
    assertEquals(new BigDecimal("60.00"), expense.getAmount());
    assertEquals("EUR", expense.getCurrency());
    assertEquals(100L, expense.getPaidBy());
    assertEquals(category, expense.getCategory());
    assertEquals(now, expense.getExpenseDate());
    assertEquals("Pizza night", expense.getNotes());
    assertEquals("http://example.com/receipt.jpg", expense.getReceiptUrl());
  }
}
