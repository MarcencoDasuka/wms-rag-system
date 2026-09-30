CREATE SEQUENCE processes_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS processes(
    id bigint DEFAULT nextval('processes_sequence') PRIMARY KEY,
    operator_id bigint REFERENCES users(id),
    task_id bigint REFERENCES tasks(id) NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    stock_id bigint REFERENCES stocks(id) NOT NULL,
    status varchar(10) DEFAULT 'CREATED' NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
    );