alter table game_session drop column character_sprite_url;

alter table game_session add column resource_asset_id bigint references saved_assets(id);
update game_session set resource_asset_id = 2 where 1=1;
alter table game_session alter column resource_asset_id set not null;

alter table game_session add column character_spreadsheet_id bigint references saved_assets(id);
update game_session set character_spreadsheet_id = 2 where 1=1;
alter table game_session alter column character_spreadsheet_id set not null;

alter table game_session add column tiles_spreadsheet_id bigint references saved_assets(id);
update game_session set tiles_spreadsheet_id = 2 where 1=1;
alter table game_session alter column tiles_spreadsheet_id set not null;

alter table game_session drop constraint game_session_map_id_fkey;
alter table game_session add foreign key (map_id) references saved_assets(id);
drop table map_asset;

create table game_travels(
    ID bigint generated always as identity primary key ,
    GAME_SESSION_ID bigint not null references game_session (ID),
    TRAVEL_TYPE varchar not null,
    TRAVEL_NAME varchar not null,
    TIME_NEEDED int,
    MONEY_MIN int not null,
    MONEY_MAX int not null
);

create table game_travels_resources(
    TRAVEL_ID bigint references game_travels(ID),
    CLASS_RESOURCE_NAME varchar not null,
    REQUIRED_VALUE int not null
)