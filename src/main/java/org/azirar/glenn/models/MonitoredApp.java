package org.azirar.glenn.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("monitored_apps")
public class MonitoredApp {

    @Id
    private Long id;

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    private String category;

    private String description;

    @NotBlank(message = "L'URL est obligatoire")
    // Pattern assoupli pour accepter http, https et les streams (tcp, postgres, etc.)
    @Pattern(regexp = "^(http|https|tcp|postgres|mongodb|ssh)://.*$", message = "URL invalide")
    private String url;

    @NotNull(message = "Les statuts acceptés sont obligatoires")
    private String acceptedStatuses;

    @Builder.Default
    private Boolean active = true;

    // --- NOUVEAUX CHAMPS POUR NOTIFICATIONS ---

    private String webhookUrl; // URL pour Slack, Teams, Discord, etc.

    @Builder.Default
    private Boolean lastStatusWasUp = true; // Pour détecter le changement d'état

    private LocalDateTime lastNotificationSentAt; // Pour le rappel toutes les 5 min

    // ------------------------------------------

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Transient
    private StatusCheck lastStatus;

    public List<Integer> getAcceptedStatusesList() {
        if (acceptedStatuses == null || acceptedStatuses.isBlank()) {
            return List.of();
        }
        try {
            return List.of(acceptedStatuses.split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            return List.of(200);
        }
    }

    public void setAcceptedStatusesFromList(List<Integer> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            this.acceptedStatuses = "200";
        } else {
            this.acceptedStatuses = statuses.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
    }
}