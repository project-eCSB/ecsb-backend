alter table game_session
    alter column TIME_FOR_GAME TYPE bigint using 0;

alter table game_session
    alter column TIME_FOR_GAME SET NOT NULL;

alter table game_session add column MAX_TIME_AMOUNT bigint not null default 0;

alter table game_session_user_classes drop column regen_time;

alter table game_session_user_classes
    add column REGEN_TIME bigint;

update game_session_user_classes set regen_time = 1;

alter table game_session_user_classes alter column REGEN_TIME set not null;
