# --- !Ups
ALTER TABLE task_review_history ADD COLUMN reject_tag INTEGER;;

# --- !Downs
ALTER TABLE IF EXISTS task_review_history DROP COLUMN reject_tag;;