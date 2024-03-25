alter table game_session
    drop column character_sprite_url,
    add column resource_asset_id        bigint references saved_assets (id) not null default 2,
    alter column resource_asset_id drop default,
    add column character_spreadsheet_id bigint references saved_assets (id) not null default 2,
    alter column character_spreadsheet_id drop default,
    add column tiles_spreadsheet_id     bigint references saved_assets (id) not null default 2,
    alter column tiles_spreadsheet_id drop default,
    drop constraint game_session_map_id_fkey,
    add foreign key (map_id) references saved_assets (id);

drop table map_asset;

create table game_travels
(
    id              bigint generated always as identity primary key,
    game_session_id bigint  not null references game_session (id),
    travel_type     varchar not null,
    travel_name     varchar not null,
    time_needed     int,
    money_min       int     not null,
    money_max       int     not null
);

create table game_travels_resources
(
    travel_id           bigint references game_travels (id),
    class_resource_name varchar not null,
    required_value      int     not null
)