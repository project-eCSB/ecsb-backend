alter table game_session
    alter column time_for_game type bigint using 0,
    alter column time_for_game set not null,
    add column max_time_amount bigint not null default 0;

alter table game_session
    alter column max_time_amount drop default;

alter table game_session_user_classes
    alter column regen_time type bigint using 1,
    alter column regen_time set not null;
