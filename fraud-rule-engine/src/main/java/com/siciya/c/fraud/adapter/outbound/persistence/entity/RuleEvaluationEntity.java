package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.rule.RuleEvaluation;
import com.siciya.c.fraud.domain.rule.RuleEvidence;
import com.siciya.c.fraud.domain.rule.RuleFailure;
import com.siciya.c.fraud.domain.rule.RuleFailureCode;
import com.siciya.c.fraud.domain.rule.RuleOutcome;
import com.siciya.c.fraud.domain.rule.RuleResult;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "evaluation_rule_result")
public class RuleEvaluationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "result_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "evaluation_id", nullable = false)
  private EvaluationEntity evaluation;

  @Column(name = "rule_code", nullable = false, length = 64)
  private String ruleCode;

  @Column(name = "configuration_version", nullable = false)
  private long configurationVersion;

  @Column(length = 24)
  private String outcome;

  @Column(name = "failure_code", length = 40)
  private String failureCode;

  @Column private Boolean retryable;

  @Column(name = "score_contribution", nullable = false, precision = 38, scale = 18)
  private BigDecimal scoreContribution;

  @Column(name = "reason_code", length = 64)
  private String reasonCode;

  @ElementCollection
  @CollectionTable(
      name = "evaluation_evidence_attribute",
      joinColumns = @JoinColumn(name = "result_id"))
  @MapKeyColumn(name = "attribute_name", length = 64)
  @Column(name = "attribute_value", nullable = false, length = 128)
  private Map<String, String> attributes;

  protected RuleEvaluationEntity() {}

  RuleEvaluationEntity(EvaluationEntity evaluation, RuleEvaluation result) {
    this.evaluation = Objects.requireNonNull(evaluation, "evaluation");
    Objects.requireNonNull(result, "result");
    ruleCode = result.ruleCode();
    configurationVersion = result.configurationVersion();
    if (result instanceof RuleResult ruleResult) {
      outcome = ruleResult.outcome().name();
      scoreContribution = ruleResult.scoreContribution();
      if (ruleResult.evidence() != null) {
        reasonCode = ruleResult.evidence().reasonCode();
        attributes = ruleResult.evidence().attributes();
      }
    } else {
      RuleFailure failure = (RuleFailure) result;
      failureCode = failure.code().name();
      retryable = failure.retryable();
      scoreContribution = BigDecimal.ZERO;
    }
  }

  public RuleEvaluation toDomain() {
    if (failureCode != null) {
      return new RuleFailure(
          ruleCode, configurationVersion, RuleFailureCode.valueOf(failureCode), retryable);
    }
    RuleEvidence evidence = reasonCode == null ? null : new RuleEvidence(reasonCode, attributes);
    return new RuleResult(
        ruleCode, configurationVersion, RuleOutcome.valueOf(outcome), scoreContribution, evidence);
  }
}
