package org.azirar.glenn.repositories;

import org.azirar.glenn.models.StatusCheck;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface StatusCheckRepository extends R2dbcRepository<StatusCheck, Long> {

    Flux<StatusCheck> findTop10ByAppIdOrderByCheckedAtDesc(Long appId);

    @Query("SELECT * FROM status_checks WHERE checked_at > DATEADD('MINUTE', -30, CURRENT_TIMESTAMP()) ORDER BY checked_at DESC")
    Flux<StatusCheck> findLast30Minutes();

    @Query("SELECT s1.* FROM status_checks s1 WHERE s1.checked_at = " +
            "(SELECT MAX(s2.checked_at) FROM status_checks s2 WHERE s2.app_id = s1.app_id)")
    Flux<StatusCheck> findLatestStatusForAllApps();

    Mono<Void> deleteByAppId(Long appId);

    Flux<StatusCheck> findByAppIdAndCheckedAtAfter(Long appId, LocalDateTime cutoff);
}