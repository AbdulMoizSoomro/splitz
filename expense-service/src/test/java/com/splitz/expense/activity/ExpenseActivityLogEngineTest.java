package com.splitz.expense.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.ActivityLogDTO;
import com.splitz.expense.mapper.ActivityLogMapper;
import com.splitz.expense.model.ActivityLog;
import com.splitz.expense.model.ActivityLogType;
import com.splitz.expense.model.Category;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.Group;
import com.splitz.expense.repository.ActivityLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Contract tests for the diff format produced by {@link DefaultExpenseActivityLogEngine}. The
 * format is pinned here as the team's activity-log contract; the frontend parses the trailing ";"
 * delimiters.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseActivityLogEngineTest {

  @Mock private ActivityLogRepository activityLogRepository;
  @Mock private ActivityLogMapper activityLogMapper;

  private DefaultExpenseActivityLogEngine engine;

  private Group group;
  private Expense expense;

  @BeforeEach
  void setUp() {
    engine = new DefaultExpenseActivityLogEngine(activityLogRepository, activityLogMapper);

    group = Group.builder().id(10L).name("Test Group").build();
    expense =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paidBy(1L)
            .build();
  }

  @Test
  void recordCreated_SavesExpenseCreatedActivityLog() {
    engine.recordCreated(expense, 1L);

    ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
    verify(activityLogRepository).save(captor.capture());

    ActivityLog saved = captor.getValue();
    assertThat(saved.getGroupId()).isEqualTo(10L);
    assertThat(saved.getType()).isEqualTo(ActivityLogType.EXPENSE_CREATED);
    assertThat(saved.getActorId()).isEqualTo(1L);
    assertThat(saved.getEntityId()).isEqualTo(100L);
    assertThat(saved.getEntityName()).isEqualTo("Dinner");
    assertThat(saved.getDetails()).isNull();
  }

  @Test
  void recordDeleted_SavesExpenseDeletedActivityLog() {
    engine.recordDeleted(expense, 1L);

    ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
    verify(activityLogRepository).save(captor.capture());

    ActivityLog saved = captor.getValue();
    assertThat(saved.getGroupId()).isEqualTo(10L);
    assertThat(saved.getType()).isEqualTo(ActivityLogType.EXPENSE_DELETED);
    assertThat(saved.getActorId()).isEqualTo(1L);
    assertThat(saved.getEntityId()).isEqualTo(100L);
    assertThat(saved.getDetails()).isNull();
  }

  @Test
  void recordUpdated_SavesExpenseUpdatedLogWithActorAndEntity() {
    Expense updated =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Lunch")
            .amount(new BigDecimal("75.00"))
            .currency("USD")
            .paidBy(1L)
            .build();

    engine.recordUpdated(expense, updated, SplitChange.NONE, 2L);

    ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
    verify(activityLogRepository).save(captor.capture());

    ActivityLog saved = captor.getValue();
    assertThat(saved.getGroupId()).isEqualTo(10L);
    assertThat(saved.getType()).isEqualTo(ActivityLogType.EXPENSE_UPDATED);
    assertThat(saved.getActorId()).isEqualTo(2L);
    assertThat(saved.getEntityId()).isEqualTo(100L);
    assertThat(saved.getEntityName()).isEqualTo("Lunch");
  }

  @Test
  void noChanges_ReturnsEmptyDetails() {
    assertThat(renderDetails(expense, expense, SplitChange.NONE)).isEmpty();
  }

  @Test
  void descriptionChange_RendersArrowAndTrailingSemicolon() {
    assertThat(renderDetails(expense, updated(e -> e.description("Lunch")), SplitChange.NONE))
        .isEqualTo("description: Dinner -> Lunch;");
  }

  @Test
  void amountChange_RendersTwoDecimalFormat() {
    assertThat(
            renderDetails(
                expense, updated(e -> e.amount(new BigDecimal("100.00"))), SplitChange.NONE))
        .isEqualTo("amount: 50.00 -> 100.00;");
  }

  @Test
  void multipleChanges_JoinedWithSameFragmentOrder() {
    assertThat(
            renderDetails(
                expense,
                updated(e -> e.description("Lunch").amount(new BigDecimal("100.00"))),
                SplitChange.NONE))
        .isEqualTo("description: Dinner -> Lunch; amount: 50.00 -> 100.00;");
  }

  @Test
  void currencyChange_RendersCurrencyChanged() {
    assertThat(renderDetails(expense, updated(e -> e.currency("EUR")), SplitChange.NONE))
        .isEqualTo("currency: USD -> EUR;");
  }

  @Test
  void paidByChange_RendersFixedPhrase() {
    assertThat(renderDetails(expense, updated(e -> e.paidBy(200L)), SplitChange.NONE))
        .isEqualTo("paidBy changed;");
  }

  @Test
  void categoryChange_WhenOldCategoryNull_RendersNone() {
    Category food = Category.builder().id(1L).name("Food").build();
    Expense noCategory =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paidBy(1L)
            .build();
    Expense withCategory =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paidBy(1L)
            .category(food)
            .build();
    assertThat(renderDetails(noCategory, withCategory, SplitChange.NONE))
        .isEqualTo("category: None -> Food;");
  }

  @Test
  void categoryChange_WhenIdDiffers_RendersOldToNewNames() {
    Category food = Category.builder().id(1L).name("Food").build();
    Category travel = Category.builder().id(2L).name("Travel").build();
    Expense oldExpense = updated(e -> e.category(food));
    Expense newExpense = updated(e -> e.category(travel));
    assertThat(renderDetails(oldExpense, newExpense, SplitChange.NONE))
        .isEqualTo("category: Food -> Travel;");
  }

  @Test
  void categoryProvided_ButSameId_RendersNothing() {
    Category food = Category.builder().id(1L).name("Food").build();
    Expense oldExpense = updated(e -> e.category(food));
    Expense sameCategory = updated(e -> e.category(food));
    assertThat(renderDetails(oldExpense, sameCategory, SplitChange.NONE)).isEmpty();
  }

  @Test
  void expenseDateChange_RendersDateChanged() {
    assertThat(
            renderDetails(
                expense, updated(e -> e.expenseDate(LocalDate.of(2026, 2, 1))), SplitChange.NONE))
        .isEqualTo("date changed;");
  }

  @Test
  void notesChange_RendersNotesUpdated() {
    assertThat(renderDetails(expense, updated(e -> e.notes("new")), SplitChange.NONE))
        .isEqualTo("notes updated;");
  }

  @Test
  void receiptUrlChange_RendersReceiptUpdated() {
    assertThat(renderDetails(expense, updated(e -> e.receiptUrl("new-receipt")), SplitChange.NONE))
        .isEqualTo("receipt updated;");
  }

  @Test
  void splitModified_RendersSplitsModified() {
    assertThat(renderDetails(expense, expense, SplitChange.MODIFIED))
        .isEqualTo("splits: modified;");
  }

  @Test
  void splitRecalculated_RendersSplitsRecalculated() {
    assertThat(renderDetails(expense, expense, SplitChange.RECALCULATED))
        .isEqualTo("splits: recalculated;");
  }

  @Test
  void providedValueEqualToOld_IsNotTreatedAsChange() {
    Expense unchanged = updated(e -> e.description("Dinner").amount(new BigDecimal("50.00")));
    assertThat(renderDetails(expense, unchanged, SplitChange.NONE)).isEmpty();
  }

  @Test
  void notProvidedFields_AreIgnored() {
    Expense allNull =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paidBy(1L)
            .build();
    assertThat(renderDetails(expense, allNull, SplitChange.NONE)).isEmpty();
  }

  @Test
  void fullUpdate_RendersEveryFragmentInCanonicalOrder() {
    Category food = Category.builder().id(1L).name("Food").build();
    Category travel = Category.builder().id(2L).name("Travel").build();
    Expense oldExpense =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Dinner")
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paidBy(1L)
            .category(food)
            .expenseDate(LocalDate.of(2026, 1, 10))
            .notes("old notes")
            .receiptUrl("old-receipt")
            .build();
    Expense newExpense =
        Expense.builder()
            .id(100L)
            .group(group)
            .description("Lunch")
            .amount(new BigDecimal("100.00"))
            .currency("EUR")
            .paidBy(2L)
            .category(travel)
            .expenseDate(LocalDate.of(2026, 2, 1))
            .notes("new")
            .receiptUrl("new-receipt")
            .build();
    assertThat(renderDetails(oldExpense, newExpense, SplitChange.RECALCULATED))
        .isEqualTo(
            "description: Dinner -> Lunch; amount: 50.00 -> 100.00; currency: USD -> EUR; "
                + "paidBy changed; category: Food -> Travel; date changed; notes updated; "
                + "receipt updated; splits: recalculated;");
  }

  @Test
  void getGroupActivity_DelegatesToRepositoryAndMapper() {
    List<ActivityLog> logs = List.of(ActivityLog.builder().groupId(10L).build());
    when(activityLogRepository.findByGroupIdOrderByTimestampDesc(10L)).thenReturn(logs);
    when(activityLogMapper.toDTOList(logs)).thenReturn(List.of(new ActivityLogDTO()));

    List<ActivityLogDTO> result = engine.getGroupActivity(10L);

    assertThat(result).hasSize(1);
    verify(activityLogRepository).findByGroupIdOrderByTimestampDesc(10L);
  }

  private Expense updated(java.util.function.Consumer<Expense.ExpenseBuilder> mutator) {
    Expense.ExpenseBuilder builder =
        Expense.builder()
            .id(expense.getId())
            .group(expense.getGroup())
            .description(expense.getDescription())
            .amount(expense.getAmount())
            .currency(expense.getCurrency())
            .paidBy(expense.getPaidBy())
            .category(expense.getCategory())
            .expenseDate(expense.getExpenseDate())
            .notes(expense.getNotes())
            .receiptUrl(expense.getReceiptUrl());
    mutator.accept(builder);
    return builder.build();
  }

  private String renderDetails(
      Expense oldExpense, Expense updatedExpense, SplitChange splitChange) {
    engine.recordUpdated(oldExpense, updatedExpense, splitChange, 1L);

    ArgumentCaptor<ActivityLog> captor = ArgumentCaptor.forClass(ActivityLog.class);
    verify(activityLogRepository).save(captor.capture());
    return captor.getValue().getDetails();
  }
}
