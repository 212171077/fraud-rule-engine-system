package com.siciya.c.fraud.application.service;

public final class ApiResourceNotFoundException extends RuntimeException {
  public ApiResourceNotFoundException() {
    super("resource not found");
  }
}
