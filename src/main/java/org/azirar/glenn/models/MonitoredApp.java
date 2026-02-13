package org.azirar.glenn.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient; // CRUCIAL pour R2DBC
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
    @Pattern(regexp = "^(http|https)://.*$", message = "L'URL doit commencer par http:// ou https://")
    private String url;

    @NotNull(message = "Les statuts acceptés sont obligatoires")
    private String acceptedStatuses; // Stocké en DB comme "200,201"

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * @Transient de Spring Data indique à R2DBC d'ignorer ce champ
     * lors des requêtes SQL (SELECT/INSERT/UPDATE).
     */
    @Transient
    private StatusCheck lastStatus;

    // Helper pour transformer la String en Liste utilisable par Java
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
            return List.of(200); // Fallback par défaut si format corrompu
        }
    }

    // Helper pour transformer une Liste en String pour la base de données
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