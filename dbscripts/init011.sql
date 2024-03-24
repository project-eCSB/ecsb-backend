alter table player_resource
    add column shared_value int not null default 0;

alter table player_resource
    alter column shared_value drop default;

alter table game_user
    add column shared_time  int not null default 0,
    add column shared_money int not null default 0;

alter table game_user
    alter column shared_time drop default,
    alter column shared_money drop default;