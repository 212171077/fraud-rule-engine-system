package com.siciya.c.fraud.domain.evaluation;

import java.util.List;
import java.util.Objects;

public record EvaluationPage(
    List<Evaluation> content, int pageNumber, int pageSize, long totalElements, int totalPages) {

  public EvaluationPage {
    Objects.requireNonNull(content, "content");
    if (pageNumber < 0 || pageSize < 1 || totalElements < 0 || totalPages < 0) {
      throw new IllegalArgumentException(
          "page metadata must be non-negative and pageSize positive");
    }
    if (content.size() > pageSize
        || totalPages != (int) Math.ceil((double) totalElements / pageSize)) {
      throw new IllegalArgumentException("page metadata does not match content size");
    }
    content = List.copyOf(content);
  }
}
