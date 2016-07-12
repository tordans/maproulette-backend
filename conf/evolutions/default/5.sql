# --- Map Roulette Scheme

# --- !Ups
-- Update users table for user settings
ALTER TABLE IF EXISTS users ADD COLUMN default_editor integer DEFAULT -1;
ALTER TABLE IF EXISTS users ADD COLUMN default_basemap integer DEFAULT -1;
ALTER TABLE IF EXISTS users ADD COLUMN custom_basemap_url character varying;
ALTER TABLE IF EXISTS users ADD COLUMN email_opt_in boolean DEFAULT false;
ALTER TABLE IF EXISTS users ADD COLUMN locale character varying;
ALTER TABLE IF EXISTS users DROP COLUMN theme;
ALTER TABLE IF EXISTS users ADD COLUMN theme integer DEFAULT -1;

-- Table for all challenges, which is a child of Project, Surveys are also stored in this table
CREATE TABLE IF NOT EXISTS saved_challenges
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  user_id integer NOT NULL,
  challenge_id integer NOT NULL,
  CONSTRAINT saved_challenges_user_id FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT saved_challenges_challenge_id FOREIGN KEY (challenge_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);
SELECT create_index_if_not_exists('saved_challenges', 'user_id', '(user_id)');
SELECT create_index_if_not_exists('saved_challenges', 'user_id_challenge_id', '(user_id, challenge_id)', true);

# --- !Downs
ALTER TABLE IF EXISTS users DROP COLUMN default_editor;
ALTER TABLE IF EXISTS users DROP COLUMN default_basemap;
ALTER TABLE IF EXISTS users DROP COLUMN custom_basemap_url;
ALTER TABLE IF EXISTS users DROP COLUMN email_opt_in;
ALTER TABLE IF EXISTS users DROP COLUMN locale;
ALTER TABLE IF EXISTS users DROP COLUMN theme;
ALTER TABLE IF EXISTS users ADD COLUMN theme character varying DEFAULT('skin-blue');
DROP TABLE IF EXISTS saved_challenges;
