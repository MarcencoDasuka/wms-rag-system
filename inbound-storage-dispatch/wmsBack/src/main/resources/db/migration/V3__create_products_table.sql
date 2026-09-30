CREATE SEQUENCE products_sequence START 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS products (
    id bigint DEFAULT nextval('products_sequence') PRIMARY KEY,
    name varchar(100) NOT NULL,
    barcode varchar(100) NOT NULL UNIQUE,
    description varchar(255),
    category_id bigint REFERENCES categories(id),
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
);
