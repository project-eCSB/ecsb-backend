drop table if exists equipment_change_queue_resource_item;
drop table if exists equipment_change_queue;
create table equipment_change_queue
(
    id              serial primary key,
    game_session_id bigint                      not null references game_session (id),
    player_id       varchar                     not null,
    money_addition  integer                     not null,
    wait_time       bigint                      not null,
    done_at         timestamp(3) with time zone,
    created_at      timestamp(3) with time zone not null
);

create table equipment_change_queue_resource_item
(
    equipment_change_queue_id bigint  not null references equipment_change_queue (id),
    resource_name             varchar not null,
    resource_value_addition   integer not null,
    primary key (equipment_change_queue_id, resource_name)
);

create index equipment_change_queue_date_index on equipment_change_queue (done_at, wait_time, created_at);






