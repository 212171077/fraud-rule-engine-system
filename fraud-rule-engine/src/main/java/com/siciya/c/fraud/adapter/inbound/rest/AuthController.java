package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.BootstrapRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.ChangePasswordRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.LoginRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.LoginView;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.MeView;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.RegisterRequest;
import com.siciya.c.fraud.adapter.inbound.rest.UserApiModels.UserView;
import com.siciya.c.fraud.application.port.in.UserAccountUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "fraud.security.local-auth", name = "enabled", havingValue = "true")
public class AuthController {
  private final UserAccountUseCase users;
  private final String bootstrapToken;

  public AuthController(
      UserAccountUseCase users,
      @Value("${fraud.security.local-auth.bootstrap-token:}") String bootstrapToken) {
    this.users = users;
    this.bootstrapToken = bootstrapToken;
  }

  @PostMapping(path = "/bootstrap", consumes = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirements
  @ResponseStatus(HttpStatus.CREATED)
  UserView bootstrap(
      @RequestHeader(name = "X-Bootstrap-Token", required = false) String suppliedToken,
      @Valid @RequestBody BootstrapRequest request) {
    return UserApiModels.toView(
        users.bootstrapAdmin(
            request.name(),
            request.surname(),
            request.email(),
            request.password(),
            suppliedToken,
            bootstrapToken));
  }

  @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirements
  LoginView login(@Valid @RequestBody LoginRequest request) {
    UserAccountUseCase.LoginSession session =
        users
            .authenticate(request.email(), request.password())
            .orElseThrow(() -> new BadCredentialsException("Authentication failed"));
    return new LoginView(
        session.accessToken(), "Bearer", session.expiresIn(), UserApiModels.toView(session.user()));
  }

  @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  @SecurityRequirements
  @ResponseStatus(HttpStatus.CREATED)
  UserView register(@Valid @RequestBody RegisterRequest request) {
    return UserApiModels.toView(
        users.register(request.name(), request.surname(), request.email(), request.password()));
  }

  @PatchMapping(path = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
  void changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
    if (!users.changePassword(
        UUID.fromString(principal.getName()), request.currentPassword(), request.newPassword())) {
      throw new BadCredentialsException("Authentication failed");
    }
  }

  @GetMapping("/me")
  MeView me(Principal principal) {
    return UserApiModels.toMeView(users.requireProfile(UUID.fromString(principal.getName())));
  }
}
