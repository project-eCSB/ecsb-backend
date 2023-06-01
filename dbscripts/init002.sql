create table GAME_SESSION
(
    ID                   bigint primary key generated always as identity,
    NAME                 varchar not null,
    CHARACTER_SPRITE_URL varchar not null,
    STARTING_X           int     not null,
    STARTING_Y           int     not null,
    STARTING_DIRECTION   varchar not null,
    SHORT_CODE           varchar not null generated always as (substr(md5(cast(ID as varchar)), 1, 6)) stored,
    STARTED_AT           timestamptz,
    ENDED_AT             timestamptz,
    CREATED_BY           bigint  not null,
    CREATED_AT           timestamptz default NOW(),
    MODIFIED_AT          timestamptz default now()
);

create table GAME_USER
(
    LOGIN_USER_ID   bigint references LOGIN_USER (id),
    NAME            varchar not null,
    CLASS_NAME      varchar not null,
    GAME_SESSION_ID bigint references GAME_SESSION (ID),
    CREATED_AT      timestamptz default NOW(),
    unique (LOGIN_USER_ID, GAME_SESSION_ID)
);

create table GAME_SESSION_USER_CLASSES
(
    GAME_SESSION_ID         bigint  not null,
    CLASS_NAME              varchar not null,
    WALKING_ANIMATION_INDEX int     not null,
    RESOURCE_NAME           varchar not null,
    RESOURCE_SPRITE_INDEX   int     not null,
    unique (GAME_SESSION_ID, CLASS_NAME),
    unique (GAME_SESSION_ID, RESOURCE_NAME)
);

alter table GAME_USER
    add foreign key (CLASS_NAME, GAME_SESSION_ID) references GAME_SESSION_USER_CLASSES (CLASS_NAME, GAME_SESSION_ID);
