drop table if exists EQUIPMENT_CHANGE_QUEUE_RESOURCE_ITEM;
drop table if exists EQUIPMENT_CHANGE_QUEUE;
create table EQUIPMENT_CHANGE_QUEUE
(
    ID              serial primary key,
    GAME_SESSION_ID bigint                      not null references GAME_SESSION (ID),
    PLAYER_ID       varchar                     not null,
    MONEY_ADDITION  integer                     not null,
    WAIT_TIME       bigint                      not null,
    DONE_AT         timestamp(3) with time zone,
    CREATED_AT      timestamp(3) with time zone not null
);

create table EQUIPMENT_CHANGE_QUEUE_RESOURCE_ITEM
(
    EQUIPMENT_CHANGE_QUEUE_ID bigint  not null references EQUIPMENT_CHANGE_QUEUE (ID),
    RESOURCE_NAME             varchar not null,
    RESOURCE_VALUE_ADDITION   integer not null,
    primary key (EQUIPMENT_CHANGE_QUEUE_ID, RESOURCE_NAME)
);

create index EQUIPMENT_CHANGE_QUEUE_DATE_INDEX on EQUIPMENT_CHANGE_QUEUE (DONE_AT, WAIT_TIME, CREATED_AT);






