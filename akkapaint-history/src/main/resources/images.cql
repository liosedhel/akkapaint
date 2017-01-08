CREATE TABLE akka.pictures_hours (
    date text,
    hour int,
    picture blob,
    PRIMARY KEY ((date), hour)
);

CREATE TABLE akka.pictures_minutes (
    date text,
    hour int,
    minutes int,
    picture blob,
    PRIMARY KEY ((date, hour), minutes)
);

create TABLE akka.changes(
    year int,
    date text,
    hour int,
    minutes int,
    PRIMARY KEY((year), date, hour, minutes)
) WITH CLUSTERING ORDER BY (date DESC, hour DESC, minutes DESC);


create TABLE akka.temp_tag_migration(
    persistence_id text,
    partition_nr bigint,
    sequence_nr bigint,
    timestamp timeuuid,
    timebucket text,
    ser_manifest text,
    tag1 text,
    PRIMARY KEY ((persistence_id, partition_nr), sequence_nr, timestamp, timebucket)
);

create index tag_index on akka.temp_tag_migration (tag1);

(persistence_id, partition_nr, sequence_nr, timestamp, timebucket, ser_manifest, tag1)