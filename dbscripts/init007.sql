create table SAVED_ASSETS
(
    ID          bigint primary key generated ALWAYS as IDENTITY,
    NAME        varchar not null,
    PATH        varchar not null,
    FILE_TYPE   varchar not null,
    CREATED_BY  bigint  not null references login_user (ID),
    CREATED_AT  timestamptz DEFAULT NOW(),
    MODIFIED_AT timestamptz DEFAULT NOW()
);

create table MAP_ASSET
(
    SAVED_ASSET_ID           bigint primary key,
    CHARACTER_SPREADSHEET_ID bigint not null,
    TILES_SPREADSHEET_ID     bigint not null,
    foreign key (CHARACTER_SPREADSHEET_ID) references SAVED_ASSETS (ID),
    foreign key (TILES_SPREADSHEET_ID) references SAVED_ASSETS (ID),
    foreign key (SAVED_ASSET_ID) references SAVED_ASSETS (ID)
);

create table MAP_ASSET_DATA
(
    SAVED_ASSET_ID bigint,
    DATA_NAME      varchar not null,
    DATA_VALUE     varchar not null,
    X              bigint  not null,
    Y              bigint  not null,
    foreign key (SAVED_ASSET_ID) references SAVED_ASSETS (ID)
);
