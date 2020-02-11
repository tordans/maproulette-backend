# --- !Ups
-- Add task_styles to challenges.
ALTER TABLE IF EXISTS challenges ADD COLUMN task_styles VARCHAR DEFAULT NULL;;

# --- !Downs
ALTER TABLE IF EXISTS challenges DROP COLUMN task_styles;;
