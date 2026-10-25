package com.siciya.c.fraud.domain.evaluation;

public enum EvaluationSort {
  EVALUATED_AT_ASC("evaluatedAt,asc"),
  EVALUATED_AT_DESC("evaluatedAt,desc"),
  RISK_SCORE_ASC("riskScore,asc"),
  RISK_SCORE_DESC("riskScore,desc");

  private final String apiValue;

  EvaluationSort(String apiValue) {
    this.apiValue = apiValue;
  }

  public static EvaluationSort fromApiValue(String value) {
    for (EvaluationSort sort : values()) {
      if (sort.apiValue.equals(value)) {
        return sort;
      }
    }
    throw new IllegalArgumentException("sort is not supported");
  }
}
