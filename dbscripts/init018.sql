alter table game_session_user_classes
    add column buyout_price integer not null default 0;

update game_session_user_classes
set buyout_price = unit_price
where 1 = 1;
