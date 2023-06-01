alter table GAME_SESSION
    add foreign key (CREATED_BY) references LOGIN_USER (id),
    add column DEFAULT_TIME_VALUE  int not null default 6,
    add column DEFAULT_MONEY_VALUE int not null default 15;

alter table GAME_USER
    add constraint GAME_USER_UNIQUE unique (GAME_SESSION_ID, NAME),
    add column TIME  int not null default 6,
    add column MONEY int not null default 15;

create table PLAYER_RESOURCE
(
    GAME_SESSION_ID bigint  not null references GAME_SESSION (ID),
    PLAYER_ID       varchar not null,
    RESOURCE_NAME   varchar  not null,
    VALUE           int     not null,
    primary key (GAME_SESSION_ID, PLAYER_ID, RESOURCE_NAME),
    foreign key (GAME_SESSION_ID, PLAYER_ID) references GAME_USER (GAME_SESSION_ID, NAME),
    foreign key (GAME_SESSION_ID, RESOURCE_NAME) references GAME_SESSION_USER_CLASSES (game_session_id, resource_name)
);

