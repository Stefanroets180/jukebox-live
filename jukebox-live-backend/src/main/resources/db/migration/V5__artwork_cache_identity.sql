ALTER TABLE tracks ADD COLUMN artwork_cache_key VARCHAR(128);
CREATE INDEX idx_tracks_artwork_cache_key ON tracks (artwork_cache_key);
