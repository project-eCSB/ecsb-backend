alter table game_session
    add column
        MAX_PLAYER_AMOUNT int;

update game_session
set max_player_amount = 30;

alter table game_session
    alter column max_player_amount set not null;
