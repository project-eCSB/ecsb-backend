alter table game_session
    add column walking_speed int not null default 3,
    alter column walking_speed drop default;