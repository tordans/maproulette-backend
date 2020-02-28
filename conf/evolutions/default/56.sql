# --- !Ups
-- Add Time Spent Columns.
SELECT add_drop_column('tasks', 'completed_time_spent', 'INT DEFAULT NULL');;
SELECT add_drop_column('tasks', 'completed_by', 'INT DEFAULT NULL');;

SELECT add_drop_column('completion_snapshots', 'avg_time_spent', 'FLOAT DEFAULT 0');;
SELECT add_drop_column('completion_snapshots', 'tasks_with_time', 'INT DEFAULT 0');;

SELECT add_drop_column('review_snapshots', 'total_review_time', 'FLOAT DEFAULT 0');;
SELECT add_drop_column('review_snapshots', 'tasks_with_review_time', 'INT DEFAULT 0');;

SELECT add_drop_column('user_metrics', 'total_time_spent', 'FLOAT DEFAULT 0');;
SELECT add_drop_column('user_metrics', 'tasks_with_time', 'INT DEFAULT 0');;

SELECT add_drop_column('user_metrics', 'total_review_time', 'FLOAT DEFAULT 0');;
SELECT add_drop_column('user_metrics', 'tasks_with_review_time', 'INT DEFAULT 0');;

SELECT add_drop_column('user_metrics_history', 'total_time_spent', 'FLOAT DEFAULT 0');;
SELECT add_drop_column('user_metrics_history', 'tasks_with_time', 'INT DEFAULT 0');;

SELECT add_drop_column('user_metrics_history', 'total_review_time', 'FLOAT DEFAULT 0');;
SELECT add_drop_column('user_metrics_history', 'tasks_with_review_time', 'INT DEFAULT 0');;

# --- !Downs
SELECT add_drop_column('tasks', 'completed_time_spent', '', false);;
SELECT add_drop_column('tasks', 'completed_by', '', false);;

SELECT add_drop_column('completion_snapshots', 'avg_time_spent', '', false);;
SELECT add_drop_column('completion_snapshots', 'tasks_with_time', '', false);;

SELECT add_drop_column('review_snapshots', 'total_review_time', '', false);;
SELECT add_drop_column('review_snapshots', 'tasks_with_review_time', '', false);;

SELECT add_drop_column('user_metrics', 'total_time_spent', '', false);;
SELECT add_drop_column('user_metrics', 'tasks_with_time', '', false);;

SELECT add_drop_column('user_metrics_history', 'total_review_time', '', false);;
SELECT add_drop_column('user_metrics_history', 'tasks_with_review_time', '', false);;

SELECT add_drop_column('user_metrics_history', 'total_time_spent', '', false);;
SELECT add_drop_column('user_metrics_history', 'tasks_with_time', '', false);;

SELECT add_drop_column('user_metrics_history', 'total_review_time', '', false);;
SELECT add_drop_column('user_metrics_history', 'tasks_with_review_time', '', false);;
