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