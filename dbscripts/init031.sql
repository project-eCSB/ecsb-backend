alter table SAVED_ASSETS add column DEFAULT_ASSET bool;
update SAVED_ASSETS set DEFAULT_ASSET = false where 1=1;
alter table SAVED_ASSETS alter column DEFAULT_ASSET set not null;
create unique index IX_SAVED_ASSETS_DEFAULT on SAVED_ASSETS (FILE_TYPE) where DEFAULT_ASSET = true;
