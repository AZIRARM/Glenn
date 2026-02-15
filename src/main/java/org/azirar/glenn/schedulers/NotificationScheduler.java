package org.azirar.glenn.schedulers;

import lombok.extern.slf4j.Slf4j;
import org.azirar.glenn.models.MonitoredApp;
import org.azirar.glenn.models.StatusCheck;
import org.azirar.glenn.repositories.MonitoredAppRepository;
import org.azirar.glenn.repositories.StatusCheckRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NotificationScheduler {

    private final MonitoredAppRepository appRepository;
    private final StatusCheckRepository statusRepository;
    private final WebClient webClient;

    @Value("${notification.reminder-interval:300000}") // 5 minutes par défaut
    private long reminderIntervalMs;

    public NotificationScheduler(MonitoredAppRepository appRepository,
                                 StatusCheckRepository statusRepository) {
        this.appRepository = appRepository;
        this.statusRepository = statusRepository;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Traite les notifications toutes les 30 secondes
     */
    @Scheduled(fixedRate = 30000)
    public void processNotifications() {
        log.debug("🔄 Démarrage du traitement des notifications");

        appRepository.findAll()
                .filter(app -> app.getWebhookUrl() != null && !app.getWebhookUrl().isBlank())
                .flatMap(this::evaluateNotificationNeeds)
                .doOnComplete(() -> log.debug("✅ Traitement des notifications terminé"))
                .doOnError(error -> log.error("❌ Erreur lors du traitement des notifications: {}", error.getMessage()))
                .subscribe();
    }

    /**
     * Évalue si une notification doit être envoyée pour une application
     */
    private Mono<Void> evaluateNotificationNeeds(MonitoredApp app) {
        log.debug("🔍 Évaluation des besoins de notification pour: {}", app.getName());

        return getLatestStatus(app.getId())
                .flatMap(currentStatus -> {
                    boolean isCurrentlyUp = currentStatus.getIsUp();

                    // Cas 1: Changement d'état (alerte immédiate)
                    boolean statusChanged = (isCurrentlyUp != app.getLastStatusWasUp());

                    // Cas 2: Toujours en erreur avec rappel périodique
                    boolean shouldSendReminder = shouldSendReminder(app, isCurrentlyUp);

                    if (statusChanged) {
                        log.info("📢 Changement d'état détecté pour {}: {} -> {}",
                                app.getName(),
                                app.getLastStatusWasUp() ? "UP" : "DOWN",
                                isCurrentlyUp ? "UP" : "DOWN");
                        return sendWebhook(app, currentStatus);
                    } else if (shouldSendReminder) {
                        log.info("⏰ Envoi d'un rappel pour {} (DOWN depuis 5 minutes)", app.getName());
                        return sendWebhook(app, currentStatus);
                    }

                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("⏭️ Aucune notification nécessaire pour {}", app.getName());
                    return Mono.empty();
                }));
    }

    /**
     * Vérifie si un rappel doit être envoyé (application DOWN depuis 5 minutes)
     */
    private boolean shouldSendReminder(MonitoredApp app, boolean isCurrentlyUp) {
        if (isCurrentlyUp) {
            return false; // Pas de rappel si c'est UP
        }

        if (app.getLastNotificationSentAt() == null) {
            return false; // Première notification déjà gérée par statusChanged
        }

        LocalDateTime reminderThreshold = LocalDateTime.now().minusMinutes(5);
        return app.getLastNotificationSentAt().isBefore(reminderThreshold);
    }

    /**
     * Récupère le dernier statut pour une application
     */
    private Mono<StatusCheck> getLatestStatus(Long appId) {
        return statusRepository.findLatestStatusForAllApps()
                .filter(status -> status.getAppId().equals(appId))
                .next();
    }

    /**
     * Envoie une notification via webhook
     */
    private Mono<Void> sendWebhook(MonitoredApp app, StatusCheck status) {
        String message = formatMessage(app, status);
        Object payload = createPayload(app.getWebhookUrl(), message, status.getIsUp());

        log.info("📤 Envoi de notification vers {} pour {}", app.getWebhookUrl(), app.getName());

        return webClient.post()
                .uri(app.getWebhookUrl())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.debug("✅ Réponse webhook: {}", response))
                .then(updateAppAfterNotification(app, status))
                .onErrorResume(e -> {
                    log.error("❌ Erreur webhook pour {}: {}", app.getName(), e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Met à jour l'application après l'envoi d'une notification
     */
    private Mono<Void> updateAppAfterNotification(MonitoredApp app, StatusCheck status) {
        app.setLastStatusWasUp(status.getIsUp());
        app.setLastNotificationSentAt(LocalDateTime.now());
        return appRepository.save(app).then();
    }


    private String formatMessage(MonitoredApp app, StatusCheck status) {
        String emoji = status.getIsUp() ? "✅" : "🔴";
        String state = status.getIsUp() ? "IS NOW OPERATIONAL" : "IS DOWN";
        String errorInfo = status.getErrorMessage() != null ?
                String.format("\n> ❌ Error: %s", status.getErrorMessage()) : "";

        return String.format(
                "%s **%s** %s\n" +
                        "> 📍 URL: %s\n" +
                        "> 📊 HTTP Code: %s\n" +
                        "> ⏱️ Response Time: %s\n" +
                        "> 🕒 Detected at: %s%s",
                emoji, app.getName(), state,
                app.getUrl(),
                status.getStatusCode(),
                status.getResponseTime(),
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(status.getCheckedAt()),
                errorInfo
        );
    }

    /**
     * Creates payload adapted to the target platform
     */
    private Object createPayload(String webhookUrl, String message, boolean isUp) {
        String url = webhookUrl.toLowerCase();

        if (url.contains("teams") || url.contains("office.com") || url.contains("dynamics.com")) {
            return createTeamsPayload(message, isUp);
        } else if (url.contains("discord")) {
            return createDiscordPayload(message, isUp);
        } else {
            // Default Slack/Mattermost format
            return createSlackPayload(message, isUp);
        }
    }

    /**
     * Payload for Slack / Mattermost
     */
    private Map<String, Object> createSlackPayload(String message, boolean isUp) {
        String color = isUp ? "good" : "danger";
        return Map.of(
                "attachments", List.of(Map.of(
                        "color", color,
                        "text", message,
                        "mrkdwn_in", List.of("text")
                ))
        );
    }

    /**
     * Payload for Discord
     */
    private Map<String, Object> createDiscordPayload(String message, boolean isUp) {
        int color = isUp ? 0x00FF00 : 0xFF0000;
        return Map.of(
                "embeds", List.of(Map.of(
                        "description", message,
                        "color", color,
                        "timestamp", java.time.Instant.now().toString()
                ))
        );
    }

    /**
     * Payload for Microsoft Teams (Adaptive Card)
     */
    private Map<String, Object> createTeamsPayload(String message, boolean isUp) {
        String color = isUp ? "good" : "attention";
        return Map.of(
                "type", "message",
                "attachments", List.of(Map.of(
                        "contentType", "application/vnd.microsoft.card.adaptive",
                        "content", Map.of(
                                "type", "AdaptiveCard",
                                "$schema", "http://adaptivecards.io/schemas/adaptive-card.json",
                                "version", "1.4",
                                "body", List.of(
                                        Map.of(
                                                "type", "TextBlock",
                                                "text", message,
                                                "wrap", true,
                                                "size", "Medium",
                                                "weight", "Bolder",
                                                "color", color
                                        )
                                )
                        )
                ))
        );
    }
}