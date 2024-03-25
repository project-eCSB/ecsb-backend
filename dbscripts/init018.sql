alter table game_session_user_classes
    add column buyout_price integer not null default unit_price,
    alter column buyout_price drop default;