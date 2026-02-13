package org.azirar.glenn.repositories;

import org.azirar.glenn.models.MonitoredApp;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MonitoredAppRepository extends R2dbcRepository<MonitoredApp, Long> {

    Flux<MonitoredApp> findByActiveTrue();

    Flux<MonitoredApp> findByCategory(String category);

    @Query("SELECT * FROM monitored_apps WHERE active = true ORDER BY name")
    Flux<MonitoredApp> findAllActiveOrdered();

    @Query("SELECT DISTINCT category FROM monitored_apps WHERE category IS NOT NULL")
    Flux<String> findAllCategories();
}