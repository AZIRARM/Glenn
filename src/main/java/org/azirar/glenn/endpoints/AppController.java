package org.azirar.glenn.endpoints;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.azirar.glenn.handlers.MonitoringService;
import org.azirar.glenn.models.MonitoredApp;
import org.azirar.glenn.models.StatusCheck;
import org.azirar.glenn.repositories.MonitoredAppRepository;
import org.azirar.glenn.repositories.StatusCheckRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AppController {

    private final MonitoredAppRepository appRepository;
    private final StatusCheckRepository statusRepository;
    private final MonitoringService monitoringService;

    private static final List<Integer> DEFAULT_STATUS_CODES = Arrays.asList(
            200, 201, 202, 204, 301, 302, 304, 400, 401, 403, 404, 500
    );

    // Dashboard live
    @GetMapping("/")
    public Mono<String> index(Model model) {
        return appRepository.findAll() // Récupère les apps depuis R2DBC
                .collectList()
                .flatMap(apps -> {
                    model.addAttribute("apps", apps); // Ce que vous avez déjà

                    // Récupère les derniers statuts via le service
                    return monitoringService.getLatestStatuses()
                            .collectList()
                            .map(statuses -> {
                                // On crée une Map pour un accès rapide par ID dans le HTML
                                java.util.Map<Long, StatusCheck> statusMap = new java.util.HashMap<>();
                                for (StatusCheck s : statuses) {
                                    statusMap.put(s.getAppId(), s);
                                }
                                model.addAttribute("latestStatuses", statusMap);
                                model.addAttribute("currentTime", java.time.LocalDateTime.now().toString());
                                return "index";
                            });
                });
    }


    // Formulaire d'ajout
    @GetMapping("/add")
    public Mono<String> showAddForm(Model model) {
        model.addAttribute("monitoredApp", new MonitoredApp());
        model.addAttribute("acceptedStatusOptions", DEFAULT_STATUS_CODES);

        return appRepository.findAllCategories()
                .collectList()
                .map(categories -> {
                    model.addAttribute("categories", categories);
                    return "add-app";
                });
    }

    @PostMapping("/apps/save")
    public Mono<String> addApplication(@ModelAttribute("monitoredApp") MonitoredApp app,
                                       BindingResult result,
                                       Model model) {

        // 1. Log direct pour voir si on entre
        System.out.println("=== TENTATIVE D'AJOUT ===");

        // 2. Vérification des erreurs de conversion (C'est là que ton 303 se cache)
        if (result.hasErrors()) {
            System.out.println("Nombre d'erreurs : " + result.getErrorCount());
            result.getAllErrors().forEach(error -> {
                System.out.println("Erreur de binding : " + error.getObjectName() + " - " + error.getDefaultMessage());
            });

            // On recharge les données nécessaires à la vue
            model.addAttribute("acceptedStatusOptions", DEFAULT_STATUS_CODES);
            return appRepository.findAllCategories()
                    .collectList()
                    .map(categories -> {
                        model.addAttribute("categories", categories);
                        return "add-app"; // On retourne au formulaire pour voir les erreurs
                    });
        }

        // 3. Log des données reçues
        System.out.println("App reçue : " + app.getName() + " | URL : " + app.getUrl());

        app.setActive(true);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());

        return appRepository.save(app)
                .doOnError(e -> System.err.println("ERREUR BDD : " + e.getMessage()))
                .map(saved -> "redirect:/");
    }


    // Détails d'une application
    @GetMapping("/app/{id}")
    public Mono<String> appDetails(@PathVariable Long id, Model model) {
        return appRepository.findById(id)
                .flatMap(app -> {
                    model.addAttribute("app", app);
                    return monitoringService.getAppHistory(id)
                            .collectList()
                            .map(history -> {
                                model.addAttribute("history", history);
                                return "details";
                            });
                });
    }

    // Activer / Désactiver
    @PostMapping("/app/{id}/toggle")
    public Mono<String> toggleApp(@PathVariable Long id) {
        return appRepository.findById(id)
                .flatMap(app -> {
                    app.setActive(!app.getActive());
                    app.setUpdatedAt(LocalDateTime.now());
                    return appRepository.save(app);
                })
                .thenReturn("redirect:/");
    }

    // Supprimer
    @PostMapping("/app/{id}/delete")
    public Mono<String> deleteApp(@PathVariable Long id) {
        return statusRepository.deleteByAppId(id)
                .then(appRepository.deleteById(id))
                .thenReturn("redirect:/");
    }

    // SSE live-status
    @GetMapping(value = "/api/live-status", produces = "text/event-stream")
    @ResponseBody
    public Flux<StatusCheck> liveStatus() {
        return monitoringService.startContinuousMonitoring();
    }

    // Statistiques
    @GetMapping("/app/{id}/stats")
    @ResponseBody
    public Mono<AppStats> getStats(@PathVariable Long id) {
        return Mono.zip(
                monitoringService.getUptimePercentage(id, 24),
                monitoringService.getUptimePercentage(id, 168),
                appRepository.findById(id)
        ).map(tuple -> new AppStats(
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3().getName()
        ));
    }

    @GetMapping("/debug")
    @ResponseBody
    public String debugPath() {
        var indexUrl = getClass().getResource("/templates/index.html");
        return "L'URL du fichier index est : " + (indexUrl != null ? indexUrl.toString() : "INTROUVABLE");
    }
    @PostMapping("/ultra-debug")
    public Mono<String> debug() {
        System.out.println("SI CA NE S'ARRETE PAS ICI, LE PROBLEME EST EXTERNE AU CODE");
        return Mono.just("Veni Vidi Vici");
    }
    // DTO JSON pour stats
    public record AppStats(double uptime24h, double uptime7d, String appName) {
    }
}
