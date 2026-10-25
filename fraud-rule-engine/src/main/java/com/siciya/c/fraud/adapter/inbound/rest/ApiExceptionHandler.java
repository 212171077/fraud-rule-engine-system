package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.application.service.ApiConflictException;
import com.siciya.c.fraud.application.service.ApiResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler({
    IllegalArgumentException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ProblemDetail> badRequest(Exception exception) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException exception) {
    ProblemDetail problem =
        problemBody(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.");
    Map<String, List<String>> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.groupingBy(
                    error -> error.getField(),
                    TreeMap::new,
                    Collectors.mapping(error -> "invalid value", Collectors.toList())));
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> unsupportedMediaType() {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The request content type is not supported.");
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ProblemDetail> notAcceptable() {
    return problem(
        HttpStatus.NOT_ACCEPTABLE,
        "NOT_ACCEPTABLE",
        "The requested representation is not available.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> methodNotAllowed() {
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "The request method is not supported.");
  }

  @ExceptionHandler({ApiResourceNotFoundException.class, NoResourceFoundException.class})
  ResponseEntity<ProblemDetail> notFound() {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.");
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> unauthorized() {
    return problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication failed.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> forbidden() {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "The requested operation is not permitted.");
  }

  @ExceptionHandler({
    ApiConflictException.class,
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception exception) {
    return problem(
        HttpStatus.CONFLICT, "CONFLICT", "The requested change conflicts with current state.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> internalError(Exception exception) {
    LOGGER.error("Unhandled API failure", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.");
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail) {
    return ResponseEntity.status(status).body(problemBody(status, code, detail));
  }

  private static ProblemDetail problemBody(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("https://fraud-rule-engine.invalid/problems/" + code.toLowerCase()));
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    return problem;
  }
}
