package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.ChangeRoleRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.ChangeStatusRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.CreateUserRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.PatchProfileRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.UserPageView;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.UserView;
import com.siciya.c.fraud.application.port.in.UserAccountUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "fraud.security.local-auth", name = "enabled", havingValue = "true")
public class UserController {
  private final UserAccountUseCase users;

  public UserController(UserAccountUseCase users) {
    this.users = users;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  UserView create(@Valid @RequestBody CreateUserRequest request, Principal principal) {
    return UserApiModels.toView(
        users.create(
            request.name(),
            request.surname(),
            request.email(),
            request.password(),
            request.role(),
            userId(principal)));
  }

  @GetMapping
  UserPageView list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return UserApiModels.toView(users.list(page, size));
  }

  @GetMapping("/{id}")
  UserView get(@PathVariable UUID id, Principal principal) {
    UUID actor = userId(principal);
    return UserApiModels.toView(users.get(id, actor, isAdministrator(principal)));
  }

  @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  UserView patch(
      @PathVariable UUID id, @Valid @RequestBody PatchProfileRequest request, Principal principal) {
    return UserApiModels.toView(
        users.updateProfile(
            id,
            userId(principal),
            isAdministrator(principal),
            request.name(),
            request.surname(),
            request.email()));
  }

  @PatchMapping(path = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
  UserView changeRole(
      @PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request, Principal principal) {
    return UserApiModels.toView(users.updateRole(id, userId(principal), request.role()));
  }

  @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
  UserView changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request, Principal principal) {
    return UserApiModels.toView(users.updateStatus(id, userId(principal), request.status()));
  }

  private static UUID userId(Principal principal) {
    return UUID.fromString(principal.getName());
  }

  private static boolean isAdministrator(Principal principal) {
    if (principal
        instanceof
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
            jwtPrincipal) {
      return jwtPrincipal.getAuthorities().stream()
          .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
    return false;
  }
}
