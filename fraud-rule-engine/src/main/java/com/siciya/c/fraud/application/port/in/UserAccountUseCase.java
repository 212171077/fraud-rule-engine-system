package com.siciya.c.fraud.application.port.in;

import com.siciya.c.fraud.domain.user.UserRole;
import com.siciya.c.fraud.domain.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountUseCase {
  UserProfile bootstrapAdmin(
      String name,
      String surname,
      String email,
      String password,
      String suppliedToken,
      String configuredToken);

  UserProfile register(String name, String surname, String email, String password);

  UserProfile create(
      String name, String surname, String email, String password, UserRole role, UUID actorUserId);

  Optional<LoginSession> authenticate(String email, String password);

  UserProfile requireProfile(UUID userId);

  UserProfile get(UUID targetUserId, UUID actorUserId, boolean administrator);

  UserPage list(int page, int size);

  UserProfile updateProfile(
      UUID targetUserId,
      UUID actorUserId,
      boolean administrator,
      String name,
      String surname,
      String email);

  boolean changePassword(UUID userId, String currentPassword, String newPassword);

  UserProfile updateRole(UUID targetUserId, UUID actorUserId, UserRole role);

  UserProfile updateStatus(UUID targetUserId, UUID actorUserId, UserStatus status);

  record UserProfile(
      UUID id,
      String name,
      String surname,
      String email,
      UserRole role,
      UserStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  record UserPage(
      List<UserProfile> content, int page, int size, long totalElements, int totalPages) {}

  record LoginSession(String accessToken, long expiresIn, UserProfile user) {}
}
