package com.siciya.c.fraud.domain.transaction;

import java.util.Objects;

public record TransactionSubject(SubjectType type, String identifier) {

  public TransactionSubject {
    Objects.requireNonNull(type, "type");
    identifier = TransactionValues.identifier(identifier, "identifier");
  }

  public enum SubjectType {
    ACCOUNT,
    CUSTOMER
  }
}
