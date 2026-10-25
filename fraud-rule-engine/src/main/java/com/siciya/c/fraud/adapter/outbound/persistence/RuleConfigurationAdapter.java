package com.siciya.c.fraud.adapter.outbound.persistence;

import com.siciya.c.fraud.adapter.outbound.persistence.repository.RuleConfigurationSnapshotRepository;
import com.siciya.c.fraud.application.port.out.RuleConfigurationPort;
import com.siciya.c.fraud.domain.evaluation.ContextData;
import com.siciya.c.fraud.domain.rule.RuleConfigurationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RuleConfigurationAdapter implements RuleConfigurationPort {
  private final RuleConfigurationSnapshotRepository snapshots;
  private final Clock clock;

  public RuleConfigurationAdapter(RuleConfigurationSnapshotRepository snapshots, Clock clock) {
    this.snapshots = snapshots;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public ContextData<RuleConfigurationSnapshot> loadSnapshot(Instant effectiveAt) {
    Objects.requireNonNull(effectiveAt, "effectiveAt");
    var retrievedAt = clock.instant();
    var matching =
        snapshots.findByEffectiveAtLessThanEqualOrderByEffectiveAtDescCapturedAtDesc(
            effectiveAt, PageRequest.of(0, 1));
    if (matching.isEmpty()) {
      return new ContextData.Missing<>(ContextData.MissingReason.NOT_CONFIGURED, retrievedAt);
    }
    var snapshot = matching.getFirst();
    if (snapshot.getCapturedAt().isAfter(retrievedAt)) {
      return new ContextData.Unavailable<>(
          ContextData.DataSource.RULE_CONFIGURATION,
          ContextData.UnavailableReason.INVALID_RESPONSE,
          false,
          retrievedAt);
    }
    return new ContextData.Available<>(snapshot.toDomain(), retrievedAt, snapshot.getCapturedAt());
  }
}
