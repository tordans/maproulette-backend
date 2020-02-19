# --- !Ups
-- Add Time Spent Columns.
SELECT add_drop_column('tasks', 'completed_time_spent', 'INT DEFAULT NULL');;
SELECT add_drop_column('tasks', 'completed_by', 'INT DEFAULT NULL');;


# --- !Downs
SELECT add_drop_column('tasks', 'completed_time_spent', '', false);;
SELECT add_drop_column('tasks', 'completed_by', '', false);;
