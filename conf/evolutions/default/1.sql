# --- Map Roulette Scheme

# --- !Ups

CREATE OR REPLACE FUNCTION create_index_if_not_exists(t_name text, i_name text, index_sql text, unq boolean default false) RETURNS void as $$
DECLARE
  full_index_name varchar;;
  schema_name varchar;;
  unqValue varchar;;
BEGIN
  full_index_name = 'idx_' || t_name || '_' || i_name;;
  schema_name = 'public';;
  unqValue = '';;
  IF unq THEN
    unqValue = 'UNIQUE ';;
  END IF;;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = full_index_name
    AND n.nspname = schema_name
  ) THEN
    execute 'CREATE ' || unqValue || 'INDEX ' || full_index_name || ' ON ' || schema_name || '.' || t_name || ' ' || index_sql;;
  END IF;;
END
$$
LANGUAGE plpgsql VOLATILE;

CREATE OR REPLACE FUNCTION updateModified() RETURNS TRIGGER AS $$
BEGIN
  NEW.modified = NOW();;
  RETURN NEW;;
END
$$
LANGUAGE plpgsql VOLATILE;;

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS users
(
  id serial NOT NULL,
  osm_id integer NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  home_location geometry,
  home_location_name character varying,
  osm_created timestamp without time zone NOT NULL,
  display_name character varying NOT NULL,
  description character varying,
  avatar_url character varying,
  api_key character varying UNIQUE,
  oauth_token character varying NOT NULL,
  oauth_secret character varying NOT NULL,
  theme character varying DEFAULT('skin-blue'),
  CONSTRAINT users_pkey PRIMARY KEY(id)
);

DROP TRIGGER IF EXISTS update_users_modified ON users;
CREATE TRIGGER update_users_modified BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE PROCEDURE updateModified();

INSERT INTO users (id, osm_id, osm_created, display_name, oauth_token, oauth_secret, theme)
VALUES (-999, -999, NOW(), 'Guest', '', '', 'skin-green');

CREATE TABLE IF NOT EXISTS groups
(
  id serial NOT NULL,
  name character varying NOT NULL UNIQUE,
  group_type integer NOT NULL,
  CONSTRAINT groups_pkey PRIMARY KEY(id)
);

INSERT INTO groups (id, name, group_type) VALUES (-1, 'SUPER_USER', -1);
INSERT INTO groups (id, name, group_type) VALUES (-999, 'GUEST', -999);

CREATE TABLE IF NOT EXISTS user_groups
(
  id serial NOT NULL,
  user_id integer NOT NULL,
  group_id integer NOT NULL,
  CONSTRAINT ug_user_id_fkey FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT ug_group_id_fkey FOREIGN KEY (group_id)
    REFERENCES groups(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT ug_pkey PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS projects
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  name character varying NOT NULL UNIQUE,
  description character varying DEFAULT '',
  CONSTRAINT projects_pkey PRIMARY KEY (id)
);

DROP TRIGGER IF EXISTS update_projects_modified ON projects;
CREATE TRIGGER update_projects_modified BEFORE UPDATE ON projects
  FOR EACH ROW EXECUTE PROCEDURE updateModified();

CREATE TABLE IF NOT EXISTS projects_group_mapping
(
  id serial NOT NULL,
  project_id integer NOT NULL,
  group_id integer NOT NULL,
  CONSTRAINT pgm_project_id_fkey FOREIGN KEY (project_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT pgm_group_id_fkey FOREIGN KEY (group_id)
    REFERENCES groups(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT pgm_pkey PRIMARY KEY(id)
);

SELECT create_index_if_not_exists('projects_group_mapping', 'project_id', '(project_id)');

CREATE TABLE IF NOT EXISTS challenges
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  identifier character varying DEFAULT '',
  name character varying NOT NULL,
  parent_id integer NOT NULL,
  description character varying DEFAULT '',
  blurb character varying DEFAULT '',
  instruction character varying DEFAULT '',
  difficulty integer DEFAULT 1,
  CONSTRAINT challenges_parent_id_fkey FOREIGN KEY (parent_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT challenges_pkey PRIMARY KEY (id)
);

DROP TRIGGER IF EXISTS update_challenges_modified ON challenges;
CREATE TRIGGER update_challenges_modified BEFORE UPDATE ON challenges
  FOR EACH ROW EXECUTE PROCEDURE updateModified();

SELECT create_index_if_not_exists('challenges', 'parent_id', '(parent_id)');
SELECT create_index_if_not_exists('challenges', 'parent_id_name', '(parent_id, name)', true);
SELECT create_index_if_not_exists('challenges', 'identifier', '(identifier)');

CREATE TABLE IF NOT EXISTS surveys
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  identifier character varying DEFAULT '',
  name character varying NOT NULL,
  parent_id integer NOT NULL,
  description character varying DEFAULT '',
  question character varying DEFAULT '',
  CONSTRAINT surveys_parent_id_fkey FOREIGN KEY (parent_id)
  REFERENCES projects(id) MATCH SIMPLE
  ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT surveys_pkey PRIMARY KEY (id)
);

DROP TRIGGER IF EXISTS update_surveys_modified ON surveys;
CREATE TRIGGER update_surveys_modified BEFORE UPDATE ON surveys
  FOR EACH ROW EXECUTE PROCEDURE updateModified();

SELECT create_index_if_not_exists('surveys', 'parent_id', '(parent_id)');
SELECT create_index_if_not_exists('surveys', 'parent_id_name', '(parent_id, name)', true);
SELECT create_index_if_not_exists('surveys', 'identifier', '(identifier)');

CREATE TABLE IF NOT EXISTS answers
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  survey_id integer NOT NULL,
  answer character varying NOT NULL,
  CONSTRAINT answers_survey_id_fkey FOREIGN KEY (survey_id)
    REFERENCES surveys(id) MATCH SIMPLE
  ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT answers_pkey PRIMARY KEY(id)
);

DROP TRIGGER IF EXISTS update_answers_modified ON answers;
CREATE TRIGGER update_answers_modified BEFORE UPDATE ON answers
  FOR EACH ROW EXECUTE PROCEDURE updateModified();

SELECT create_index_if_not_exists('answers', 'survey_id', '(survey_id)');

CREATE TABLE IF NOT EXISTS survey_answers
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  user_id integer NOT NULL DEFAULT(-999),
  survey_id integer NOT NULL,
  task_id integer NOT NULL,
  answer_id integer NOT NULL,
  CONSTRAINT survey_answers_user_id_fkey FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE,
  CONSTRAINT survey_answers_survey_id_fkey FOREIGN KEY (survey_id)
    REFERENCES surveys(id) MATCH SIMPLE,
  CONSTRAINT survey_answers_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE,
  CONSTRAINT survey_answers_answer_id_fkey FOREIGN KEY (answer_id)
    REFERENCES answers(id) MATCH SIMPLE,
  CONSTRAINT survey_answers_pkey PRIMARY KEY(id)
);

