package org.azirar.glenn.repositories;

import org.azirar.glenn.models.StatusCheck;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface StatusCheckRepository extends R2dbcRepository<StatusCheck, Long> {

    Flux<StatusCheck> findTop100ByAppIdOrderByCheckedAtDesc(Long appId);

    @Query("SELECT * FROM status_checks WHERE checked_at > DATEADD('MINUTE', -30, CURRENT_TIMESTAMP()) ORDER BY checked_at DESC")
    Flux<StatusCheck> findLast30Minutes();

    @Query("SELECT s1.* FROM status_checks s1 WHERE s1.checked_at = " +
            "(SELECT MAX(s2.checked_at) FROM status_checks s2 WHERE s2.app_id = s1.app_id)")
    Flux<StatusCheck> findLatestStatusForAllApps();

    Mono<Void> deleteByAppId(Long appId);

    Flux<StatusCheck> findByAppIdAndCheckedAtAfter(Long appId, LocalDateTime cutoff);
    // Récupérer tous les app_ids distincts
    @Query("SELECT DISTINCT app_id FROM status_checks")
    Flux<Long> findDistinctAppIds();

    // Compter les checks par application
    @Query("SELECT COUNT(*) FROM status_checks WHERE app_id = :appId")
    Mono<Long> countByAppId(Long appId);

    // Supprimer les anciens checks pour une application spécifique
    @Modifying
    @Query("DELETE FROM status_checks WHERE app_id = :appId AND id NOT IN (" +
            "SELECT id FROM status_checks WHERE app_id = :appId " +
            "ORDER BY checked_at DESC LIMIT :keepCount)")
    Mono<Integer> deleteOldChecksForApp(Long appId, int keepCount);

    // Compter total des checks
    @Query("SELECT COUNT(*) FROM status_checks")
    Mono<Long> countAll();
}