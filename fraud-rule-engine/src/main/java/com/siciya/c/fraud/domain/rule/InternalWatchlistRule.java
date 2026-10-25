package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import java.util.List;

public final class InternalWatchlistRule extends ReferenceMatchRule {
  public static final String CODE = "INTERNAL_WATCHLIST";

  public InternalWatchlistRule() {
    super(ReferenceDataType.INTERNAL_WATCHLIST);
  }

  @Override
  public String ruleCode() {
    return CODE;
  }

  @Override
  protected List<Candidate> candidates(
      com.siciya.c.fraud.domain.evaluation.EvaluationContext context) {
    return entityCandidates(context);
  }
}
