create table new_player_tokens
(
    game_session_id bigint  not null,
    player_id       varchar not null,
    actual_state    integer not null,
    max_state       integer not null,
    alter_date      timestamp(3) with time zone,
    primary key (game_session_id, player_id),
    foreign key (game_session_id, player_id) references game_user (game_session_id, name)
);

insert into new_player_tokens (game_session_id, player_id, actual_state, max_state, alter_date)
select game_session_id, player_id, 50 * (max(index) + 1), 50 * (max(index) + 1), now()
from player_time_token
group by game_session_id, player_id;

alter table player_time_token
    rename to old_player_time_token;
alter table new_player_tokens
    rename to player_time_token;
drop table old_player_time_token;
