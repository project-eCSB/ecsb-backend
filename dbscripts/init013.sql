alter table game_user
    add column busy_status varchar not null default 'not busy',
    alter column busy_status drop default;
