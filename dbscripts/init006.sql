create table SAVED_ASSETS
(
    ID          bigint primary key generated always as identity,
    NAME        varchar not null,
    PATH        varchar not null,
    FILE_TYPE   varchar not null,
    CREATED_BY  bigint  not null references login_user (ID),
    CREATED_AT  timestamptz DEFAULT NOW(),
    MODIFIED_AT timestamptz DEFAULT NOW()
);

create table MAP_ASSET
(
    SAVED_ASSET_ID           bigint primary key references SAVED_ASSETS (ID),
    CHARACTER_SPREADSHEET_ID bigint not null references SAVED_ASSETS (ID),
    TILES_SPREADSHEET_ID     bigint not null references SAVED_ASSETS (ID)
);

create table MAP_ASSET_DATA
(
    SAVED_ASSET_ID bigint references SAVED_ASSETS (ID),
    DATA_NAME      varchar not null,
    DATA_VALUE     varchar not null,
    X              bigint  not null,
    Y              bigint  not null
);
