alter table game_session
    add foreign key (created_by) references login_user (id);

alter table GAME_SESSION
    add column DEFAULT_TIME_VALUE  int not null default 6,
    add column DEFAULT_MONEY_VALUE int not null default 15;

create table GAME_SESSION_RESOURCE
(
    ID              bigint primary key generated always as identity,
    GAME_SESSION_ID bigint  not null,
    RESOURCE_NAME   varchar not null,
    CONSTRAINT GAME_SESSION_RESOURCE_NAME_UNIQUE UNIQUE (GAME_SESSION_ID, RESOURCE_NAME)
);

alter table GAME_SESSION_RESOURCE
    add constraint GAME_SESSION_RESOURCE_GAME_SESSION_ID_FK foreign key (GAME_SESSION_ID) references GAME_SESSION (ID);

create table PLAYER_RESOURCE
(
    GAME_SESSION_ID bigint  not null,
    PLAYER_ID       varchar not null,
    RESOURCE_ID     bigint  not null,
    VALUE           int     not null,
    PRIMARY KEY (GAME_SESSION_ID, PLAYER_ID, RESOURCE_ID)
);

alter table PLAYER_RESOURCE
    add constraint PLAYER_RESOURCE_RESOURCE_ID_FK foreign key (RESOURCE_ID) references GAME_SESSION_RESOURCE (ID),
    add constraint PLAYER_RESOURCE_GAME_SESSION_ID_FK foreign key (GAME_SESSION_ID) references GAME_SESSION (ID),
    add constraint PLAYER_RESOURCE_PLAYER_ID_FK foreign key (GAME_SESSION_ID, PLAYER_ID) references GAME_USER (GAME_SESSION_ID, NAME);

alter table GAME_SESSION_USER_CLASSES
    add column PRODUCED_RESOURCE_ID bigint not null default 1,
    add constraint GAME_SESSION_USER_CLASSES_RESOURCE_ID_FK foreign key (PRODUCED_RESOURCE_ID) references GAME_SESSION_RESOURCE (ID);

alter table GAME_USER
    add column TIME  int not null default 6,
    add column MONEY int not null default 15;
