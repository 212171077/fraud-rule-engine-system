package com.siciya.c.fraud.domain.rule;

import com.siciya.c.fraud.domain.evaluation.ReferenceDataType;
import java.util.List;

/**
 * Flags transactions involving an entity on the internal watchlist reference data.
 *
 * <p>Detects known accounts, customers, merchants or beneficiaries under internal review. The rule
 * matches when any of these identifiers has a watchlist entry effective at the transaction time. It
 * is not applicable when the transaction carries none of them.
 */
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
