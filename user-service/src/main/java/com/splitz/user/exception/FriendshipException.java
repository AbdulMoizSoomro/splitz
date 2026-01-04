package com.splitz.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FriendshipException extends RuntimeException {
  public FriendshipException(String message) {
    super(message);
  }
}
