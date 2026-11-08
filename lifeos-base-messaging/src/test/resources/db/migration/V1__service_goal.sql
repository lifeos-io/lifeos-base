-- Stands in for a service's own migration, to prove both Flyway histories coexist.
CREATE TABLE goal (
    id    uuid PRIMARY KEY,
    title text NOT NULL
);
