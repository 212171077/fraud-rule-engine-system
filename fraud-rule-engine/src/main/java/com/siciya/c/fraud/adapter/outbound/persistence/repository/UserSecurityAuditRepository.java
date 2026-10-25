package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.UserSecurityAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityAuditRepository extends JpaRepository<UserSecurityAuditEntity, Long> {}
