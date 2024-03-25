create table game_session
(
    id                   bigint primary key generated always as identity,
    name                 varchar not null,
    character_sprite_url varchar not null,
    starting_x           int     not null,
    starting_y           int     not null,
    starting_direction   varchar not null,
    short_code           varchar not null generated always as (substr(md5(cast(id as varchar)), 1, 6)) stored,
    started_at           timestamptz,
    ended_at             timestamptz,
    created_by           bigint  not null,
    created_at           timestamptz default now(),
    modified_at          timestamptz default now()
);

create table game_user
(
    login_user_id   bigint references login_user (id),
    name            varchar not null,
    class_name      varchar not null,
    game_session_id bigint references game_session (id),
    created_at      timestamptz default now(),
    unique (login_user_id, game_session_id)
);

create table game_session_user_classes
(
    game_session_id         bigint  not null,
    class_name              varchar not null,
    walking_animation_index int     not null,
    resource_name           varchar not null,
    resource_sprite_index   int     not null,
    unique (game_session_id, class_name),
    unique (game_session_id, resource_name)
);

alter table game_user
    add foreign key (class_name, game_session_id) references game_session_user_classes (class_name, game_session_id);
