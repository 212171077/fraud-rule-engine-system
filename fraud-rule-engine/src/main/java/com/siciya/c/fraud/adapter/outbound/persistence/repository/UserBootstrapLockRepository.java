package com.siciya.c.fraud.adapter.outbound.persistence.repository;

import com.siciya.c.fraud.adapter.outbound.persistence.entity.UserBootstrapLockEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserBootstrapLockRepository extends JpaRepository<UserBootstrapLockEntity, Short> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select bootstrapLock from UserBootstrapLockEntity bootstrapLock where bootstrapLock.lockId = 1")
  Optional<UserBootstrapLockEntity> findSingletonForUpdate();
}
