# --- MapRoulette Scheme

# --- !Ups
-- Add needs_review, is_reviewer to user table
ALTER TABLE "users" ADD COLUMN needs_review boolean DEFAULT false;;
ALTER TABLE "users" ADD COLUMN is_reviewer boolean DEFAULT false;;

-- Add review columns (status, reviewed_by, reviewed_at)
ALTER TABLE "tasks" ADD COLUMN review_status integer DEFAULT NULL;;
ALTER TABLE "tasks" ADD COLUMN review_requested_by integer DEFAULT NULL;;
ALTER TABLE "tasks" ADD COLUMN reviewed_by integer DEFAULT NULL;;
ALTER TABLE "tasks" ADD COLUMN reviewed_at timestamp without time zone DEFAULT NULL;;
ALTER TABLE "tasks" ADD COLUMN review_claimed_at timestamp without time zone DEFAULT NULL;;
ALTER TABLE "tasks" ADD COLUMN review_claimed_by integer DEFAULT NULL;;

-- Add table to keep track of reviews
CREATE TABLE IF NOT EXISTS task_review_history
(
  id SERIAL NOT NULL PRIMARY KEY,
  task_id integer NOT NULL,
  requested_by integer NOT NULL,
  reviewed_by integer,
  review_status integer NOT NULL,
  reviewed_at timestamp without time zone DEFAULT NOW()
);;

ALTER TABLE "status_actions" ADD COLUMN started_at timestamp without time zone DEFAULT NULL;;

SELECT create_index_if_not_exists('task_review_history', 'task_review_history_task_id', '(task_id)');;
SELECT create_index_if_not_exists('tasks', 'tasks_review_status', '(review_status)');;
SELECT create_index_if_not_exists('tasks', 'tasks_reviewed_by', '(reviewed_by)');;
SELECT create_index_if_not_exists('tasks', 'tasks_review_claimed_by', '(review_claimed_by)');;
SELECT create_index_if_not_exists('tasks', 'tasks_review_claimed_at', '(review_claimed_at)');;

# --- !Downs
ALTER TABLE "users" DROP COLUMN needs_review;;
ALTER TABLE "users" DROP COLUMN is_reviewer;;

ALTER TABLE "tasks" DROP COLUMN review_status;;
ALTER TABLE "tasks" DROP COLUMN review_requested_by;;
ALTER TABLE "tasks" DROP COLUMN reviewed_by;;
ALTER TABLE "tasks" DROP COLUMN reviewed_at;;
ALTER TABLE "tasks" DROP COLUMN review_claimed_at;;
ALTER TABLE "tasks" DROP COLUMN review_claimed_by;;

ALTER TABLE "status_actions" DROP COLUMN started_at;;

DROP TABLE IF EXISTS task_review_history;;
