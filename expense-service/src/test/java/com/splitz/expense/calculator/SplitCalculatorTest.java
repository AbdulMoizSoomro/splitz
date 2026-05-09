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
    calculator = new SplitCalculator();
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
}
