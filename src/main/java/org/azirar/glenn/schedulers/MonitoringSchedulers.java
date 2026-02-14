package org.azirar.glenn.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.azirar.glenn.repositories.StatusCheckRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MonitoringSchedulers {

    private final StatusCheckRepository statusRepository;

    @Value("${monitoring.history:5000}")
    private int maxHistoryPerApp;

    /**
     * Nettoie l'historique des checks toutes les heures
     * Garde seulement les N derniers checks PAR APPLICATION
     */
    @Scheduled(fixedRate = 3600000) // Toutes les heures
    public void cleanupOldHistory() {
        log.info("🧹 [SCHEDULER] Starting cleanup of old status checks. Keeping last {} records per application", maxHistoryPerApp);

        statusRepository.findDistinctAppIds()
                .flatMap(this::cleanupForApp)
                .doOnComplete(() -> log.info("✅ Scheduled cleanup completed"))
                .doOnError(error -> log.error("❌ Error during scheduled cleanup: {}", error.getMessage()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    /**
     * Nettoie l'historique pour une application spécifique
     */
    private Mono<Void> cleanupForApp(Long appId) {
        return statusRepository.countByAppId(appId)
                .flatMap(count -> {
                    if (count > maxHistoryPerApp) {
                        long toDelete = count - maxHistoryPerApp;
                        log.info("📊 App {} has {} checks, keeping {}, deleting {}",
                                appId, count, maxHistoryPerApp, toDelete);

                        return statusRepository.deleteOldChecksForApp(appId, maxHistoryPerApp)
                                .doOnNext(deleted -> log.debug("Deleted {} old checks for app {}", deleted, appId))
                                .then();
                    } else {
                        log.debug("App {} has {} checks, within limit of {}", appId, count, maxHistoryPerApp);
                        return Mono.empty();
                    }
                });
    }

    /**
     * Nettoie l'historique toutes les 6 heures (version plus espacée)
     */
    @Scheduled(cron = "0 0 */6 * * ?") // Toutes les 6 heures
    public void cleanupOldHistoryCron() {
        log.info("🧹 [SCHEDULER CRON] Starting cleanup of old status checks");
        cleanupOldHistory();
    }

    /**
     * Méthode pour déclencher un nettoyage manuel pour toutes les apps
     */
    public Mono<Long> triggerManualCleanup() {
        log.info("🧹 [MANUAL] Manual cleanup triggered");

        return statusRepository.findDistinctAppIds()
                .flatMap(appId ->
                        statusRepository.countByAppId(appId)
                                .flatMap(count -> {
                                    if (count > maxHistoryPerApp) {
                                        return statusRepository.deleteOldChecksForApp(appId, maxHistoryPerApp)
                                                .map(deleted -> {
                                                    log.info("✅ Manually deleted {} old checks for app {}", deleted, appId);
                                                    return 1L;
                                                });
                                    }
                                    return Mono.just(0L);
                                })
                )
                .count();
    }

    /**
     * Récupère les statistiques actuelles de la base par application
     */
    public Mono<Long> getTotalChecksCount() {
        return statusRepository.countAll();
    }

    public Mono<Long> getChecksCountForApp(Long appId) {
        return statusRepository.countByAppId(appId);
    }

    public int getMaxHistoryPerApp() {
        return maxHistoryPerApp;
    }
}