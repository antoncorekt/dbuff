-- V4: Add Dbuf Instance Configuration tables
-- This migration adds support for multi-tenant instance configurations

-- Main instance configuration table
CREATE TABLE IF NOT EXISTS dbuf_instance_config (
    id VARCHAR(36) PRIMARY KEY,
    discord_channel_id VARCHAR(255),
    discord_guild_id VARCHAR(255),
    name VARCHAR(255),
    extra_config TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    active BOOLEAN DEFAULT TRUE
);

-- Many-to-many relationship between instance config and players
-- References PlayerDomain which has 'name' as primary key
CREATE TABLE IF NOT EXISTS dbuf_instance_config_players (
    instance_id VARCHAR(36) NOT NULL,
    player_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (instance_id, player_name),
    FOREIGN KEY (instance_id) REFERENCES dbuf_instance_config(id) ON DELETE CASCADE,
    FOREIGN KEY (player_name) REFERENCES player(name) ON DELETE CASCADE
);

-- Game mode IDs collection table (stores numeric game mode IDs matching MatchDomain.gameModeId)
CREATE TABLE IF NOT EXISTS dbuf_instance_game_modes (
    instance_id VARCHAR(36) NOT NULL,
    game_mode_id BIGINT NOT NULL,
    PRIMARY KEY (instance_id, game_mode_id),
    FOREIGN KEY (instance_id) REFERENCES dbuf_instance_config(id) ON DELETE CASCADE
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_dbuf_instance_discord_channel ON dbuf_instance_config(discord_channel_id);
CREATE INDEX IF NOT EXISTS idx_dbuf_instance_active ON dbuf_instance_config(active);
CREATE INDEX IF NOT EXISTS idx_dbuf_instance_config_players_player ON dbuf_instance_config_players(player_name);
