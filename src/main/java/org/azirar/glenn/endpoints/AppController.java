package org.azirar.glenn.endpoints;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.azirar.glenn.handlers.MonitoringService;
import org.azirar.glenn.models.MonitoredApp;
import org.azirar.glenn.models.StatusCheck;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AppController {

    private final MonitoringService monitoringService;

    private static final List<Integer> DEFAULT_STATUS_CODES = Arrays.asList(
            200, 201, 202, 204, 301, 302, 304, 400, 401, 403, 404, 500
    );

    @GetMapping("/")
    public Mono<String> index(Model model) {
        return monitoringService.getAllApps()
                .collectList()
                .flatMap(apps -> {
                    model.addAttribute("apps", apps);
                    return monitoringService.getLatestStatuses()
                            .collectList()
                            .map(statuses -> {
                                java.util.Map<Long, StatusCheck> statusMap = statuses.stream()
                                        .collect(java.util.stream.Collectors.toMap(StatusCheck::getAppId, s -> s));
                                model.addAttribute("latestStatuses", statusMap);
                                model.addAttribute("currentTime", java.time.LocalDateTime.now().toString());
                                return "index";
                            });
                });
    }

    // 1. Route pour l'AJOUT
    @GetMapping("/app/add")
    public Mono<String> showAddForm(Model model) {
        MonitoredApp newApp = new MonitoredApp();
        newApp.setAcceptedStatuses("200"); // Empêche l'erreur de parsing Thymeleaf
        model.addAttribute("monitoredApp", newApp);
        model.addAttribute("acceptedStatusOptions", DEFAULT_STATUS_CODES);

        return monitoringService.getAllCategories()
                .collectList()
                .map(categories -> {
                    model.addAttribute("categories", categories);
                    return "add-app"; // Doit correspondre à votre fichier add-app.html
                });
    }

    // 2. Route pour l'ÉDITION
    @GetMapping("/app/edit/{id}")
    public Mono<String> showEditForm(@PathVariable Long id, Model model) {
        return monitoringService.getAppById(id)
                .flatMap(app -> {
                    model.addAttribute("monitoredApp", app);
                    model.addAttribute("acceptedStatusOptions", DEFAULT_STATUS_CODES);

                    return monitoringService.getAllCategories()
                            .collectList()
                            .map(categories -> {
                                model.addAttribute("categories", categories);
                                return "edit-app"; // Utilise edit-app.html
                            });
                });
    }


    @PostMapping("/apps/save")
    public Mono<String> saveApplication(@ModelAttribute("monitoredApp") MonitoredApp app,
                                        BindingResult result,
                                        Model model) {
        if (result.hasErrors()) {
            model.addAttribute("acceptedStatusOptions", DEFAULT_STATUS_CODES);
            return monitoringService.getAllCategories().collectList().map(c -> {
                model.addAttribute("categories", c);
                // Retourne au bon template selon si c'est une création ou édition
                return (app.getId() == null) ? "add-app" : "edit-app";
            });
        }

        return monitoringService.saveApp(app)
                .thenReturn("redirect:/");
    }


    /*@GetMapping("/app/{id}")
    public Mono<String> appDetails(@PathVariable Long id, Model model) {
        return monitoringService.getAppById(id)
                .flatMap(app -> {
                    model.addAttribute("app", app);
                    return monitoringService.getAppHistory(id)
                            .collectList()
                            .map(history -> {
                                model.addAttribute("history", history);
                                log.info("------------> History : {} ", history);
                                return "details";
                            });
                });
    }*/
    @GetMapping(value = "/app/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> appDetails(@PathVariable Long id, Model model) {
        return monitoringService.getAppById(id)
                .flatMap(app -> {
                    model.addAttribute("app", app);

                    // Récupérer l'historique et le convertir en liste
                    return monitoringService.getAppHistory(id)
                            .collectList()
                            .flatMap(history -> {
                                model.addAttribute("history", history);

                                // Calculer les statistiques
                                return calculateStats(history)
                                        .map(stats -> {
                                            model.addAttribute("stats", stats);
                                            log.info("------------> History for app {}: {} checks", id, history.size());
                                            log.info("------------> Stats: {}", stats);
                                            return "details";
                                        });
                            });
                });
    }

    private Mono<Map<String, Object>> calculateStats(List<StatusCheck> history) {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();

            if (history == null || history.isEmpty()) {
                stats.put("totalChecks", 0);
                stats.put("successRate", 0.0);
                stats.put("avgResponseTime", "N/A");
                stats.put("lastStatus", "N/A");
                stats.put("lastTime", "N/A");
                return stats;
            }

            // Total des checks
            int total = history.size();
            stats.put("totalChecks", total);

            // Taux de succès
            long successCount = history.stream().filter(StatusCheck::getIsUp).count();
            double successRate = (successCount * 100.0) / total;
            stats.put("successRate", Math.round(successRate * 10) / 10.0); // Arrondi à 1 décimale

            // Temps de réponse moyen (ignorer les valeurs > 5000ms qui sont des erreurs)
            double avgResponse = history.stream()
                    .mapToInt(check -> {
                        String responseStr = check.getResponseTime();
                        if (responseStr != null) {
                            try {
                                // Extraire le nombre de "13ms"
                                String numeric = responseStr.replaceAll("[^0-9]", "");
                                if (!numeric.isEmpty()) {
                                    int val = Integer.parseInt(numeric);
                                    return val < 5000 ? val : 0; // Ignorer les timeouts
                                }
                            } catch (NumberFormatException e) {
                                // Ignorer
                            }
                        }
                        return 0;
                    })
                    .filter(val -> val > 0)
                    .average()
                    .orElse(0.0);

            stats.put("avgResponseTime", avgResponse > 0 ? Math.round(avgResponse) + "ms" : "N/A");

            // Dernier statut
            StatusCheck lastCheck = history.get(0);
            stats.put("lastStatus", lastCheck.getIsUp() ? "UP" : "DOWN");
            stats.put("lastStatusCode", lastCheck.getStatusCode());
            stats.put("lastResponseTime", lastCheck.getResponseTime());
            stats.put("lastTime", lastCheck.getCheckedAt() != null ?
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(lastCheck.getCheckedAt()) : "N/A");

            // Préparer les données pour le graphique (20 derniers checks)
            List<StatusCheck> recentChecks = history.stream().limit(20).collect(Collectors.toList());
            stats.put("chartChecks", recentChecks);

            return stats;
        });
    }
    @PostMapping("/app/{id}/toggle")
    public Mono<String> toggleApp(@PathVariable Long id) {
        return monitoringService.toggleAppActive(id)
                .thenReturn("redirect:/");
    }

    @PostMapping("/app/{id}/delete")
    public Mono<String> deleteApp(@PathVariable Long id) {
        return monitoringService.deleteApp(id)
                .thenReturn("redirect:/");
    }

    @GetMapping(value = "/api/live-status", produces = "text/event-stream")
    @ResponseBody
    public Flux<StatusCheck> liveStatus() {
        return monitoringService.startContinuousMonitoring();
    }

    @GetMapping("/app/{id}/stats")
    @ResponseBody
    public Mono<AppStats> getStats(@PathVariable Long id) {
        return Mono.zip(
                monitoringService.getUptimePercentage(id, 24),
                monitoringService.getUptimePercentage(id, 168),
                monitoringService.getAppById(id)
        ).map(tuple -> new AppStats(tuple.getT1(), tuple.getT2(), tuple.getT3().getName()));
    }

    public record AppStats(double uptime24h, double uptime7d, String appName) {
    }
}