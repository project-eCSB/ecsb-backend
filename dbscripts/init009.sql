alter table game_session_user_classes
    add column MAX_PRODUCTION bigint,
    add column UNIT_PRICE     bigint;

update game_session_user_classes
set MAX_PRODUCTION = 5
where MAX_PRODUCTION is null;

update game_session_user_classes
set UNIT_PRICE = 2
where UNIT_PRICE is null;

alter table game_session_user_classes
    alter column MAX_PRODUCTION set not null,
    alter column UNIT_PRICE set not null;