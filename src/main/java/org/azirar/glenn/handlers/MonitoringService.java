package org.azirar.glenn.handlers;

import  org.azirar.glenn.models.MonitoredApp;
import  org.azirar.glenn.models.StatusCheck;
import  org.azirar.glenn.repositories.MonitoredAppRepository;
import  org.azirar.glenn.repositories.StatusCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MonitoredAppRepository appRepository;
    private final StatusCheckRepository statusRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${monitoring.interval:30000}")
    private long monitoringInterval;

    @Value("${monitoring.timeout:5000}")
    private int timeout;

    // Flux continu de statuts pour le live
    public Flux<StatusCheck> startContinuousMonitoring() {
        return Flux.interval(Duration.ofMillis(monitoringInterval))
                .flatMap(tick -> checkAllApplications())
                .flatMap(status -> {
                    // Sauvegarde et broadcast
                    return statusRepository.save(status)
                            .doOnNext(saved -> log.debug("Status saved for app: {}", saved.getAppName()))
                            .onErrorResume(e -> {
                                log.error("Error saving status: {}", e.getMessage());
                                return Mono.just(status);
                            });
                })
                .share(); // Partage le flux entre plusieurs subscribers
    }

    // Vérifie toutes les applications actives
    private Flux<StatusCheck> checkAllApplications() {
        return appRepository.findByActiveTrue()
                .flatMap(this::checkSingleApplication)
                .doOnComplete(() -> log.debug("Completed monitoring cycle"));
    }

    // Vérifie une URL individuelle
    private Mono<StatusCheck> checkSingleApplication(MonitoredApp app) {
        log.debug("Checking URL: {}", app.getUrl());
        long startTime = System.currentTimeMillis();

        return webClientBuilder.build()
                .get()
                .uri(app.getUrl())
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    long responseTime = System.currentTimeMillis() - startTime;
                    int statusCode = response.getStatusCode().value();
                    boolean isUp = (statusCode == 200) || app.getAcceptedStatusesList().contains(statusCode);
                    log.info("---------------> App: {}, status codes: {}", app.getName(), statusCode);
                    return StatusCheck.builder()
                            .appId(app.getId())
                            .appName(app.getName())
                            .statusCode(statusCode)
                            .isUp(isUp)
                            .responseTime(responseTime + "ms")
                            .checkedAt(LocalDateTime.now())
                            .build();
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    long responseTime = System.currentTimeMillis() - startTime;
                    int statusCode = e.getStatusCode().value();
                    boolean isUp = (statusCode == 200) || app.getAcceptedStatusesList().contains(statusCode);

                    return Mono.just(StatusCheck.builder()
                            .appId(app.getId())
                            .appName(app.getName())
                            .statusCode(statusCode)
                            .isUp(isUp)
                            .responseTime(responseTime + "ms")
                            .errorMessage(e.getStatusText())
                            .checkedAt(LocalDateTime.now())
                            .build());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Error checking {}: {}", app.getUrl(), e.getMessage());
                    return Mono.just(StatusCheck.builder()
                            .appId(app.getId())
                            .appName(app.getName())
                            .statusCode(0)
                            .isUp(false)
                            .responseTime("Timeout")
                            .errorMessage(e.getMessage() != null ?
                                    e.getMessage().substring(0, Math.min(100, e.getMessage().length())) :
                                    "Connection failed")
                            .checkedAt(LocalDateTime.now())
                            .build());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // Récupère les derniers statuts pour toutes les apps
    public Flux<StatusCheck> getLatestStatuses() {
        return statusRepository.findLatestStatusForAllApps();
    }

    // Historique d'une application
    public Flux<StatusCheck> getAppHistory(Long appId) {
        return statusRepository.findTop10ByAppIdOrderByCheckedAtDesc(appId);
    }

    // Statistiques de disponibilité
    public Mono<Double> getUptimePercentage(Long appId, int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);

        return statusRepository.findByAppIdAndCheckedAtAfter(appId, cutoff)
                .collectList()
                .map(checks -> {
                    if (checks.isEmpty()) return 100.0;
                    long upCount = checks.stream().filter(StatusCheck::getIsUp).count();
                    return (upCount * 100.0) / checks.size();
                });
    }
}