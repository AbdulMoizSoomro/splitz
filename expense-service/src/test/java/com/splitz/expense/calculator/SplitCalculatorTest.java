package com.splitz.expense.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import com.splitz.expense.dto.SplitRequest;
import com.splitz.expense.model.SplitType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

  private SplitCalculator calculator;

  @BeforeEach
  void setUp() {
    List<SplitStrategy> strategies =
        List.of(
            new EqualSplitStrategy(),
            new ExactSplitStrategy(),
            new PercentageSplitStrategy(),
            new SharesSplitStrategy(),
            new AdjustmentSplitStrategy());
    calculator = new SplitCalculator(strategies, new RemainderHandler());
  }

  @Test
  void calculate_ExactSplits_ReturnsCorrectSplits() {
    BigDecimal totalAmount = new BigDecimal("100.00");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("40.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("60.00")).build());

    List<SplitResult> results = calculator.calculate(totalAmount, SplitType.EXACT, requests, "USD");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).userId()).isEqualTo(1L);
    assertThat(results.get(0).shareAmount()).isEqualTo(new BigDecimal("40.00"));
    assertThat(results.get(0).splitType()).isEqualTo(SplitType.EXACT);
    assertThat(results.get(0).splitValue()).isEqualTo(new BigDecimal("40.00"));

    assertThat(results.get(1).userId()).isEqualTo(2L);
    assertThat(results.get(1).shareAmount()).isEqualTo(new BigDecimal("60.00"));
  }

  @Test
  void calculate_EqualSplits_ReturnsCorrectSplits() {
    BigDecimal totalAmount = new BigDecimal("100.00");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).build(), SplitRequest.builder().userId(2L).build());

    List<SplitResult> results = calculator.calculate(totalAmount, SplitType.EQUAL, requests, "USD");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).shareAmount()).isEqualByComparingTo("50.00");
    assertThat(results.get(1).shareAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void calculate_PercentageSplits_ReturnsCorrectSplits() {
    BigDecimal totalAmount = new BigDecimal("100.00");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("50.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("50.00")).build());

    List<SplitResult> results =
        calculator.calculate(totalAmount, SplitType.PERCENTAGE, requests, "USD");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).shareAmount()).isEqualByComparingTo("50.00");
    assertThat(results.get(1).shareAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void calculate_SharesSplits_ReturnsCorrectSplits() {
    BigDecimal totalAmount = new BigDecimal("100.00");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("1.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("3.00")).build());

    List<SplitResult> results =
        calculator.calculate(totalAmount, SplitType.SHARES, requests, "USD");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).shareAmount()).isEqualByComparingTo("25.00");
    assertThat(results.get(1).shareAmount()).isEqualByComparingTo("75.00");
  }

  @Test
  void calculate_PercentageSplits_HandlesRemainderAndPrecision() {
    BigDecimal totalAmount = new BigDecimal("100.00");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("33.33")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("33.33")).build(),
            SplitRequest.builder().userId(3L).splitValue(new BigDecimal("33.34")).build());

    // Assuming default precision is 2
    List<SplitResult> results =
        calculator.calculate(totalAmount, SplitType.PERCENTAGE, requests, "USD");

    assertThat(results).hasSize(3);
    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("100.00");
  }

  @Test
  void calculate_SharesSplits_HandlesRemainder() {
    BigDecimal totalAmount = new BigDecimal("10.00"); // 10.00 / 3 shares = 3.3333...
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("1.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("1.00")).build(),
            SplitRequest.builder().userId(3L).splitValue(new BigDecimal("1.00")).build());

    List<SplitResult> results =
        calculator.calculate(totalAmount, SplitType.SHARES, requests, "USD");

    assertThat(results).hasSize(3);
    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("10.00");
  }

  @Test
  void calculate_AdjustmentSplits_ReturnsCorrectSplits() {
    BigDecimal totalAmount = new BigDecimal("100.00");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).splitValue(new BigDecimal("10.00")).build(),
            SplitRequest.builder().userId(2L).splitValue(new BigDecimal("-10.00")).build());

    List<SplitResult> results =
        calculator.calculate(totalAmount, SplitType.ADJUSTMENT, requests, "USD");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).shareAmount()).isEqualByComparingTo("60.00"); // 50 + 10
    assertThat(results.get(1).shareAmount()).isEqualByComparingTo("40.00"); // 50 - 10
  }

  @Test
  void calculate_MultiCurrency_Precision_JPY() {
    BigDecimal totalAmount = new BigDecimal("100");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).build(),
            SplitRequest.builder().userId(2L).build(),
            SplitRequest.builder().userId(3L).build());

    // 100 / 3 = 33.33... -> JPY scale is 0 -> 33 + 33 + 34
    List<SplitResult> results = calculator.calculate(totalAmount, SplitType.EQUAL, requests, "JPY");

    assertThat(results).hasSize(3);
    // Reminder is given to the first user
    assertThat(results.get(0).shareAmount()).isEqualByComparingTo("34");
    assertThat(results.get(1).shareAmount()).isEqualByComparingTo("33");
    assertThat(results.get(2).shareAmount()).isEqualByComparingTo("33");

    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("100");
  }

  @Test
  void calculate_MultiCurrency_Precision_KWD() {
    BigDecimal totalAmount = new BigDecimal("10.000");
    List<SplitRequest> requests =
        List.of(
            SplitRequest.builder().userId(1L).build(),
            SplitRequest.builder().userId(2L).build(),
            SplitRequest.builder().userId(3L).build());

    // 10 / 3 = 3.3333... -> KWD scale is 3 -> 3.333 + 3.333 + 3.334
    List<SplitResult> results = calculator.calculate(totalAmount, SplitType.EQUAL, requests, "KWD");

    assertThat(results).hasSize(3);
    assertThat(results.get(0).shareAmount()).isEqualByComparingTo("3.334");
    BigDecimal sum =
        results.stream().map(SplitResult::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("10.000");
  }
}
