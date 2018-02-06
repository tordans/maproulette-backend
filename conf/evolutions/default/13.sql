# --- MapRoulette Scheme

# --- !Ups
SELECT add_drop_column('users', 'properties', 'character varying');;
SELECT create_index_if_not_exists('status_actions', 'osm_user_id_created', '(osm_user_id,created)');;

# --- !Downs
