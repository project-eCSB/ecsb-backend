alter table player_resource
    add column shared_value int;

update player_resource
set shared_value = 0
where shared_value is null;

alter table player_resource
    alter column shared_value set not null;

alter table game_user
    add column shared_time  int,
    add column shared_money int;

update game_user
set shared_time = 0
where shared_time is null;

update game_user
set shared_money = 0
where shared_money is null;

alter table game_user
    alter column shared_time set not null,
    alter column shared_money set not null;