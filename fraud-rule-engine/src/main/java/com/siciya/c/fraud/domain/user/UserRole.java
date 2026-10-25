package com.siciya.c.fraud.domain.user;

import java.util.Set;

public enum UserRole {
  ADMIN(
      Set.of(
          "USERS_MANAGE",
          "OWN_PROFILE_READ",
          "OWN_PROFILE_WRITE",
          "EVALUATIONS_READ",
          "REPORTS_READ",
          "RULES_READ",
          "RULES_WRITE")),
  FRAUD_ANALYST(
      Set.of(
          "OWN_PROFILE_READ",
          "OWN_PROFILE_WRITE",
          "EVALUATIONS_READ",
          "REPORTS_READ",
          "RULES_READ")),
  AUDITOR(
      Set.of(
          "OWN_PROFILE_READ",
          "OWN_PROFILE_WRITE",
          "EVALUATIONS_READ",
          "REPORTS_READ",
          "RULES_READ")),
  SERVICE(Set.of("EVENT_PROCESS"));

  private final Set<String> permissions;

  UserRole(Set<String> permissions) {
    this.permissions = permissions;
  }

  public Set<String> permissions() {
    return permissions;
  }

  public boolean interactive() {
    return this != SERVICE;
  }
}
