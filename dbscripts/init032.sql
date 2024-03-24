alter table game_travels
    add column regen_time bigint not null default 30000;

update game_travels
set time_needed = 2
where time_needed is null;

alter table game_travels
    alter column regen_time drop default,
    alter column time_needed set not null;

delete
from player_time_token
where alter_date < now();

alter table player_time_token
    add column regen_time  bigint,
    add column token_index int not null default 1,
    alter column token_index drop default,
    drop constraint new_player_tokens_pkey,
    add primary key (game_session_id, player_id, token_index);

alter table game_session
    rename column max_time_amount to max_time_tokens;
alter table game_session
    rename column max_player_amount to min_players_to_start;