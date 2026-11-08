-- Owned by lifeos-base-messaging. Applied by the library with its own Flyway history table
-- (flyway_schema_history_lifeos_base), so its versions never collide with the service's migrations.

-- Transactional outbox: rows are written in the same transaction as the business change and published
-- to Kafka by the relay. seq gives the publish order; event_id (UUID v7) is the envelope's eventId.
CREATE TABLE outbox_event (
    seq            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       uuid        NOT NULL UNIQUE,
    aggregate_type text        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     text        NOT NULL,
    event_version  int         NOT NULL CHECK (event_version >= 1),
    user_id        uuid        NOT NULL,
    correlation_id text,
    payload        jsonb       NOT NULL,
    occurred_at    timestamptz NOT NULL,
    published_at   timestamptz,
    attempts       int         NOT NULL DEFAULT 0,
    last_error     text
);

CREATE INDEX outbox_event_unpublished ON outbox_event (seq) WHERE published_at IS NULL;
CREATE INDEX outbox_event_published_at ON outbox_event (published_at) WHERE published_at IS NOT NULL;

-- Inbox: one row per (event, consumer) processed, written in the same transaction as the handler's
-- changes. A redelivered event hits the primary key and is skipped.
CREATE TABLE inbox_message (
    message_id   uuid        NOT NULL,
    consumer     text        NOT NULL,
    event_type   text        NOT NULL,
    processed_at timestamptz NOT NULL,
    PRIMARY KEY (message_id, consumer)
);

CREATE INDEX inbox_message_processed_at ON inbox_message (processed_at);
