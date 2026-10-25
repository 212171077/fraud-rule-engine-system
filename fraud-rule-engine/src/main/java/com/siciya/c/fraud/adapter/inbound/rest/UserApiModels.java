package com.siciya.c.fraud.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.siciya.c.fraud.application.port.in.UserAccountUseCase;
import com.siciya.c.fraud.domain.user.UserRole;
import com.siciya.c.fraud.domain.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserApiModels {
  private UserApiModels() {}

  public record BootstrapRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 100) String surname,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record LoginRequest(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record RegisterRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 100) String surname,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
      throw new IllegalArgumentException("unknown registration field");
    }
  }

  public record ChangePasswordRequest(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  public record CreateUserRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 100) String surname,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull UserRole role,
      @Size(min = 12, max = 72) String password) {}

  public record PatchProfileRequest(
      @Size(max = 100) String name,
      @Size(max = 100) String surname,
      @Email @Size(max = 254) String email) {}

  public record ChangeRoleRequest(@NotNull UserRole role) {}

  public record ChangeStatusRequest(@NotNull UserStatus status) {}

  public record UserView(
      UUID id,
      String name,
      String surname,
      String email,
      UserRole role,
      UserStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  public record UserPageView(
      List<UserView> content, int page, int size, long totalElements, int totalPages) {}

  public record MeView(
      UUID id, String name, String surname, String email, UserRole role, Set<String> permissions) {}

  public record LoginView(String accessToken, String tokenType, long expiresIn, UserView user) {}

  public static UserView toView(UserAccountUseCase.UserProfile profile) {
    return new UserView(
        profile.id(),
        profile.name(),
        profile.surname(),
        profile.email(),
        profile.role(),
        profile.status(),
        profile.createdAt(),
        profile.updatedAt());
  }

  public static UserPageView toView(UserAccountUseCase.UserPage page) {
    return new UserPageView(
        page.content().stream().map(UserApiModels::toView).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }

  public static MeView toMeView(UserAccountUseCase.UserProfile profile) {
    return new MeView(
        profile.id(),
        profile.name(),
        profile.surname(),
        profile.email(),
        profile.role(),
        profile.role().permissions());
  }
}
