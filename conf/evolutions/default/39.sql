# --- MapRoulette Scheme

# --- !Ups
-- Add tag type for tags

ALTER TABLE "task_review" ADD COLUMN review_started_at timestamp without time zone DEFAULT NULL;;
ALTER TABLE "task_review_history" ADD COLUMN review_started_at timestamp without time zone DEFAULT NULL;;

# --- !Downs
ALTER TABLE "task_review" DROP COLUMN review_started_at;;
ALTER TABLE "task_review_history" DROP COLUMN review_started_at;;
