-- Table des applications surveillées
CREATE TABLE IF NOT EXISTS monitored_apps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    description TEXT,
    url VARCHAR(500) NOT NULL,
    accepted_statuses VARCHAR(100) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Table des checks de statut
CREATE TABLE IF NOT EXISTS status_checks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id BIGINT NOT NULL,
    app_name VARCHAR(255),
    status_code INT,
    is_up BOOLEAN,
    response_time VARCHAR(50),
    error_message VARCHAR(500),
    checked_at TIMESTAMP,
    FOREIGN KEY (app_id) REFERENCES monitored_apps(id) ON DELETE CASCADE
);

-- Index pour les performances (ajout IF NOT EXISTS)
DROP INDEX IF EXISTS idx_status_checks_app_id;
DROP INDEX IF EXISTS idx_status_checks_checked_at;
DROP INDEX IF EXISTS idx_monitored_apps_active;

CREATE INDEX IF NOT EXISTS idx_status_checks_app_id ON status_checks(app_id);
CREATE INDEX IF NOT EXISTS idx_status_checks_checked_at ON status_checks(checked_at);
CREATE INDEX IF NOT EXISTS idx_monitored_apps_active ON monitored_apps(active);


-- Données de démonstration
INSERT INTO monitored_apps (name, category, url, accepted_statuses, active, created_at, updated_at)
VALUES ('Google', 'Moteur recherche', 'https://www.google.com', '200,301,302', true, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

INSERT INTO monitored_apps (name, category, url, accepted_statuses, active, created_at, updated_at)
VALUES ('GitHub', 'Développement', 'https://github.com', '200,301,302', true, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

INSERT INTO monitored_apps (name, category, url, accepted_statuses, active, created_at, updated_at)
VALUES ('Stack Overflow', 'Communauté', 'https://stackoverflow.com', '200,301,302', true, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());
