-- V3: Add match analysis table for AI-generated analysis results

-- Create the match_analysis_domain table
CREATE TABLE IF NOT EXISTS match_analysis_domain (
    id BIGSERIAL PRIMARY KEY,
    analysis_text TEXT,
    ai_provider VARCHAR(255),
    ai_model VARCHAR(255),
    created_at TIMESTAMP,
    context_prompt TEXT,
    match_ids VARCHAR(1000),
    match_count INTEGER,
    tokens_used INTEGER,
    success BOOLEAN,
    error_message VARCHAR(1000)
);

-- Add analysis_id column to match_domain table
ALTER TABLE match_domain 
ADD COLUMN IF NOT EXISTS analysis_id BIGINT;

-- Add foreign key constraint
ALTER TABLE match_domain 
ADD CONSTRAINT fk_match_analysis 
FOREIGN KEY (analysis_id) 
REFERENCES match_analysis_domain(id);

-- Create index on analysis_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_match_domain_analysis_id 
ON match_domain(analysis_id);

-- Create index on created_at for time-based queries
CREATE INDEX IF NOT EXISTS idx_match_analysis_created_at 
ON match_analysis_domain(created_at);

-- Create index on ai_provider for filtering by provider
CREATE INDEX IF NOT EXISTS idx_match_analysis_ai_provider 
ON match_analysis_domain(ai_provider);

-- Create index on success for filtering successful/failed analyses
CREATE INDEX IF NOT EXISTS idx_match_analysis_success 
ON match_analysis_domain(success);
