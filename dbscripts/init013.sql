alter table game_user add column BUSY_STATUS varchar;
update game_user set BUSY_STATUS = 'not busy';
alter table game_user alter column BUSY_STATUS set not null;
