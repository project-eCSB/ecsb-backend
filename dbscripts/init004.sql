alter table game_session
    add foreign key (created_by) references login_user (id),
    add column default_time_value  int not null default 6,
    add column default_money_value int not null default 15;

alter table game_user
    add constraint game_user_unique unique (game_session_id, name),
    add column time  int not null default 6,
    add column money int not null default 15;

create table player_resource
(
    game_session_id bigint  not null references game_session (id),
    player_id       varchar not null,
    resource_name   varchar not null,
    value           int     not null,
    primary key (game_session_id, player_id, resource_name),
    foreign key (game_session_id, player_id) references game_user (game_session_id, name),
    foreign key (game_session_id, resource_name) references game_session_user_classes (game_session_id, resource_name)
);

