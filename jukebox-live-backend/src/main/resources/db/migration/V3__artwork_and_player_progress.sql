ALTER TABLE tracks
    ADD COLUMN musicbrainz_release_id VARCHAR(36),
    ADD COLUMN artwork_lookup_status VARCHAR(20) NOT NULL DEFAULT 'NOT_LOOKED_UP',
    ADD COLUMN artwork_last_checked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN artwork_candidates_json TEXT,
    ADD COLUMN artwork_source_url VARCHAR(500);

ALTER TABLE queue
    ADD COLUMN progress_seconds INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN is_paused BOOLEAN NOT NULL DEFAULT FALSE;
