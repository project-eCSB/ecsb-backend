alter table game_session_user_classes
    rename column NAME to CLASS_NAME;

alter table game_session_user_classes
    drop constraint GAME_SESSION_USER_CLASSES_RESOURCE_ID_FK,
    drop column PRODUCED_RESOURCE_ID,
    add column RESOURCE_NAME         varchar not null default 'bread',
    add column RESOURCE_SPRITE_INDEX int     not null default 1,
    add constraint GAME_SESSION_CLASS_UNIQUE unique (game_session_id, CLASS_NAME),
    add constraint GAME_SESSION_RESOURCE_UNIQUE unique (game_session_id, RESOURCE_NAME);

create unique index GAME_SESSION_RESOURCES_UNIQUE_INDEX on GAME_SESSION_USER_CLASSES (game_session_id, RESOURCE_NAME);

alter table PLAYER_RESOURCE
    drop constraint PLAYER_RESOURCE_RESOURCE_ID_FK,
    drop column resource_id,
    add column RESOURCE_NAME varchar not null default 'bread',
    add constraint PLAYER_RESOURCE_PK PRIMARY KEY (GAME_SESSION_ID, PLAYER_ID, RESOURCE_NAME),
    add constraint PLAYER_RESOURCE_FK foreign key (GAME_SESSION_ID, RESOURCE_NAME) references game_session_user_classes (game_session_id, resource_name);

truncate table GAME_SESSION_RESOURCE cascade;

drop table GAME_SESSION_RESOURCE;

