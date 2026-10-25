package com.siciya.c.fraud.adapter.inbound.rest;

import com.siciya.c.fraud.application.service.EvaluationQueryService;
import com.siciya.c.fraud.domain.decision.FraudDecision;
import com.siciya.c.fraud.domain.evaluation.ProcessingStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/fraud-evaluations", produces = MediaType.APPLICATION_JSON_VALUE)
public class FraudEvaluationController {
  private final EvaluationQueryService evaluations;

  public FraudEvaluationController(EvaluationQueryService evaluations) {
    this.evaluations = evaluations;
  }

  @GetMapping
  EvaluationApiModels.EvaluationPageView search(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) FraudDecision decision,
      @RequestParam(required = false) ProcessingStatus status,
      @RequestParam(required = false) String eventId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "evaluatedAt,desc") String sort) {
    return EvaluationApiModels.toView(
        evaluations.search(from, to, decision, status, eventId, page, size, sort));
  }

  @GetMapping("/{evaluationId}")
  EvaluationApiModels.EvaluationView get(@PathVariable UUID evaluationId) {
    return EvaluationApiModels.toView(evaluations.require(evaluationId));
  }

  @GetMapping("/summary")
  EvaluationApiModels.EvaluationSummaryView summary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return EvaluationApiModels.toView(evaluations.summary(from, to));
  }
}
