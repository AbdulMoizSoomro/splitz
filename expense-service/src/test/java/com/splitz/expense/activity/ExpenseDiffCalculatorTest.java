package com.splitz.expense.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.splitz.expense.activity.ExpenseChange.SplitChange;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the diff format produced by {@link ExpenseDiffCalculator}. The format is
 * pinned here as the team's activity-log contract; the frontend parses the trailing ";" delimiters.
 */
class ExpenseDiffCalculatorTest {

  private final ExpenseDiffCalculator calculator = new ExpenseDiffCalculator();

  private Category food;
  private Category travel;
  private Expense dinner60;

  @BeforeEach
  void setUp() {
    food = Category.builder().id(1L).name("Food").build();
    travel = Category.builder().id(2L).name("Travel").build();
    dinner60 =
        Expense.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .currency("EUR")
            .paidBy(100L)
            .category(food)
            .expenseDate(LocalDate.of(2026, 1, 10))
            .notes("old notes")
            .receiptUrl("old-receipt")
            .build();
  }

  private ExpenseChange.Builder change() {
    return ExpenseChange.builder();
  }

  @Test
  void noChanges_ReturnsEmptyDetails() {
    assertEquals("", calculator.calculate(dinner60, change().build()));
  }

  @Test
  void descriptionChange_RendersArrowAndTrailingSemicolon() {
    assertEquals(
        "description: Dinner -> Lunch;",
        calculator.calculate(dinner60, change().description("Lunch").build()));
  }

  @Test
  void amountChange_RendersTwoDecimalFormat() {
    assertEquals(
        "amount: 60.00 -> 100.00;",
        calculator.calculate(dinner60, change().amount(new BigDecimal("100.00")).build()));
  }

  @Test
  void multipleChanges_JoinedWithSameFragmentOrder() {
    assertEquals(
        "description: Dinner -> Lunch; amount: 60.00 -> 100.00;",
        calculator.calculate(
            dinner60, change().description("Lunch").amount(new BigDecimal("100.00")).build()));
  }

  @Test
  void currencyChange_RendersCurrencyChanged() {
    assertEquals(
        "currency: EUR -> USD;", calculator.calculate(dinner60, change().currency("USD").build()));
  }

  @Test
  void paidByChange_RendersFixedPhrase() {
    assertEquals("paidBy changed;", calculator.calculate(dinner60, change().paidBy(200L).build()));
  }

  @Test
  void categoryChange_WhenOldCategoryNull_RendersNone() {
    Expense noCategory =
        Expense.builder()
            .description("Dinner")
            .amount(new BigDecimal("60.00"))
            .category(null)
            .build();
    assertEquals(
        "category: None -> Food;",
        calculator.calculate(noCategory, change().categoryId(1L).categoryName("Food").build()));
  }

  @Test
  void categoryChange_WhenIdDiffers_RendersOldToNewNames() {
    assertEquals(
        "category: Food -> Travel;",
        calculator.calculate(dinner60, change().categoryId(2L).categoryName("Travel").build()));
  }

  @Test
  void categoryProvided_ButSameId_RendersNothing() {
    assertEquals(
        "", calculator.calculate(dinner60, change().categoryId(1L).categoryName("Food").build()));
  }

  @Test
  void expenseDateChange_RendersDateChanged() {
    assertEquals(
        "date changed;",
        calculator.calculate(dinner60, change().expenseDate(LocalDate.of(2026, 2, 1)).build()));
  }

  @Test
  void notesChange_RendersNotesUpdated() {
    assertEquals("notes updated;", calculator.calculate(dinner60, change().notes("new").build()));
  }

  @Test
  void receiptUrlChange_RendersReceiptUpdated() {
    assertEquals(
        "receipt updated;",
        calculator.calculate(dinner60, change().receiptUrl("new-receipt").build()));
  }

  @Test
  void splitModified_RendersSplitsModified() {
    assertEquals(
        "splits: modified;",
        calculator.calculate(dinner60, change().splitChange(SplitChange.MODIFIED).build()));
  }

  @Test
  void splitRecalculated_RendersSplitsRecalculated() {
    assertEquals(
        "splits: recalculated;",
        calculator.calculate(dinner60, change().splitChange(SplitChange.RECALCULATED).build()));
  }

  @Test
  void providedValueEqualToOld_IsNotTreatedAsChange() {
    assertEquals(
        "",
        calculator.calculate(
            dinner60, change().description("Dinner").amount(new BigDecimal("60.00")).build()));
  }

  @Test
  void notProvidedFields_AreIgnored() {
    assertEquals(
        "",
        calculator.calculate(
            dinner60,
            change()
                .description(null)
                .amount(null)
                .currency(null)
                .paidBy(null)
                .expenseDate(null)
                .notes(null)
                .receiptUrl(null)
                .build()));
  }

  @Test
  void fullUpdate_RendersEveryFragmentInCanonicalOrder() {
    assertEquals(
        "description: Dinner -> Lunch; amount: 60.00 -> 100.00; currency: EUR -> USD; "
            + "paidBy changed; category: Food -> Travel; date changed; notes updated; "
            + "receipt updated; splits: recalculated;",
        calculator.calculate(
            dinner60,
            change()
                .description("Lunch")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paidBy(200L)
                .categoryId(2L)
                .categoryName("Travel")
                .expenseDate(LocalDate.of(2026, 2, 1))
                .notes("new")
                .receiptUrl("new-receipt")
                .splitChange(SplitChange.RECALCULATED)
                .build()));
  }
}
