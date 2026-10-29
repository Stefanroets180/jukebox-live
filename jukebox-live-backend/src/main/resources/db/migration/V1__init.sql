CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    credits INTEGER DEFAULT 0,
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE parties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(10) UNIQUE NOT NULL,
    host_user_id UUID REFERENCES users(id),
    party_ip INET NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    geofence_radius_meters DOUBLE PRECISION DEFAULT 100.0,
    music_library_path VARCHAR(500),
    spotify_device_id VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    ended_at TIMESTAMP
);

CREATE TABLE party_guests (
    party_id UUID REFERENCES parties(id),
    user_id UUID REFERENCES users(id),
    joined_ip INET NOT NULL,
    joined_lat DOUBLE PRECISION,
    joined_lng DOUBLE PRECISION,
    joined_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (party_id, user_id)
);

CREATE TABLE tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(255) NOT NULL,
    album VARCHAR(255),
    duration_seconds INTEGER,
    file_path VARCHAR(500) NOT NULL,
    album_art_path VARCHAR(500),
    genre VARCHAR(100),
    file_size BIGINT,
    indexed_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id UUID REFERENCES parties(id),
    track_id UUID REFERENCES tracks(id),
    requested_by UUID REFERENCES users(id),
    status VARCHAR(20) DEFAULT 'PENDING',
    position INTEGER NOT NULL,
    cost INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    played_at TIMESTAMP
);

CREATE TABLE credit_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    party_id UUID REFERENCES parties(id),
    amount INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tracks_search ON tracks USING gin(to_tsvector('english', title || ' ' || artist || ' ' || COALESCE(album, '')));
CREATE INDEX idx_queue_party ON queue(party_id, status, position);
CREATE INDEX idx_party_code ON parties(code);
CREATE INDEX idx_credit_user ON credit_transactions(user_id, created_at DESC);
