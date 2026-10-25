package com.siciya.c.fraud.adapter.outbound.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_bootstrap_lock")
public class UserBootstrapLockEntity {
  @Id
  @Column(name = "lock_id", nullable = false)
  private Short lockId;

  protected UserBootstrapLockEntity() {}
}
