alter table equipment_change_queue add column context varchar(255) not null default 'workshop';
alter table equipment_change_queue ALTER column context drop default;