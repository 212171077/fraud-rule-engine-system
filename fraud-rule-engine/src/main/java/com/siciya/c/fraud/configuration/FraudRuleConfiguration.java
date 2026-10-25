package com.siciya.c.fraud.configuration;

import com.siciya.c.fraud.domain.decision.DecisionPolicy;
import com.siciya.c.fraud.domain.decision.RuleEvaluationService;
import com.siciya.c.fraud.domain.rule.BehavioralAnomalyRule;
import com.siciya.c.fraud.domain.rule.CountryRiskRule;
import com.siciya.c.fraud.domain.rule.DormantAccountRule;
import com.siciya.c.fraud.domain.rule.FraudRule;
import com.siciya.c.fraud.domain.rule.InternalWatchlistRule;
import com.siciya.c.fraud.domain.rule.ThresholdLimitRule;
import com.siciya.c.fraud.domain.rule.VelocityDuplicationRule;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudRuleConfiguration {
  @Bean
  BehavioralAnomalyRule behavioralAnomalyRule() {
    return new BehavioralAnomalyRule();
  }

  @Bean
  InternalWatchlistRule internalWatchlistRule() {
    return new InternalWatchlistRule();
  }

  @Bean
  CountryRiskRule countryRiskRule() {
    return new CountryRiskRule();
  }

  @Bean
  VelocityDuplicationRule velocityDuplicationRule() {
    return new VelocityDuplicationRule();
  }

  @Bean
  DormantAccountRule dormantAccountRule() {
    return new DormantAccountRule();
  }

  @Bean
  ThresholdLimitRule thresholdLimitRule() {
    return new ThresholdLimitRule();
  }

  @Bean
  RuleEvaluationService ruleEvaluationService(
      List<FraudRule> fraudRules, DecisionPolicy decisionPolicy) {
    return new RuleEvaluationService(fraudRules, decisionPolicy);
  }

  @Bean
  DecisionPolicy decisionPolicy() {
    return new DecisionPolicy();
  }
}
