package org.azirar.glenn.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.azirar.glenn.models.MonitoredApp;
import org.azirar.glenn.models.StatusCheck;
import org.azirar.glenn.repositories.MonitoredAppRepository;
import org.azirar.glenn.repositories.StatusCheckRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MonitoredAppRepository appRepository;
    private final StatusCheckRepository statusRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${monitoring.interval:10000}")
    private long monitoringInterval;

    // --- GESTION DES APPLICATIONS ---

    public Flux<MonitoredApp> getAllApps() {
        return appRepository.findAll();
    }

    public Mono<MonitoredApp> getAppById(Long id) {
        return appRepository.findById(id);
    }

    public Flux<String> getAllCategories() {
        return appRepository.findAllCategories();
    }

    public Mono<MonitoredApp> saveApp(MonitoredApp app) {
        // Logique de création/mise à jour centralisée
        if (app.getId() == null) {
            app.setActive(true);
            app.setCreatedAt(LocalDateTime.now());
        }
        app.setUpdatedAt(LocalDateTime.now());

        return appRepository.save(app)
                .flatMap(savedApp ->
                        // On déclenche un check immédiat pour ne pas attendre l'intervalle
                        performHealthCheck(savedApp)
                                .flatMap(statusRepository::save)
                                .thenReturn(savedApp)
                );
    }

    public Mono<Void> deleteApp(Long id) {
        return statusRepository.deleteByAppId(id)
                .then(appRepository.deleteById(id));
    }

    public Mono<MonitoredApp> toggleAppActive(Long id) {
        return appRepository.findById(id)
                .flatMap(app -> {
                    app.setActive(!app.getActive());
                    app.setUpdatedAt(LocalDateTime.now());
                    return appRepository.save(app);
                });
    }


    public Mono<StatusCheck> performHealthCheck(MonitoredApp app) {
        long startTime = System.currentTimeMillis();
        return webClientBuilder.build()
                .get()
                .uri(app.getUrl())
                .exchangeToMono(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = response.statusCode().value();

                    // CORRECTION ICI : Vérifie si le code HTTP est dans la liste des statuts acceptés
                    // Si oui, l'app est UP, sinon DOWN
                    boolean isUp = app.getAcceptedStatusesList().contains(statusCode);

                    log.debug("App {} - Code: {} - Accepted: {} - isUp: {}",
                            app.getName(), statusCode, app.getAcceptedStatusesList(), isUp);

                    return Mono.just(buildStatus(app, statusCode, isUp, duration, null));
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    // En cas d'erreur (timeout, connexion refusée, etc.), c'est DOWN
                    log.warn("Health check failed for {}: {}", app.getName(), e.getMessage());
                    return Mono.just(buildStatus(app, 0, false, duration, e.getMessage()));
                });
    }

    private StatusCheck buildStatus(MonitoredApp app, int code, boolean isUp, long ms, String error) {
        return StatusCheck.builder()
                .appId(app.getId())
                .appName(app.getName())
                .statusCode(code)
                .isUp(isUp)
                .responseTime(ms + "ms")
                .errorMessage(error)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    public Flux<StatusCheck> getLatestStatuses() {
        return statusRepository.findLatestStatusForAllApps();
    }

    public Flux<StatusCheck> getAppHistory(Long appId) {
        return statusRepository.findTop10ByAppIdOrderByCheckedAtDesc(appId);
    }

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

    private Flux<StatusCheck> monitoringFlux;

    @EventListener(ApplicationReadyEvent.class)
    public void initMonitoring() {
        log.info("Monitoring démarré avec intervalle de {} ms", monitoringInterval);
        startContinuousMonitoring()
                .subscribe();
    }

    public Flux<StatusCheck> startContinuousMonitoring() {

        if (monitoringFlux != null) {
            return monitoringFlux;
        }

        monitoringFlux = Flux.interval(Duration.ofMillis(monitoringInterval))
                .flatMap(tick -> appRepository.findAll())
                .filter(MonitoredApp::getActive)
                .flatMap(this::performHealthCheck)
                .flatMap(statusRepository::save)
                .doOnNext(statusCheck -> {
                    if (statusCheck.getIsUp()) {
                        log.info("✅ {} is UP (HTTP {})", statusCheck.getAppName(), statusCheck.getStatusCode());
                    } else {
                        log.warn("❌ {} is DOWN (HTTP {})", statusCheck.getAppName(), statusCheck.getStatusCode());
                    }
                })
                .publish()
                .autoConnect(1);

        return monitoringFlux;
    }

    public Flux<String> getDistinctCategories() {
        return appRepository.findAll()
                .map(app -> app.getCategory() == null || app.getCategory().isBlank()
                        ? "Uncategorized"
                        : app.getCategory())
                .distinct()
                .sort();
    }


    public Mono<MonitoredApp> updateApp(Long id, MonitoredApp updatedApp) {
        return appRepository.findById(id)
                .flatMap(existingApp -> {
                    // On met à jour les champs nécessaires
                    existingApp.setName(updatedApp.getName());
                    existingApp.setUrl(updatedApp.getUrl());
                    existingApp.setCategory(updatedApp.getCategory());
                    existingApp.setDescription(updatedApp.getDescription());
                    existingApp.setAcceptedStatuses(updatedApp.getAcceptedStatuses());
                    existingApp.setUpdatedAt(LocalDateTime.now()); //

                    return appRepository.save(existingApp); // Sauvegarde en base
                })
                .flatMap(savedApp ->
                        // On lance un check immédiat pour valider la nouvelle URL
                        performHealthCheck(savedApp)
                                .flatMap(statusRepository::save)
                                .thenReturn(savedApp)
                );
    }

    // Dans MonitoringService.java
    public Mono<MonitoredApp> saveOrUpdateApp(MonitoredApp app) {
        if (app.getId() == null) {
            app.setActive(true);
            app.setCreatedAt(LocalDateTime.now());
            // Initialisation de secours pour éviter le crash Thymeleaf au cas où
            if (app.getAcceptedStatuses() == null) app.setAcceptedStatuses("200");
        }
        app.setUpdatedAt(LocalDateTime.now());

        return appRepository.save(app)
                .flatMap(savedApp ->
                        performHealthCheck(savedApp)
                                .flatMap(statusRepository::save)
                                .thenReturn(savedApp)
                );
    }
}