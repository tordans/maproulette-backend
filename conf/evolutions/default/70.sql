# --- !Ups
-- Add overpass_target_type to challenges
ALTER TABLE IF EXISTS challenges
  ADD COLUMN overpass_target_type VARCHAR;;

# --- !Downs
-- Remove added column overpass_target_type
ALTER TABLE IF EXISTS challenges
  DROP COLUMN overpass_target_type;;
