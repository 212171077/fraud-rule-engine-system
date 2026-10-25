package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.adapter.inbound.rest.FraudRuleApiModels.AuditView;
import com.siciya.c.fraud.adapter.inbound.rest.FraudRuleApiModels.CreateRuleRequest;
import com.siciya.c.fraud.adapter.inbound.rest.FraudRuleApiModels.DeactivateRuleRequest;
import com.siciya.c.fraud.adapter.inbound.rest.FraudRuleApiModels.PatchRuleRequest;
import com.siciya.c.fraud.adapter.inbound.rest.FraudRuleApiModels.RuleHistoryView;
import com.siciya.c.fraud.adapter.inbound.rest.FraudRuleApiModels.RuleView;
import com.siciya.c.fraud.application.service.RuleConfigurationManagementService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping(path = "/api/v1/fraud-rules", produces = MediaType.APPLICATION_JSON_VALUE)
public class FraudRuleController {
  private final RuleConfigurationManagementService rules;
  private final Clock clock;

  public FraudRuleController(RuleConfigurationManagementService rules, Clock clock) {
    this.rules = rules;
    this.clock = clock;
  }

  @GetMapping
  List<RuleView> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant effectiveAt) {
    Instant at = effectiveAt == null ? clock.instant() : effectiveAt;
    return rules.list(at).stream().map(FraudRuleApiModels::toView).toList();
  }

  @GetMapping("/{ruleCode}/history")
  RuleHistoryView history(@PathVariable String ruleCode) {
    List<AuditView> history =
        rules.history(ruleCode).stream().map(FraudRuleApiModels::toView).toList();
    return new RuleHistoryView(ruleCode, history);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  RuleView create(@Valid @RequestBody CreateRuleRequest request, Principal principal) {
    var snapshot =
        rules.create(
            request.ruleCode(),
            request.enabled(),
            request.scoreContribution(),
            request.parameters(),
            request.effectiveAt(),
            request.reasonCode(),
            principal.getName());
    return snapshot.configurations().stream()
        .filter(configuration -> configuration.ruleCode().equals(request.ruleCode()))
        .map(FraudRuleApiModels::toView)
        .findFirst()
        .orElseThrow(IllegalStateException::new);
  }

  @PatchMapping(path = "/{ruleCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
  RuleView patch(
      @PathVariable String ruleCode,
      @Valid @RequestBody PatchRuleRequest request,
      Principal principal) {
    var snapshot =
        rules.update(
            ruleCode,
            request.enabled(),
            request.scoreContribution(),
            request.parameters(),
            request.effectiveAt(),
            request.reasonCode(),
            principal.getName());
    return snapshot.configurations().stream()
        .filter(configuration -> configuration.ruleCode().equals(ruleCode))
        .map(FraudRuleApiModels::toView)
        .findFirst()
        .orElseThrow(IllegalStateException::new);
  }

  @DeleteMapping(path = "/{ruleCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
  RuleView deactivate(
      @PathVariable String ruleCode,
      @Valid @RequestBody DeactivateRuleRequest request,
      Principal principal) {
    var snapshot =
        rules.deactivate(
            ruleCode, request.effectiveAt(), request.reasonCode(), principal.getName());
    return snapshot.configurations().stream()
        .filter(configuration -> configuration.ruleCode().equals(ruleCode))
        .map(FraudRuleApiModels::toView)
        .findFirst()
        .orElseThrow(IllegalStateException::new);
  }
}
