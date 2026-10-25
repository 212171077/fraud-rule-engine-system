package com.siciya.c.fraud.domain.evaluation;

public record EvaluationPageRequest(int pageNumber, int pageSize) {
  public static final int MAX_PAGE_SIZE = 100;

  public EvaluationPageRequest {
    if (pageNumber < 0) {
      throw new IllegalArgumentException("pageNumber must be non-negative");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
    }
    try {
      Math.multiplyExact(pageNumber, pageSize);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("requested page offset is too large", exception);
    }
  }
}
