package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.user.UserRole;
import com.siciya.c.fraud.domain.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class UserEntity {
  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "surname", nullable = false, length = 100)
  private String surname;

  @Column(name = "normalized_email", nullable = false, unique = true, length = 254)
  private String normalizedEmail;

  @Column(name = "password_hash", length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private UserStatus status;

  @Column(name = "token_version", nullable = false)
  private long tokenVersion;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserEntity() {}

  public UserEntity(
      UUID userId,
      String name,
      String surname,
      String normalizedEmail,
      String passwordHash,
      UserRole role,
      UserStatus status,
      Instant createdAt) {
    this.userId = userId;
    this.name = name;
    this.surname = surname;
    this.normalizedEmail = normalizedEmail;
    this.passwordHash = passwordHash;
    this.role = role;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getSurname() {
    return surname;
  }

  public String getNormalizedEmail() {
    return normalizedEmail;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public long getTokenVersion() {
    return tokenVersion;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void updateProfile(String name, String surname, String email, Instant updatedAt) {
    this.name = name;
    this.surname = surname;
    this.normalizedEmail = email;
    this.updatedAt = updatedAt;
    tokenVersion++;
  }

  public void updateRole(UserRole role, Instant updatedAt) {
    this.role = role;
    this.updatedAt = updatedAt;
    tokenVersion++;
  }

  public void updateStatus(UserStatus status, Instant updatedAt) {
    this.status = status;
    this.updatedAt = updatedAt;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    tokenVersion++;
  }

  public void updatePassword(String passwordHash, Instant updatedAt) {
    this.passwordHash = passwordHash;
    this.updatedAt = updatedAt;
    failedLoginAttempts = 0;
    lockedUntil = null;
    tokenVersion++;
  }

  public void recordFailedLogin(int maxAttempts, Instant lockedUntil, Instant updatedAt) {
    failedLoginAttempts++;
    if (failedLoginAttempts >= maxAttempts) {
      this.lockedUntil = lockedUntil;
    }
    this.updatedAt = updatedAt;
  }

  public void recordSuccessfulLogin(Instant updatedAt) {
    failedLoginAttempts = 0;
    lockedUntil = null;
    this.updatedAt = updatedAt;
  }
}
