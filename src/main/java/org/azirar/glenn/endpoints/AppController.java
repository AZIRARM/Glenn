package org.azirar.glenn.endpoints;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.azirar.glenn.handlers.MonitoringService;
import org.azirar.glenn.models.MonitoredApp;
import org.azirar.glenn.models.StatusCheck;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AppController {

    private final MonitoringService monitoringService;

    @GetMapping("/")
    public Mono<String> index(Model model) {
        return Mono.zip(
                monitoringService.getAllApps().collectList(),
                monitoringService.getLatestStatuses().collectList(), // CHANGEMENT: collectList au lieu de collectMap
                monitoringService.getDistinctCategories().collectList()
        ).map(tuple -> {
            List<MonitoredApp> apps = tuple.getT1();
            List<StatusCheck> latestStatusesList = tuple.getT2();
            List<String> categories = tuple.getT3();

            // CORRECTION: Créer une Map manuellement en gérant les doublons
            Map<Long, StatusCheck> latestStatuses = new HashMap<>();
            for (StatusCheck status : latestStatusesList) {
                // Si la clé n'existe pas déjà, on l'ajoute
                // Cela garde la première occurrence (la plus récente normalement)
                latestStatuses.putIfAbsent(status.getAppId(), status);
            }

            model.addAttribute("apps", apps);
            model.addAttribute("latestStatuses", latestStatuses);
            model.addAttribute("categories", categories);
            model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            log.info("Index page loaded - Total apps: {}, Latest statuses: {}", apps.size(), latestStatuses.size());
            return "index";
        });
    }

    @GetMapping("/supervision")
    public Mono<String> supervisionView(Model model) {
        return Mono.zip(
                monitoringService.getAllApps().collectList(),
                monitoringService.getLatestStatuses().collectList() // CHANGEMENT: collectList
        ).map(tuple -> {
            List<MonitoredApp> apps = tuple.getT1();
            List<StatusCheck> latestStatusesList = tuple.getT2();

            // CORRECTION: Créer une Map manuellement
            Map<Long, StatusCheck> latestStatuses = new HashMap<>();
            for (StatusCheck status : latestStatusesList) {
                latestStatuses.putIfAbsent(status.getAppId(), status);
            }

            model.addAttribute("apps", apps);
            model.addAttribute("latestStatuses", latestStatuses);
            model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            log.info("Supervision view accessed - Total apps: {}", apps.size());
            return "supervision";
        });
    }

    @GetMapping("/app/{id}")
    public Mono<String> appDetails(@PathVariable Long id, Model model) {
        return monitoringService.getAppById(id)
                .flatMap(app -> {
                    model.addAttribute("app", app);
                    return monitoringService.getAppHistory(id)
                            .collectList()
                            .map(history -> {
                                model.addAttribute("history", history);

                                // Calculer les statistiques
                                Map<String, Object> stats = calculateStats(history);
                                model.addAttribute("stats", stats);

                                log.info("Details for app {}: {} checks", id, history.size());
                                return "details";
                            });
                });
    }


    @GetMapping("/app/{id}/history")
    @ResponseBody
    public Mono<Map<String, Object>> getAppHistory(@PathVariable Long id) {
        return monitoringService.getAppById(id)
                .flatMap(app -> monitoringService.getAppHistory(id)
                        .collectList()
                        .map(history -> {
                            Map<String, Object> response = new HashMap<>();
                            response.put("history", history);
                            response.put("stats", calculateStats(history));
                            return response;
                        }));
    }

    private Map<String, Object> calculateStats(List<StatusCheck> history) {
        Map<String, Object> stats = new HashMap<>();

        if (history == null || history.isEmpty()) {
            stats.put("totalChecks", 0);
            stats.put("successRate", 0.0);
            stats.put("avgResponseTime", "N/A");
            stats.put("lastStatus", "N/A");
            stats.put("lastTime", "N/A");
            return stats;
        }

        int total = history.size();
        stats.put("totalChecks", total);

        long successCount = history.stream().filter(StatusCheck::getIsUp).count();
        double successRate = (successCount * 100.0) / total;
        stats.put("successRate", Math.round(successRate * 10) / 10.0);

        double avgResponse = history.stream()
                .mapToInt(check -> {
                    String responseStr = check.getResponseTime();
                    if (responseStr != null) {
                        try {
                            String numeric = responseStr.replaceAll("[^0-9]", "");
                            if (!numeric.isEmpty()) {
                                int val = Integer.parseInt(numeric);
                                return val < 5000 ? val : 0;
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

        StatusCheck lastCheck = history.get(0);
        stats.put("lastStatus", lastCheck.getIsUp() ? "UP" : "DOWN");
        stats.put("lastTime", lastCheck.getCheckedAt() != null ?
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(lastCheck.getCheckedAt()) : "N/A");

        return stats;
    }


    @GetMapping("/app/add")
    public Mono<String> addAppForm(Model model) {
        return monitoringService.getDistinctCategories().collectList()
                .map(categories -> {
                    MonitoredApp newApp = new MonitoredApp();
                    newApp.setAcceptedStatuses("200,201,202,203,204,205,206");

                    model.addAttribute("monitoredApp", newApp);
                    model.addAttribute("categories", categories);
                    List<Integer> statusOptions = List.of(
                            200,201,202,203,204,205,206,
                            300,301,302,303,304,305,307,308,
                            400,401,403,404,
                            500,502,503
                    );

                    List<Integer> sortedStatusOptions = new ArrayList<>(statusOptions);
                    Collections.sort(sortedStatusOptions);
                    model.addAttribute("acceptedStatusOptions", sortedStatusOptions);

                    return "add-app";  // Note: retourne "add-app" pas "add"
                });
    }

    @GetMapping("/app/edit/{id}")
    public Mono<String> editAppForm(@PathVariable Long id, Model model) {
        return Mono.zip(
                monitoringService.getAppById(id),
                monitoringService.getDistinctCategories().collectList()
        ).map(tuple -> {
            MonitoredApp app = tuple.getT1();
            List<String> categories = tuple.getT2();

            model.addAttribute("monitoredApp", app);
            model.addAttribute("categories", categories);
            List<Integer> statusOptions = List.of(
                    200,201,202,203,204,205,206,
                    300,301,302,303,304,307,308,
                    400,401,403,404,
                    500,502,503
            );
            List<Integer> sortedStatusOptions = new ArrayList<>(statusOptions);
            Collections.sort(sortedStatusOptions);
            model.addAttribute("acceptedStatusOptions", sortedStatusOptions);
            return "edit-app";
        });
    }

    @PostMapping("/app/save")
    public Mono<String> saveApp(@ModelAttribute MonitoredApp app) {
        return monitoringService.saveApp(app)
                .thenReturn("redirect:/");
    }

    @PostMapping("/app/update/{id}")
    public Mono<String> updateApp(@PathVariable Long id, @ModelAttribute MonitoredApp app) {
        app.setId(id);
        return monitoringService.saveApp(app)
                .thenReturn("redirect:/");
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