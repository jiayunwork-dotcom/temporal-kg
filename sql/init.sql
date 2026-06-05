CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

CREATE TABLE entities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    entity_type VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    attributes JSONB DEFAULT '{}',
    canonical_name VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(name, entity_type)
);

CREATE INDEX idx_entities_name ON entities USING gin(name gin_trgm_ops);
CREATE INDEX idx_entities_type ON entities(entity_type);
CREATE INDEX idx_entities_canonical ON entities(canonical_name);

CREATE TABLE relations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    canonical_name VARCHAR(256),
    category VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(name)
);

CREATE INDEX idx_relations_name ON relations(name);
CREATE INDEX idx_relations_category ON relations(category);

CREATE TABLE triples (
    id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    relation_id BIGINT NOT NULL REFERENCES relations(id) ON DELETE CASCADE,
    object_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    time_point TIMESTAMP WITH TIME ZONE,
    time_start TIMESTAMP WITH TIME ZONE,
    time_end TIMESTAMP WITH TIME ZONE,
    confidence DOUBLE PRECISION DEFAULT 1.0,
    source VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_triples_subject ON triples(subject_id);
CREATE INDEX idx_triples_object ON triples(object_id);
CREATE INDEX idx_triples_relation ON triples(relation_id);
CREATE INDEX idx_triples_time_point ON triples(time_point);
CREATE INDEX idx_triples_time_range ON triples(time_start, time_end);
CREATE INDEX idx_triples_subject_relation ON triples(subject_id, relation_id);
CREATE INDEX idx_triples_object_relation ON triples(object_id, relation_id);
CREATE INDEX idx_triples_subject_object ON triples(subject_id, object_id);

CREATE TABLE entity_aliases (
    id BIGSERIAL PRIMARY KEY,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    alias VARCHAR(512) NOT NULL,
    similarity_score DOUBLE PRECISION,
    confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_entity_aliases_entity ON entity_aliases(entity_id);
CREATE INDEX idx_entity_aliases_alias ON entity_aliases USING gin(alias gin_trgm_ops);

CREATE TABLE relation_aliases (
    id BIGSERIAL PRIMARY KEY,
    relation_id BIGINT NOT NULL REFERENCES relations(id) ON DELETE CASCADE,
    alias VARCHAR(256) NOT NULL,
    confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE temporal_patterns (
    id BIGSERIAL PRIMARY KEY,
    antecedent JSONB NOT NULL,
    consequent JSONB NOT NULL,
    support DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    avg_time_interval_hours DOUBLE PRECISION,
    pattern_type VARCHAR(32) NOT NULL DEFAULT 'PAIR',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_temporal_patterns_support ON temporal_patterns(support);
CREATE INDEX idx_temporal_patterns_confidence ON temporal_patterns(confidence);

CREATE TABLE causal_links (
    id BIGSERIAL PRIMARY KEY,
    cause_entity_id BIGINT NOT NULL REFERENCES entities(id),
    cause_relation_id BIGINT NOT NULL REFERENCES relations(id),
    effect_entity_id BIGINT NOT NULL REFERENCES entities(id),
    effect_relation_id BIGINT NOT NULL REFERENCES relations(id),
    granger_f_statistic DOUBLE PRECISION,
    granger_p_value DOUBLE PRECISION,
    lag_hours DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE model_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    params JSONB DEFAULT '{}',
    result JSONB,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_model_jobs_status ON model_jobs(status);
CREATE INDEX idx_model_jobs_type ON model_jobs(job_type);

CREATE TABLE evaluation_results (
    id BIGSERIAL PRIMARY KEY,
    model_type VARCHAR(64) NOT NULL,
    model_job_id BIGINT REFERENCES model_jobs(id),
    mrr DOUBLE PRECISION,
    hits_at_1 DOUBLE PRECISION,
    hits_at_3 DOUBLE PRECISION,
    hits_at_10 DOUBLE PRECISION,
    time_aware_metrics JSONB DEFAULT '{}',
    pattern_metrics JSONB DEFAULT '{}',
    params JSONB DEFAULT '{}',
    evaluated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE import_jobs (
    id BIGSERIAL PRIMARY KEY,
    format VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_records INT DEFAULT 0,
    processed_records INT DEFAULT 0,
    failed_records INT DEFAULT 0,
    duplicates_skipped INT DEFAULT 0,
    error_details JSONB DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_import_jobs_status ON import_jobs(status);

CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    pattern_id BIGINT REFERENCES temporal_patterns(id),
    trigger_triple_id BIGINT REFERENCES triples(id),
    predicted_event JSONB NOT NULL,
    predicted_time_window VARCHAR(128),
    confidence DOUBLE PRECISION,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_alerts_status ON alerts(status);
