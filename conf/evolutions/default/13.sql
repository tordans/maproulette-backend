# --- MapRoulette Scheme

# --- !Ups
SELECT add_drop_column('users', 'properties', 'character varying');;
SELECT create_index_if_not_exists('status_actions', 'osm_user_id_created', '(osm_user_id,created)');;
-- Add changeset_id column
SELECT add_drop_column('tasks', 'changeset_id', 'integer DEFAULT -1');;
SELECT add_drop_column('challenges', 'info_link', 'character varying');;

-- Creates or updates and task. Will also check if task status needs to be updated
-- This change simply adds the priority
CREATE OR REPLACE FUNCTION create_update_task(task_name text,
                                              task_parent_id bigint,
                                              task_instruction text,
                                              task_status integer,
                                              task_id bigint DEFAULT -1,
                                              task_priority integer DEFAULT 0,
                                              task_changeset_id bigint DEFAULT -1,
                                              reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  return_id integer;;
BEGIN
  return_id := task_id;;
  IF (SELECT task_id) = -1 THEN
    BEGIN
      INSERT INTO tasks (name, parent_id, instruction,  priority) VALUES (task_name, task_parent_id, task_instruction, task_priority) RETURNING id INTO return_id;;
      EXCEPTION WHEN UNIQUE_VIOLATION THEN
      SELECT INTO return_id update_task(task_name, task_parent_id, task_instruction, task_status, task_id, task_priority, task_changeset_id, reset_interval);;
    END;;
  ELSE
    PERFORM update_task(task_name, task_parent_id, task_instruction, task_status, task_id, task_priority, task_changeset_id, reset_interval);;
  END IF;;
  RETURN return_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

CREATE OR REPLACE FUNCTION update_task(task_name text,
                                       task_parent_id bigint,
                                       task_instruction text,
                                       task_status integer,
                                       task_id bigint DEFAULT -1,
                                       task_priority integer DEFAULT 0,
                                       task_changeset_id bigint DEFAULT -1,
                                       reset_interval text DEFAULT '7 days') RETURNS integer as $$
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
  UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority, changeset_id = task_changeset_id WHERE id = update_id;;
  RETURN update_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

# --- !Downs
