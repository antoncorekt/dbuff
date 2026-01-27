-- =====================================================
-- PostgreSQL Indexes for Item Ranking API Performance
-- =====================================================
-- 
-- These indexes are optimized for the ItemRankingRepository queries:
-- 1. Main query: Aggregates items by player with date filtering
-- 2. Total match count query: Counts matches per player with date filtering
-- 3. Player name lookup: Simple lookup by player ID
--
-- Run this migration after the tables are created.
-- =====================================================

-- =====================================================
-- 1. ITEM_DOMAIN INDEXES
-- =====================================================

-- Primary lookup: Filter items by player_id (most selective filter)
-- This is the main entry point for the query
CREATE INDEX IF NOT EXISTS idx_item_domain_player_id 
ON item_domain (player_id);

-- Composite index for the main query join conditions and filters
-- Covers: player_id filter, match_id join, player_slot join, is_neutral filter
-- INCLUDE clause adds columns needed for SELECT without table lookup (covering index)
CREATE INDEX IF NOT EXISTS idx_item_domain_player_match_slot 
ON item_domain (player_id, match_id, player_slot, is_neutral)
INCLUDE (item_id, item_name, item_pretty_name, item_purchase_time);

-- Index for item_id filtering (when items/excludedItems params are used)
CREATE INDEX IF NOT EXISTS idx_item_domain_item_id 
ON item_domain (item_id);

-- Partial index for non-neutral items (most common query pattern)
-- This is more efficient than filtering is_neutral = false in every query
CREATE INDEX IF NOT EXISTS idx_item_domain_player_non_neutral 
ON item_domain (player_id, match_id, player_slot)
WHERE is_neutral = false;


-- =====================================================
-- 2. PLAYER_MATCH_STATISTIC_DOMAIN INDEXES
-- =====================================================

-- Primary lookup: Filter stats by player_id
CREATE INDEX IF NOT EXISTS idx_player_match_stat_player_id 
ON player_match_statistic_domain (player_id);

-- Composite index for join conditions and aggregations
-- Covers: player_id filter, match_id join, player_slot join
-- INCLUDE clause adds win column for aggregation without table lookup
CREATE INDEX IF NOT EXISTS idx_player_match_stat_player_match_slot 
ON player_match_statistic_domain (player_id, match_id, player_slot)
INCLUDE (win);

-- Index for match_id join (used in cross-join with match_domain)
CREATE INDEX IF NOT EXISTS idx_player_match_stat_match_id 
ON player_match_statistic_domain (match_id);


-- =====================================================
-- 3. MATCH_DOMAIN INDEXES
-- =====================================================

-- Index for date range filtering (startDate, endDate parameters)
-- This is critical for the date-based filtering in the query
CREATE INDEX IF NOT EXISTS idx_match_domain_start_local_date 
ON match_domain (start_local_date);

-- Composite index for date range with match ID
-- Useful when joining with item_domain and filtering by date
CREATE INDEX IF NOT EXISTS idx_match_domain_id_date 
ON match_domain (id, start_local_date);


-- =====================================================
-- 4. PLAYER_DOMAIN INDEXES
-- =====================================================

-- Index for player lookup by ID (used in getPlayerName query)
-- Note: If 'id' is already the primary key, this may not be needed
CREATE INDEX IF NOT EXISTS idx_player_domain_id 
ON player_domain (id);


-- =====================================================
-- QUERY ANALYSIS AND INDEX JUSTIFICATION
-- =====================================================
--
-- Main Query Pattern:
-- SELECT item_id, item_name, item_pretty_name, 
--        COUNT(DISTINCT match_id), SUM(win), AVG(item_purchase_time)
-- FROM item_domain i, match_domain m, player_match_statistic_domain s
-- WHERE i.player_id = ?
--   AND i.match_id = m.id
--   AND i.match_id = s.match_id
--   AND i.player_slot = s.player_slot
--   AND m.start_local_date >= ?
--   AND m.start_local_date <= ?
--   AND i.is_neutral = false
--   [AND i.item_id IN (?)]
--   [AND i.item_id NOT IN (?)]
-- GROUP BY item_id, item_name, item_pretty_name
-- ORDER BY COUNT(DISTINCT match_id) DESC
-- LIMIT ?
--
-- Index Strategy:
-- 1. Start with item_domain filtered by player_id (most selective)
-- 2. Use covering index to avoid table lookups for item columns
-- 3. Join with match_domain using id, filter by date
-- 4. Join with player_match_statistic_domain using match_id + player_slot
-- 5. Aggregate win values from stats table
--
-- Expected Query Plan:
-- 1. Index Scan on idx_item_domain_player_non_neutral (player_id = ?)
-- 2. Nested Loop Join with match_domain using idx_match_domain_id_date
-- 3. Filter by start_local_date range
-- 4. Nested Loop Join with player_match_statistic_domain using idx_player_match_stat_player_match_slot
-- 5. HashAggregate for GROUP BY
-- 6. Sort for ORDER BY
-- 7. Limit


