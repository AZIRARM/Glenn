package org.azirar.glenn.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("status_checks")
public class StatusCheck {
    @Id
    private Long id;

    private Long appId;

    private String appName;

    private Integer statusCode;

    private Boolean isUp;

    private String responseTime;

    private String errorMessage;

    private LocalDateTime checkedAt;

    // Pour l'affichage live
    public String getStatusColor() {
        if (isUp) return "success";
        return "danger";
    }

    public String getStatusIcon() {
        if (isUp) return "bi-check-circle-fill";
        return "bi-exclamation-triangle-fill";
    }
}