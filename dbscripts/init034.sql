alter table game_session
    add column logs_sent bool not null default false;

alter table game_session
    alter column logs_sent drop default;