-- =====================================================
-- 5. ABILITY_DOMAIN INDEXES
-- =====================================================

-- Primary lookup: Filter abilities by player_id (most selective filter)
CREATE INDEX IF NOT EXISTS idx_ability_domain_player_id
ON ability_domain (player_id);

-- Composite index for the main query join conditions
-- Covers: player_id filter, match_id join, player_slot join
-- INCLUDE clause adds columns needed for SELECT without table lookup (covering index)
CREATE INDEX IF NOT EXISTS idx_ability_domain_player_match_slot
ON ability_domain (player_id, match_id, player_slot)
INCLUDE (ability_id, name, pretty_name);

-- Index for ability_id filtering (when abilities/excludedAbilities params are used)
CREATE INDEX IF NOT EXISTS idx_ability_domain_ability_id
ON ability_domain (ability_id);


-- =====================================================
-- 6. PLAYER STATISTICS INDEXES
-- =====================================================
-- These indexes optimize the PlayerStatisticRepository queries:
-- 1. Aggregation query: Aggregates all player stats with date filtering
-- 2. Popular heroes query: Groups by hero with win rate calculation
-- 3. Total match count query: Counts distinct matches per player

-- Composite index for player statistics aggregation queries
-- Covers: player_id filter, match_id join
-- INCLUDE clause adds all aggregated columns for covering index
CREATE INDEX IF NOT EXISTS idx_player_match_stat_aggregation
ON player_match_statistic_domain (player_id, match_id)
INCLUDE (
    hero_id, hero_name, hero_pretty_name, win,
    obs_placed, sen_placed, creeps_stacked, last_hits, denies,
    camps_stacked, rune_pickups, tower_kills, roshan_kills,
    kda, neutral_kills, courier_kills, lane_efficiency,
    gold_per_min, xp_per_min
);

-- Index for hero grouping in popular heroes query
-- Optimizes GROUP BY hero_id, hero_name, hero_pretty_name
CREATE INDEX IF NOT EXISTS idx_player_match_stat_hero
ON player_match_statistic_domain (player_id, hero_id)
INCLUDE (hero_name, hero_pretty_name, win, match_id);


-- =====================================================
-- 7. PLAYER_STATISTIC_SUMMARY INDEXES
-- =====================================================
-- These indexes optimize the PlayerStatisticSummaryRepo queries:
-- 1. Lookup by player and date range
-- 2. Historical summaries ordered by date

-- Index for finding summaries by player and exact date range
-- Used by findByPlayerIdAndStartDateAndEndDate
CREATE INDEX IF NOT EXISTS idx_player_stat_summary_player_dates
ON player_statistic_summary (player_id, start_date, end_date);

-- Index for finding summaries by player ordered by date
-- Used by findByPlayerIdOrderByStartDateDesc
CREATE INDEX IF NOT EXISTS idx_player_stat_summary_player_start_date
ON player_statistic_summary (player_id, start_date DESC);


