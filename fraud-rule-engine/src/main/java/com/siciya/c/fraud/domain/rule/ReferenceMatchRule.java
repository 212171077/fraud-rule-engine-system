package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataEntry;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataSnapshot;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import com.siciya.c.fraud.domain.evaluation.ReferenceSubjectType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class ReferenceMatchRule implements FraudRule {
  private final ReferenceDataType dataType;

  ReferenceMatchRule(ReferenceDataType dataType) {
    this.dataType = dataType;
  }

  @Override
  public RuleEvaluation evaluate(EvaluationContext context, RuleConfiguration configuration) {
    RuleEvaluation invalid = RuleSupport.validate(ruleCode(), context, configuration);
    if (invalid != null) {
      return invalid;
    }
    if (!configuration.enabled()) {
      return RuleSupport.disabled(configuration);
    }
    boolean required;
    try {
      required = RuleSupport.booleanValue(configuration, "required", true);
    } catch (RuleSupport.InvalidConfigurationException exception) {
      return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONFIGURATION, false);
    }

    if (context.referenceData() instanceof ContextData.Missing<ReferenceDataSnapshot>) {
      return required
          ? RuleSupport.failure(configuration, RuleFailureCode.REFERENCE_DATA_UNAVAILABLE, false)
          : RuleSupport.notApplicable(configuration, "REFERENCE_DATA_MISSING");
    }
    if (context.referenceData()
        instanceof ContextData.Unavailable<ReferenceDataSnapshot> unavailable) {
      if (unavailable.source() != ContextData.DataSource.REFERENCE_DATA) {
        return RuleSupport.failure(configuration, RuleFailureCode.INVALID_CONTEXT, false);
      }
      return RuleSupport.failure(
          configuration,
          unavailable.reason() == ContextData.UnavailableReason.INVALID_RESPONSE
              ? RuleFailureCode.INVALID_CONTEXT
              : RuleFailureCode.REFERENCE_DATA_UNAVAILABLE,
          unavailable.retryable());
    }
    var referenceContext =
        RuleSupport.contextValue(
            configuration,
            context,
            context.referenceData(),
            ContextData.DataSource.REFERENCE_DATA,
            required);
    if (referenceContext.hasOutcome()) {
      return referenceContext.outcome();
    }
    ReferenceDataSnapshot snapshot = referenceContext.value();
    Long version = snapshot.versions().get(dataType);
    if (version == null) {
      return required
          ? RuleSupport.failure(configuration, RuleFailureCode.REFERENCE_DATA_UNAVAILABLE, false)
          : RuleSupport.notApplicable(configuration, "REFERENCE_DATA_TYPE_MISSING");
    }

    List<Candidate> candidates = candidates(context);
    if (candidates.isEmpty()) {
      return RuleSupport.notApplicable(configuration, "REFERENCE_SUBJECT_NOT_PRESENT");
    }
    for (Candidate candidate : candidates) {
      ReferenceDataEntry match =
          snapshot.entries().stream()
              .filter(entry -> entry.type() == dataType)
              .filter(entry -> entry.subjectType() == candidate.subjectType())
              .filter(entry -> entry.normalizedValue().equals(candidate.value()))
              .filter(entry -> entry.effectiveAt(context.transaction().occurredAt()))
              .findFirst()
              .orElse(null);
      if (match != null) {
        return RuleSupport.result(
            configuration,
            RuleOutcome.MATCH,
            configuration.scoreContribution(),
            new RuleEvidence(
                match.reasonCode(),
                Map.of(
                    "reference_type", dataType.name(),
                    "reference_version", Long.toString(version),
                    "subject_type", candidate.subjectType().name())));
      }
    }
    return RuleSupport.result(configuration, RuleOutcome.NO_MATCH, BigDecimal.ZERO, null);
  }

  protected abstract List<Candidate> candidates(EvaluationContext context);

  protected record Candidate(ReferenceSubjectType subjectType, String value) {}

  protected static List<Candidate> entityCandidates(EvaluationContext context) {
    var event = context.transaction();
    List<Candidate> candidates = new ArrayList<>();
    if (event.accountId() != null) {
      candidates.add(new Candidate(ReferenceSubjectType.ACCOUNT, event.accountId()));
    }
    if (event.customerId() != null) {
      candidates.add(new Candidate(ReferenceSubjectType.CUSTOMER, event.customerId()));
    }
    if (event.merchantId() != null) {
      candidates.add(new Candidate(ReferenceSubjectType.MERCHANT, event.merchantId()));
    }
    if (event.beneficiaryId() != null) {
      candidates.add(new Candidate(ReferenceSubjectType.BENEFICIARY, event.beneficiaryId()));
    }
    return List.copyOf(candidates);
  }
}
