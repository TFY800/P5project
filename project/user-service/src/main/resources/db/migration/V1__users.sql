CREATE TABLE users (
 id uuid PRIMARY KEY,
 email varchar(254) NOT NULL UNIQUE,
 display_name varchar(80) NOT NULL,
 password_hash varchar(100) NOT NULL
);
