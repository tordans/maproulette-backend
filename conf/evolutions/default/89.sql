# --- !Ups
ALTER TABLE task_review_history ADD COLUMN reject_tags VARCHAR;;

DELETE FROM tags WHERE name = 'Geometry' AND tag_type = 'reject';
INSERT INTO tags (name, tag_type) VALUES ('Geometry', 'reject');
DELETE FROM tags WHERE name = 'Tagging' AND tag_type = 'reject';
INSERT INTO tags (name, tag_type) VALUES ('Tagging', 'reject');
DELETE FROM tags WHERE name = 'Incomplete' AND tag_type = 'reject';
INSERT INTO tags (name, tag_type) VALUES ('Incomplete', 'reject');

# --- !Downs
ALTER TABLE IF EXISTS task_review_history DROP COLUMN reject_tags;;

DELETE FROM tags WHERE name = 'Geometry' AND tag_type = 'reject';
DELETE FROM tags WHERE name = 'Tagging' AND tag_type = 'reject';
DELETE FROM tags WHERE name = 'Incomplete' AND tag_type = 'reject';