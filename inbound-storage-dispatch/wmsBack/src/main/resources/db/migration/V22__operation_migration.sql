ALTER TABLE processes
DROP COLUMN operator_id;

ALTER TABLE tasks
ADD COLUMN operator_id bigint REFERENCES users(id);
