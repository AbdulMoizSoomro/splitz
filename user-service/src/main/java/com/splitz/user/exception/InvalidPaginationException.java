package com.splitz.user.exception;

public class InvalidPaginationException extends RuntimeException {
  public InvalidPaginationException(String message) {
    super(message);
  }
}
