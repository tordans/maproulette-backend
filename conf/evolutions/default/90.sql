# --- !Ups
DROP TRIGGER IF EXISTS update_challenges_modified ON challenges;;

# --- !Downs
DROP TRIGGER IF EXISTS update_challenges_modified ON challenges;;
CREATE TRIGGER update_challenges_modified BEFORE UPDATE ON challenges
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;
