-- Migration: Remove meeting status column
-- Date: 2025-11-26
-- Description: Remove redundant 'status' column from meetings table.
--              The 'ended_at' timestamp is sufficient to determine meeting state.

-- Remove the status column from meetings table
ALTER TABLE meetings DROP COLUMN IF EXISTS status;

-- Verification query (optional - run after migration to confirm)
-- SELECT column_name, data_type 
-- FROM information_schema.columns 
-- WHERE table_name = 'meetings' 
-- ORDER BY ordinal_position;
