create table GAME_SESSION
(
    ID                   bigint primary key generated always as identity,
    NAME                 varchar not null,
    CHARACTER_SPRITE_URL varchar not null,
    STARTING_X           int     not null,
    STARTING_Y           int     not null,
    STARTING_DIRECTION   varchar not null,
    SHORT_CODE           varchar not null generated always as (substr(md5(cast(ID as varchar)), 1, 6)) STORED,
    STARTED_AT           timestamptz,
    ENDED_AT             timestamptz,
    CREATED_BY           bigint not null,
    CREATED_AT           timestamptz DEFAULT NOW(),
    MODIFIED_AT          timestamptz default now()
);

CREATE TABLE GAME_USER
(
    LOGIN_USER_ID   bigint,
    NAME            varchar not null,
    CLASS_NAME      varchar not null,
    GAME_SESSION_ID bigint,
    CREATED_AT      TIMESTAMPTZ DEFAULT NOW()
);


CREATE UNIQUE INDEX GAME_USER_IDX ON GAME_USER (LOGIN_USER_ID, GAME_SESSION_ID);
alter table GAME_USER
    add constraint GAME_USER_LOGIN_USER foreign key (LOGIN_USER_ID) references LOGIN_USER (id);
alter table GAME_USER
    add constraint GAME_USER_GAME_SESSION foreign key (GAME_SESSION_ID) references GAME_SESSION (ID);

CREATE TABLE GAME_SESSION_USER_CLASSES
(
    GAME_SESSION_ID         bigint  not null,
    NAME                    VARCHAR NOT NULL,
    WALKING_ANIMATION_INDEX int     not null
);

create unique index GAME_SESSION_USER_CLASSES_UNIQ on GAME_SESSION_USER_CLASSES (game_session_id, name);

alter table GAME_USER
    add constraint GAME_USER_CLASS foreign key (CLASS_NAME, GAME_SESSION_ID) references GAME_SESSION_USER_CLASSES (NAME, GAME_SESSION_ID);