SELECT create_index_if_not_exists('survey_answer', 'survey_id', '(survey_id)');

CREATE TABLE IF NOT EXISTS tasks
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  identifier character varying DEFAULT '',
  name character varying NOT NULL,
  location geometry NOT NULL,
  instruction character varying NOT NULL,
  parent_id integer NOT NULL,
  status integer DEFAULT 0 NOT NULL,
  CONSTRAINT tasks_pkey PRIMARY KEY(id),
  CONSTRAINT tasks_parent_id_fkey FOREIGN KEY (parent_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);

DROP TRIGGER IF EXISTS update_tasks_modified ON tasks;
CREATE TRIGGER update_tasks_modified BEFORE UPDATE ON tasks
  FOR EACH ROW EXECUTE PROCEDURE updateModified();

SELECT create_index_if_not_exists('tasks', 'parent_id', '(parent_id)');
SELECT create_index_if_not_exists('tasks', 'parent_id_name', '(parent_id, name)', true);

CREATE TABLE IF NOT EXISTS tags
(
  id SERIAL NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  name character varying NOT NULL UNIQUE,
  description character varying DEFAULT '',
  CONSTRAINT tag_pkey PRIMARY KEY(id)
);
-- index has the potentially to slow down inserts badly
SELECT create_index_if_not_exists('tags', 'name', '(name)');

CREATE TABLE IF NOT EXISTS tags_on_tasks
(
  id SERIAL NOT NULL,
  task_id integer NOT NULL,
  tag_id integer NOT NULL,
  CONSTRAINT tags_on_tasks_pkey PRIMARY KEY(id),
  CONSTRAINT task_tags_on_tasks_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks (id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT task_tags_on_tasks_tag_id_fkey FOREIGN KEY (tag_id)
    REFERENCES tags (id) MATCH SIMPLE
);

SELECT create_index_if_not_exists('tags_on_tasks', 'task_id', '(task_id)');
SELECT create_index_if_not_exists('tags_on_tasks', 'tag_id', '(tag_id)');
-- This index could slow down inserts pretty badly
SELECT create_index_if_not_exists('tags_on_tasks', 'task_id_tag_id', '(task_id, tag_id)');

CREATE TABLE IF NOT EXISTS task_geometries
(
  id SERIAL NOT NULL,
  osmid bigint,
  taskId integer NOT NULL,
  geom geometry NOT NULL,
  CONSTRAINT task_geometries_pkey PRIMARY KEY(id),
  CONSTRAINT task_geometries_task_id_fkey FOREIGN KEY (id)
    REFERENCES tasks (id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);

SELECT create_index_if_not_exists('task_geometries', 'geom', '(geom)');

CREATE TABLE IF NOT EXISTS actions
(
  id serial NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  user_id integer DEFAULT(-999),
  type_id integer,
  item_id integer,
  action integer NOT NULL,
  status integer NOT NULL,
  extra character varying,
  CONSTRAINT actions_user_id_fkey FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE,
  CONSTRAINT actions_task_id_fkey FOREIGN KEY (item_id)
    REFERENCES tasks(id) MATCH SIMPLE,
  CONSTRAINT actions_user_id_fkey FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE,
  CONSTRAINT actions_pkey PRIMARY KEY (id)
);

SELECT create_index_if_not_exists('actions', 'status', '(status)');
SELECT create_index_if_not_exists('actions', 'item_id', '(item_id)');
SELECT create_index_if_not_exists('actions', 'user_id', '(user_id)');
select create_index_if_not_exists('actions', 'created', '(created)');

-- Insert the default root, used for migration and those using the old API
INSERT INTO projects (name, description) VALUES ('default', 'The default root for any challenges not defining a project parent. Only available when using old API, and through migration.');

# --- !Downs
