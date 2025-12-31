package com.splitz.user.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.net.URISyntaxException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("User Already Exists");
        problem.setDetail(ex.getMessage());
        problem.setType(create("https://example.com/errors/user-already-exists"));
        problem.setInstance(create(request.getRequestURI()));
        return problem;
    }

    @org.springframework.lang.NonNull
    public static URI create(String str) {
        try {
            return new URI(str);
        } catch (URISyntaxException var2) {
            throw new IllegalArgumentException(var2.getMessage(), var2);
        }
    }
}