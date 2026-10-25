package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import com.siciya.c.fraud.domain.transaction.HistoricalTransaction;
import com.siciya.c.fraud.domain.transaction.TransactionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity implements Persistable<String> {
  @Id
  @Column(name = "event_id", length = 128, nullable = false)
  private String eventId;

  @Column(name = "transaction_id", length = 128, nullable = false)
  private String transactionId;

  @Column(name = "account_id", length = 128)
  private String accountId;

  @Column(name = "customer_id", length = 128)
  private String customerId;

  @Column(nullable = false, precision = 38, scale = 18)
  private java.math.BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 64)
  private String category;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "merchant_id", length = 128)
  private String merchantId;

  @Column(length = 128)
  private String location;

  @Column(name = "beneficiary_id", length = 128)
  private String beneficiaryId;

  @Column(name = "origin_country", length = 2)
  private String originCountry;

  @Column(name = "beneficiary_country", length = 2)
  private String beneficiaryCountry;

  @Column(length = 32)
  private String channel;

  @Column(name = "schema_version", nullable = false, length = 16)
  private String schemaVersion;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(name = "qualifying_activity", nullable = false)
  private boolean qualifyingActivity;

  @Column(name = "ingested_at", nullable = false)
  private Instant ingestedAt;

  @Transient private boolean newEntity = true;

  protected ProcessedEventEntity() {}

  public ProcessedEventEntity(TransactionEvent event, Instant ingestedAt) {
    Objects.requireNonNull(event, "event");
    this.eventId = event.eventId();
    this.transactionId = event.transactionId();
    this.accountId = event.accountId();
    this.customerId = event.customerId();
    this.amount = event.amount();
    this.currency = event.currency();
    this.category = event.category();
    this.occurredAt = event.occurredAt();
    this.merchantId = event.merchantId();
    this.location = event.location();
    this.beneficiaryId = event.beneficiaryId();
    this.originCountry = event.originCountry();
    this.beneficiaryCountry = event.beneficiaryCountry();
    this.channel = event.channel();
    this.schemaVersion = event.schemaVersion();
    this.correlationId = event.correlationId();
    this.qualifyingActivity = false;
    this.ingestedAt = Objects.requireNonNull(ingestedAt, "ingestedAt");
  }

  public TransactionEvent toDomain() {
    return new TransactionEvent(
        eventId,
        transactionId,
        accountId,
        customerId,
        amount,
        currency,
        category,
        occurredAt,
        merchantId,
        location,
        beneficiaryId,
        originCountry,
        beneficiaryCountry,
        channel,
        schemaVersion,
        correlationId);
  }

  public HistoricalTransaction toHistoricalTransaction() {
    return new HistoricalTransaction(
        eventId,
        amount,
        currency,
        category,
        occurredAt,
        qualifyingActivity,
        beneficiaryId,
        channel);
  }

  public String getEventId() {
    return eventId;
  }

  @Override
  public String getId() {
    return eventId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    newEntity = false;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }
}