-- =====================================================
-- PLAYER STATISTICS QUERY ANALYSIS
-- =====================================================
--
-- Main Aggregation Query Pattern:
-- SELECT AVG(obs_placed), AVG(sen_placed), AVG(last_hits), MAX(last_hits), MIN(last_hits),
--        AVG(kda), MAX(kda), MIN(kda), AVG(gold_per_min), MAX(gold_per_min), MIN(gold_per_min),
--        SUM(win), COUNT(match_id), ...
-- FROM player_match_statistic_domain s, match_domain m
-- WHERE s.player_id = ?
--   AND s.match_id = m.id
--   AND m.start_local_date >= ?
--   AND m.start_local_date <= ?
--
-- Popular Heroes Query Pattern:
-- SELECT hero_id, hero_name, hero_pretty_name, COUNT(match_id), SUM(win)
-- FROM player_match_statistic_domain s, match_domain m
-- WHERE s.player_id = ?
--   AND s.match_id = m.id
--   AND m.start_local_date >= ?
--   AND m.start_local_date <= ?
-- GROUP BY hero_id, hero_name, hero_pretty_name
-- ORDER BY COUNT(match_id) DESC
-- LIMIT ?
--
-- Index Strategy:
-- 1. Use idx_player_match_stat_aggregation for main aggregation (covering index)
-- 2. Use idx_player_match_stat_hero for hero grouping
-- 3. Join with match_domain using idx_match_domain_id_date for date filtering
-- 4. Use idx_player_stat_summary_player_dates for summary lookups


-- =====================================================
-- 8. FIND PLAYER MATCHES INDEXES
-- =====================================================
-- These indexes optimize the FindPlayerMatchesRepository queries:
-- 1. Get match IDs for a player ordered by date
-- 2. Get statistics for multiple players in multiple matches
-- 3. Player lookup by name

-- Index for player lookup by name (used by findByName)
-- Critical for the initial player search
CREATE INDEX IF NOT EXISTS idx_player_domain_name
ON player_domain (name);

-- Composite index for finding player matches with date ordering
-- Covers: player_id filter, match_id for join, with date for ordering
CREATE INDEX IF NOT EXISTS idx_player_match_stat_player_match_date
ON player_match_statistic_domain (player_id, match_id);

-- Covering index for match statistics retrieval
-- Used when fetching statistics for multiple players in multiple matches
CREATE INDEX IF NOT EXISTS idx_player_match_stat_match_players
ON player_match_statistic_domain (match_id, player_id)
INCLUDE (player_slot, hero_pretty_name, kda, win, gold_per_min);


-- =====================================================
-- FIND PLAYER MATCHES QUERY ANALYSIS
-- =====================================================
--
-- Query 1: Get Match IDs for Player (ordered by date)
-- SELECT s.match_id, m.start_local_date
-- FROM player_match_statistic_domain s, match_domain m
-- WHERE s.player_id = ?
--   AND s.match_id = m.id
-- GROUP BY s.match_id, m.start_local_date
-- ORDER BY m.start_local_date DESC
-- LIMIT ?
--
-- Query 2: Get Statistics for Players in Matches
-- SELECT match_id, player_id, player_slot, hero_pretty_name, kda, win, gold_per_min
-- FROM player_match_statistic_domain
-- WHERE match_id IN (?, ?, ...)
--   AND player_id IN (?, ?, ...)
-- ORDER BY match_id, player_slot
--
-- Index Strategy:
-- 1. Use idx_player_domain_name for initial player lookup by name
-- 2. Use idx_player_match_stat_player_match_date for finding player's matches
-- 3. Join with match_domain using idx_match_domain_id_date for date ordering
-- 4. Use idx_player_match_stat_match_players for fetching statistics (covering index)


-- =====================================================
-- MAINTENANCE RECOMMENDATIONS
-- =====================================================
--
-- 1. Run ANALYZE after creating indexes:
--    ANALYZE item_domain;
--    ANALYZE ability_domain;
--    ANALYZE player_match_statistic_domain;
--    ANALYZE player_statistic_summary;
--    ANALYZE match_domain;
--    ANALYZE player_domain;
--
-- 2. Monitor index usage with:
--    SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
--    FROM pg_stat_user_indexes
--    WHERE tablename IN ('item_domain', 'ability_domain', 'player_match_statistic_domain',
--                        'player_statistic_summary', 'match_domain', 'player_domain')
--    ORDER BY idx_scan DESC;
--
-- 3. Check for unused indexes periodically:
--    SELECT indexname, idx_scan
--    FROM pg_stat_user_indexes
--    WHERE idx_scan = 0 AND schemaname = 'public';
--
-- 4. Consider VACUUM ANALYZE after bulk data loads:
--    VACUUM ANALYZE item_domain;
--    VACUUM ANALYZE ability_domain;
--    VACUUM ANALYZE player_match_statistic_domain;
--    VACUUM ANALYZE player_statistic_summary;
--    VACUUM ANALYZE match_domain;
