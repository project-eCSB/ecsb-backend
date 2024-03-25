create table player_time_token
(
    game_session_id bigint  not null references game_session (id),
    player_id       varchar not null,
    index           bigint  not null,
    actual_state    int     not null,
    max_state       int     not null,
    last_used       timestamp(3) with time zone,
    primary key (game_session_id, player_id, index),
    foreign key (game_session_id, player_id) references game_user (game_session_id, name)
);

update game_session
set time_for_game = 6000, max_time_amount = 10;

alter table game_session
    alter column started_at type timestamp(3) with time zone,
    add column ended_at timestamp(3) with time zone;

with recursive
    table_nums as (select 1 as n
                   union all
                   select n + 1
                   from table_nums
                   where n < (select max(max_time_amount) from game_session)),
    tokens as (select game_session_id,
                      name,
                      time
               from game_user)
insert
into player_time_token (game_session_id, player_id, index, max_state, actual_state, last_used)
select tokens.game_session_id,
       tokens.name,
       table_nums.n,
       10,
       case when table_nums.n > tokens.time then 0 else 10 end,
       case when table_nums.n > tokens.time then now() end
from tokens
         inner join game_session gs on gs.id = tokens.game_session_id
         inner join table_nums on gs.max_time_amount >= table_nums.n