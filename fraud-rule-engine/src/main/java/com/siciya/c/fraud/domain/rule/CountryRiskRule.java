package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.EvaluationContext;
import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import com.siciya.c.fraud.domain.evaluation.ReferenceSubjectType;
import java.util.ArrayList;
import java.util.List;

public final class CountryRiskRule extends ReferenceMatchRule {
  public static final String CODE = "COUNTRY_RISK";

  public CountryRiskRule() {
    super(ReferenceDataType.COUNTRY_RISK);
  }

  @Override
  public String ruleCode() {
    return CODE;
  }

  @Override
  protected List<Candidate> candidates(EvaluationContext context) {
    List<Candidate> candidates = new ArrayList<>();
    if (context.transaction().originCountry() != null) {
      candidates.add(
          new Candidate(ReferenceSubjectType.COUNTRY, context.transaction().originCountry()));
    }
    if (context.transaction().beneficiaryCountry() != null) {
      candidates.add(
          new Candidate(ReferenceSubjectType.COUNTRY, context.transaction().beneficiaryCountry()));
    }
    return List.copyOf(candidates);
  }
}
