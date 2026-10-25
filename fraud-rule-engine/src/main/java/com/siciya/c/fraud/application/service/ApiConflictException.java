package com.siciya.c.fraud.application.service;

public final class ApiConflictException extends RuntimeException {
  public ApiConflictException() {
    super("resource conflict");
  }
}
