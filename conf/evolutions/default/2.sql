# --- Map Roulette Scheme

# --- !Ups
-- adding new column for task to set it in priority 1 (HIGH), 2 (MEDIUM) or 3 (LOW), defaults to 1
ALTER TABLE tasks ADD COLUMN priority integer DEFAULT 0;
-- enabled defaults to false now
ALTER TABLE projects ALTER COLUMN enabled SET DEFAULT false;
ALTER TABLE challenges ALTER COLUMN enabled SET DEFAULT false;
-- New options for challenges
ALTER TABLE challenges ADD COLUMN default_priority integer DEFAULT 0;
ALTER TABLE challenges ADD COLUMN high_priority_rule character varying;
ALTER TABLE challenges ADD COLUMN medium_priority_rule character varying;
ALTER TABLE challenges ADD COLUMN low_priority_rule character varying;
ALTER TABLE challenges ADD COLUMN extra_options HSTORE;

-- Creates or updates and task. Will also check if task status needs to be updated
-- This change simply adds the priority
CREATE OR REPLACE FUNCTION create_update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, task_priority integer DEFAULT 0, reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  return_id integer;;
BEGIN
  return_id := task_id;;
  IF (SELECT task_id) = -1 THEN
    BEGIN
      INSERT INTO tasks (name, parent_id, instruction, priority) VALUES (task_name, task_parent_id, task_instruction, task_priority) RETURNING id INTO return_id;;
      EXCEPTION WHEN UNIQUE_VIOLATION THEN
      SELECT INTO return_id update_task(task_name, task_parent_id, task_instruction, task_status, task_id, task_priority, reset_interval);;
    END;;
  ELSE
    SELECT update_task(task_name, task_parent_id, task_instruction, task_status, task_id, task_priority, reset_interval);;
  END IF;;
  RETURN return_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

CREATE OR REPLACE FUNCTION update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, task_priority integer DEFAULT 0, reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  update_id integer;;
  update_modified timestamp without time zone;;
  update_status integer;;
  new_status integer;;
BEGIN
  IF (SELECT task_id) = -1 THEN
    SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE name = task_name AND parent_id = task_parent_id;;
  ELSE
    SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE id = task_id;;
  END IF;;
  new_status := task_status;;
  IF update_status = task_status AND (SELECT AGE(NOW(), update_modified)) > reset_interval::INTERVAL THEN
    new_status := 0;;
  END IF;;
  UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority WHERE id = update_id;;
  RETURN update_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

# --- !Downs
