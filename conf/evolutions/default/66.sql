# --- !Ups
-- Remove negative timestamps. Prior to PR #728 if the database and server
-- ran on different machines and had different times than this could occur
UPDATE TASKS SET completed_time_spent = NULL WHERE completed_time_spent <= 0

# --- !Downs
