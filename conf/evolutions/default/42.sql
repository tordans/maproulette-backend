# --- MapRoulette Scheme

# --- !Ups
-- Add completion_responses for tasks
ALTER TABLE tasks ADD COLUMN completion_responses jsonb;;

# --- !Downs
ALTER TABLE tasks DROP COLUMN completion_responses;;
