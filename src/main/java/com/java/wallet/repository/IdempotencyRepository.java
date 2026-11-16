package com.java.wallet.repository;

import com.java.wallet.model.entity.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Modifying
    @Query("""
            insert into IdempotencyRecord (idempotencyKey, requestHash, status, lockedAt, createdDate)
            values (:idempotencyKey, :requestHash, com.java.wallet.model.datatype.IdempotencyStatus.IN_PROGRESS, :now, :now)
            on conflict do nothing
            """)
    int insertIfAbsent(@Param("idempotencyKey") UUID idempotencyKey, @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);

    Optional<IdempotencyRecord> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotencyRecord r where r.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyRecord> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") UUID idempotencyKey);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.idempotencyKey = :idempotencyKey")
    void deleteByIdempotencyKey(@Param("idempotencyKey") UUID idempotencyKey);
}
