CREATE SEQUENCE tasks_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS tasks(
    id bigint DEFAULT nextval('tasks_sequence') PRIMARY KEY,
    supervisor_id bigint REFERENCES users(id) NOT NULL,
    task_type varchar(20) NOT NULL,
    requested_quantity integer NOT NULL,
    completed_at timestamptz,
    status varchar(30) NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
    );