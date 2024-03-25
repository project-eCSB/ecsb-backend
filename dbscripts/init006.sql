create table saved_assets
(
    id          bigint primary key generated always as identity,
    name        varchar not null,
    path        varchar not null,
    file_type   varchar not null,
    created_by  bigint  not null references login_user (id),
    created_at  timestamptz default now(),
    modified_at timestamptz default now()
);

create table map_asset
(
    saved_asset_id           bigint primary key references saved_assets (id),
    character_spreadsheet_id bigint not null references saved_assets (id),
    tiles_spreadsheet_id     bigint not null references saved_assets (id)
);

create table map_asset_data
(
    saved_asset_id bigint references saved_assets (id),
    data_name      varchar not null,
    data_value     varchar not null,
    x              bigint  not null,
    y              bigint  not null
);
