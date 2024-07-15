-- SQL migration script to add 'is_global' column to 'challenges' table
-- !Ups
-- Add a new column 'is_global' to challenges table if it doesn't exist
ALTER TABLE IF EXISTS challenges ADD COLUMN IF NOT EXISTS is_global BOOLEAN;

-- Update 'is_global' column based on bounding box dimensions
UPDATE challenges
SET is_global = (
    CASE
        WHEN (ST_XMax(bounding)::numeric - ST_XMin(bounding)::numeric) > 180 THEN TRUE
        WHEN (ST_YMax(bounding)::numeric - ST_YMin(bounding)::numeric) > 90 THEN TRUE
        ELSE FALSE
    END
);

-- !Downs
ALTER TABLE IF EXISTS challenges
DROP COLUMN is_global;