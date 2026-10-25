package com.siciya.c.fraud.adapter.outbound.identity;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.UserEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.entity.UserSecurityAuditEntity;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.UserBootstrapLockRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.UserRepository;
import com.siciya.c.fraud.adapter.outbound.persistence.repository.UserSecurityAuditRepository;
import com.siciya.c.fraud.application.port.in.UserAccountUseCase;
import com.siciya.c.fraud.application.service.ApiConflictException;
import com.siciya.c.fraud.application.service.ApiResourceNotFoundException;
import com.siciya.c.fraud.domain.user.UserRole;
import com.siciya.c.fraud.domain.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@ConditionalOnProperty(prefix = "fraud.security.local-auth", name = "enabled", havingValue = "true")
public class UserAccountService implements UserAccountUseCase {
  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

  private final UserRepository users;
  private final UserSecurityAuditRepository audit;
  private final UserBootstrapLockRepository bootstrapLock;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final Clock clock;
  private final String dummyPasswordHash;
  private final String issuer;
  private final String audience;
  private final Duration accessTokenLifetime;

  public UserAccountService(
      UserRepository users,
      UserSecurityAuditRepository audit,
      UserBootstrapLockRepository bootstrapLock,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      Clock clock,
      @Value("${fraud.security.local-auth.issuer:}") String localJwtIssuer,
      @Value("${fraud.security.local-auth.audience:}") String localJwtAudience,
      @Value("${fraud.security.local-auth.access-token-lifetime:PT5M}")
          Duration accessTokenLifetime) {
    this.users = Objects.requireNonNull(users, "users");
    this.audit = Objects.requireNonNull(audit, "audit");
    this.bootstrapLock = Objects.requireNonNull(bootstrapLock, "bootstrapLock");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
    this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.issuer = Objects.requireNonNull(localJwtIssuer, "localJwtIssuer");
    this.audience = Objects.requireNonNull(localJwtAudience, "localJwtAudience");
    this.accessTokenLifetime = Objects.requireNonNull(accessTokenLifetime, "accessTokenLifetime");
    if (issuer.isBlank() || audience.isBlank()) {
      throw new IllegalStateException("Local JWT issuer and audience must be configured");
    }
    if (accessTokenLifetime.isNegative()
        || accessTokenLifetime.isZero()
        || accessTokenLifetime.compareTo(Duration.ofMinutes(15)) > 0) {
      throw new IllegalStateException(
          "Local access token lifetime must be between 1 and 15 minutes");
    }
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public UserProfile bootstrapAdmin(
      String name,
      String surname,
      String email,
      String password,
      String suppliedBootstrapToken,
      String configuredBootstrapToken) {
    verifyBootstrapToken(suppliedBootstrapToken, configuredBootstrapToken);
    bootstrapLock
        .findSingletonForUpdate()
        .orElseThrow(() -> new IllegalStateException("user bootstrap lock is missing"));
    if (users.count() != 0) {
      throw new ApiConflictException();
    }
    UserEntity admin =
        createUser(name, surname, email, password, UserRole.ADMIN, null, "BOOTSTRAP_ADMIN");
    return toProfile(admin);
  }

  @Transactional
  public UserProfile register(String name, String surname, String email, String password) {
    bootstrapLock
        .findSingletonForUpdate()
        .orElseThrow(() -> new IllegalStateException("user bootstrap lock is missing"));
    if (users.count() == 0) {
      throw new ApiConflictException();
    }
    return toProfile(
        createUser(
            name,
            surname,
            email,
            password,
            UserRole.AUDITOR,
            UserStatus.PENDING,
            null,
            "USER_REGISTERED"));
  }

  @Transactional
  public UserProfile create(
      String name, String surname, String email, String password, UserRole role, UUID actorUserId) {
    if (role == UserRole.SERVICE && password != null) {
      throw new IllegalArgumentException("service identities cannot have passwords");
    }
    if (role != UserRole.SERVICE && password == null) {
      throw new IllegalArgumentException("interactive users require a password");
    }
    UserEntity created =
        createUser(name, surname, email, password, role, actorUserId, "USER_CREATED");
    return toProfile(created);
  }

  @Transactional
  public Optional<LoginSession> authenticate(String email, String password) {
    Instant now = now();
    String normalizedEmail = normalizeEmailForLookup(email);
    Optional<UserEntity> match = users.findByEmailForUpdate(normalizedEmail);
    UserEntity user = match.orElse(null);
    boolean passwordMatches =
        passwordMatches(
            password,
            user == null || user.getPasswordHash() == null
                ? dummyPasswordHash
                : user.getPasswordHash());
    boolean locked =
        user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    boolean accepted =
        user != null
            && user.getStatus() == UserStatus.ACTIVE
            && user.getRole().interactive()
            && !locked
            && passwordMatches;

    if (!accepted) {
      if (user != null
          && user.getStatus() == UserStatus.ACTIVE
          && user.getRole().interactive()
          && !locked) {
        user.recordFailedLogin(MAX_FAILED_ATTEMPTS, now.plus(LOCKOUT_DURATION), now);
      }
      recordAudit(null, user == null ? null : user.getUserId(), "LOGIN", "FAILURE", now);
      return Optional.empty();
    }

    user.recordSuccessfulLogin(now);
    recordAudit(user.getUserId(), user.getUserId(), "LOGIN", "SUCCESS", now);
    return Optional.of(issueSession(user, now));
  }

  @Transactional(readOnly = true)
  public UserProfile requireProfile(UUID userId) {
    return toProfile(findUser(userId));
  }

  @Transactional(readOnly = true)
  public UserProfile get(UUID targetUserId, UUID actorUserId, boolean administrator) {
    if (!administrator && !targetUserId.equals(actorUserId)) {
      throw new ApiResourceNotFoundException();
    }
    return toProfile(findUser(targetUserId));
  }

  @Transactional(readOnly = true)
  public UserPage list(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("user pagination is outside the supported range");
    }
    var result =
        users.findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.PageRequest.of(page, size));
    return new UserPage(
        result.getContent().stream().map(UserAccountService::toProfile).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional
  public UserProfile updateProfile(
      UUID targetUserId,
      UUID actorUserId,
      boolean administrator,
      String name,
      String surname,
      String email) {
    if (!administrator && !targetUserId.equals(actorUserId)) {
      throw new ApiResourceNotFoundException();
    }
    if (name == null && surname == null && email == null) {
      throw new IllegalArgumentException("at least one profile field must be supplied");
    }
    UserEntity user = findForUpdate(targetUserId);
    String updatedName = name == null ? user.getName() : name;
    String updatedSurname = surname == null ? user.getSurname() : surname;
    String updatedEmail = email == null ? user.getNormalizedEmail() : normalizeEmail(email);
    validateName(updatedName);
    validateSurname(updatedSurname, surname == null && user.getSurname().isBlank());
    validateEmail(updatedEmail);
    user.updateProfile(updatedName.strip(), updatedSurname.strip(), updatedEmail, now());
    recordAudit(actorUserId, targetUserId, "PROFILE_UPDATED", "SUCCESS", now());
    return toProfile(user);
  }

  @Transactional
  public boolean changePassword(UUID userId, String currentPassword, String newPassword) {
    UserEntity user = findForUpdate(userId);
    Instant now = now();
    boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
    boolean accepted =
        user.getStatus() == UserStatus.ACTIVE
            && user.getRole().interactive()
            && !locked
            && passwordMatches(currentPassword, user.getPasswordHash());
    if (!accepted) {
      if (user.getStatus() == UserStatus.ACTIVE && user.getRole().interactive() && !locked) {
        user.recordFailedLogin(MAX_FAILED_ATTEMPTS, now.plus(LOCKOUT_DURATION), now);
      }
      recordAudit(null, userId, "PASSWORD_CHANGE", "FAILURE", now);
      return false;
    }
    validatePassword(newPassword);
    user.updatePassword(passwordEncoder.encode(newPassword), now);
    recordAudit(userId, userId, "PASSWORD_CHANGE", "SUCCESS", now);
    return true;
  }

  @Transactional
  public UserProfile updateRole(UUID targetUserId, UUID actorUserId, UserRole role) {
    if (targetUserId.equals(actorUserId)) {
      throw new AccessDeniedException("administrators cannot change their own role");
    }
    UserEntity user = findForUpdate(targetUserId);
    if (user.getRole() == UserRole.SERVICE || role == UserRole.SERVICE) {
      throw new IllegalArgumentException("service identity roles cannot be changed");
    }
    user.updateRole(role, now());
    recordAudit(actorUserId, targetUserId, "ROLE_UPDATED", "SUCCESS", now());
    return toProfile(user);
  }

  @Transactional
  public UserProfile updateStatus(UUID targetUserId, UUID actorUserId, UserStatus status) {
    if (targetUserId.equals(actorUserId)) {
      throw new AccessDeniedException("administrators cannot change their own account status");
    }
    UserEntity user = findForUpdate(targetUserId);
    user.updateStatus(status, now());
    recordAudit(actorUserId, targetUserId, "STATUS_UPDATED", "SUCCESS", now());
    return toProfile(user);
  }

  @Transactional(readOnly = true)
  public boolean isCurrentToken(Jwt jwt) {
    UUID userId;
    Number tokenVersion;
    try {
      userId = UUID.fromString(jwt.getSubject());
      tokenVersion = jwt.getClaim("token_version");
    } catch (IllegalArgumentException | NullPointerException exception) {
      return false;
    }
    if (tokenVersion == null) {
      return false;
    }
    return users
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .filter(user -> user.getTokenVersion() == tokenVersion.longValue())
        .filter(user -> user.getRole().interactive())
        .map(
            user -> {
              var roles = jwt.getClaimAsStringList("roles");
              return roles != null && roles.contains(user.getRole().name());
            })
        .orElse(false);
  }

  private UserEntity createUser(
      String name,
      String surname,
      String email,
      String password,
      UserRole role,
      UUID actorUserId,
      String action) {
    return createUser(name, surname, email, password, role, UserStatus.ACTIVE, actorUserId, action);
  }

  private UserEntity createUser(
      String name,
      String surname,
      String email,
      String password,
      UserRole role,
      UserStatus status,
      UUID actorUserId,
      String action) {
    validateName(name);
    validateSurname(surname, role == UserRole.SERVICE);
    validateEmail(email);
    if (role != UserRole.SERVICE) {
      validatePassword(password);
    }
    Instant now = now();
    UserEntity user =
        new UserEntity(
            UUID.randomUUID(),
            name.strip(),
            surname.strip(),
            normalizeEmail(email),
            role == UserRole.SERVICE ? null : passwordEncoder.encode(password),
            role,
            status,
            now);
    UserEntity saved = users.save(user);
    recordAudit(actorUserId, saved.getUserId(), action, "SUCCESS", now);
    return saved;
  }

  private LoginSession issueSession(UserEntity user, Instant issuedAt) {
    Instant expiresAt = issuedAt.plus(accessTokenLifetime);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getUserId().toString())
            .audience(java.util.List.of(audience))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("roles", java.util.List.of(user.getRole().name()))
            .claim("token_version", user.getTokenVersion())
            .build();
    JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).keyId("assessment-local-hs256").build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    return new LoginSession(token, accessTokenLifetime.toSeconds(), toProfile(user));
  }

  private void recordAudit(UUID actor, UUID target, String action, String outcome, Instant at) {
    audit.save(new UserSecurityAuditEntity(actor, target, action, outcome, at));
  }

  private UserEntity findUser(UUID userId) {
    return users.findById(userId).orElseThrow(ApiResourceNotFoundException::new);
  }

  private UserEntity findForUpdate(UUID userId) {
    return users.findForUpdate(userId).orElseThrow(ApiResourceNotFoundException::new);
  }

  private static UserProfile toProfile(UserEntity user) {
    return new UserProfile(
        user.getUserId(),
        user.getName(),
        user.getSurname(),
        user.getNormalizedEmail(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > 100) {
      throw new IllegalArgumentException("name is invalid");
    }
  }

  private static void validateSurname(String surname, boolean allowEmpty) {
    if (surname == null || (!allowEmpty && surname.isBlank()) || surname.strip().length() > 100) {
      throw new IllegalArgumentException("surname is invalid");
    }
  }

  private static void validateEmail(String email) {
    if (email == null
        || email.length() > 254
        || !email.strip().matches("(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
      throw new IllegalArgumentException("email is invalid");
    }
  }

  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private static String normalizeEmailForLookup(String email) {
    if (email == null || email.length() > 254) {
      return "";
    }
    return email.strip().toLowerCase(Locale.ROOT);
  }

  private static void validatePassword(String password) {
    if (password == null
        || password.length() < 12
        || password.length() > 72
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException(
          "password must be 12 to 72 characters and no more than 72 UTF-8 bytes");
    }
  }

  private boolean passwordMatches(String password, String passwordHash) {
    boolean compatible =
        password != null
            && password.length() <= 72
            && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    return passwordEncoder.matches(compatible ? password : "", passwordHash) && compatible;
  }

  private static void verifyBootstrapToken(String supplied, String configured) {
    if (configured == null
        || configured.length() < 32
        || supplied == null
        || !MessageDigest.isEqual(
            configured.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8))) {
      throw new AccessDeniedException("bootstrap authorization failed");
    }
  }

  private Instant now() {
    return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }
}
