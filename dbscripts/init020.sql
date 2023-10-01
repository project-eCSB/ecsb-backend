create table PLAYER_TIME_TOKEN
(
    GAME_SESSION_ID bigint  not null references game_session (id),
    PLAYER_ID       varchar not null,
    INDEX           bigint  not null,
    ACTUAL_STATE    int     not null,
    MAX_STATE       int     not null,
    LAST_USED       timestamp(3) with time zone,
    primary key (GAME_SESSION_ID, PLAYER_ID, INDEX),
    foreign key (GAME_SESSION_ID, PLAYER_ID) references game_user (game_session_id, name)
);

update game_session
set time_for_game = 6000;

alter table game_session
    alter column started_at type timestamp(3) with time zone,
    add column ended_at timestamp(3) with time zone;