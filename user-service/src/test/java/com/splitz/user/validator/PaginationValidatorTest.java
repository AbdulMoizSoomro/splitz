package com.splitz.user.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.splitz.user.exception.InvalidPaginationException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginationValidatorTest {

  private PaginationValidator validator;

  private static final Set<String> ALLOWED_SORT_FIELDS =
      Set.of("id", "username", "email", "firstName", "lastName", "createdAt");
  private static final int MAX_PAGE_SIZE = 100;

  @BeforeEach
  void setUp() {
    validator =
        new PaginationValidator(ALLOWED_SORT_FIELDS, MAX_PAGE_SIZE, "id", Sort.Direction.ASC);
  }

  @Nested
  @DisplayName("Page Size Validation")
  class PageSizeValidationTests {

    @Test
    @DisplayName("Should accept page size <= max")
    void shouldAcceptValidPageSize() {
      Pageable pageable = PageRequest.of(0, 50, Sort.by("id").ascending());

      Pageable validated = validator.validate(pageable);

      assertThat(validated.getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should throw InvalidPaginationException for page size > max")
    void shouldThrowForPageSizeExceedingMax() {
      Pageable pageable = PageRequest.of(0, 150, Sort.by("id").ascending());

      Throwable thrown = catchThrowable(() -> validator.validate(pageable));

      assertThat(thrown)
          .isInstanceOf(InvalidPaginationException.class)
          .hasMessageContaining("Page size cannot exceed 100");
    }
  }

  @Nested
  @DisplayName("Sort Field Validation")
  class SortFieldValidationTests {

    @Test
    @DisplayName("Should accept allowed sort fields")
    void shouldAcceptAllowedSortField() {
      Pageable pageable = PageRequest.of(0, 20, Sort.by("username").ascending());

      Pageable validated = validator.validate(pageable);

      assertThat(validated.getSort().getOrderFor("username")).isNotNull();
    }

    @Test
    @DisplayName("Should throw InvalidPaginationException for disallowed sort field")
    void shouldThrowForDisallowedSortField() {
      Pageable pageable = PageRequest.of(0, 20, Sort.by("passwordHash").ascending());

      Throwable thrown = catchThrowable(() -> validator.validate(pageable));

      assertThat(thrown)
          .isInstanceOf(InvalidPaginationException.class)
          .hasMessageContaining("Sort field 'passwordHash' is not allowed");
    }
  }

  @Nested
  @DisplayName("Default Sort Behavior")
  class DefaultSortTests {

    @Test
    @DisplayName("Should apply default sort when no sort is provided")
    void shouldApplyDefaultSortWhenNoSort() {
      Pageable pageable = PageRequest.of(0, 20);

      Pageable validated = validator.validate(pageable);

      Sort.Order defaultOrder = validated.getSort().getOrderFor("id");
      assertThat(defaultOrder).isNotNull();
      assertThat(defaultOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("Should keep user-provided sort when present")
    void shouldKeepUserSortWhenProvided() {
      Pageable pageable = PageRequest.of(0, 20, Sort.by("username").descending());

      Pageable validated = validator.validate(pageable);

      Sort.Order order = validated.getSort().getOrderFor("username");
      assertThat(order).isNotNull();
      assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }
  }
}
