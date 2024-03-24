alter table game_session_user_classes
    add column max_production bigint not null default 5,
    add column unit_price     bigint not null default 2;

alter table game_session_user_classes
    alter column max_production drop default,
    alter column unit_price drop default;
