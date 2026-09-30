CREATE SEQUENCE categories_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS categories (
    id bigint DEFAULT nextval('categories_sequence') PRIMARY KEY,
    name varchar(50) NOT NULL UNIQUE
);
