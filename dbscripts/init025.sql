alter table game_session
    add column max_player_amount int not null default 30;

alter table game_session
    alter column max_player_amount drop default;
