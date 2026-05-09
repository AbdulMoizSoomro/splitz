package com.splitz.user.validator;

import com.splitz.user.exception.InvalidPaginationException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationValidator {

  private final Set<String> allowedSortFields;
  private final int maxPageSize;
  private final String defaultSortField;
  private final Sort.Direction defaultSortDirection;

  public PaginationValidator(
      Set<String> allowedSortFields,
      int maxPageSize,
      String defaultSortField,
      Sort.Direction defaultSortDirection) {
    this.allowedSortFields = allowedSortFields;
    this.maxPageSize = maxPageSize;
    this.defaultSortField = defaultSortField;
    this.defaultSortDirection = defaultSortDirection;
  }

  public Pageable validate(Pageable pageable) {
    if (pageable.getPageSize() > maxPageSize) {
      throw new InvalidPaginationException(
          String.format("Page size cannot exceed %d", maxPageSize));
    }

    Sort sort = pageable.getSort();
    if (!sort.isSorted()) {
      sort = Sort.by(defaultSortDirection, defaultSortField);
    } else {
      for (Sort.Order order : sort) {
        if (!allowedSortFields.contains(order.getProperty())) {
          throw new InvalidPaginationException(
              String.format("Sort field '%s' is not allowed", order.getProperty()));
        }
      }
    }

    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }
}
