create table LOGIN_USER
(
    ID       serial primary key,
    LOGIN    varchar not null,
    PASSWORD varchar not null
);

create table ROLE
(
    ID   int     not null primary key,
    NAME varchar not null
);

insert into ROLE (ID, NAME)
values (1, 'ADMIN');
insert into ROLE (ID, NAME)
values (2, 'USER');


create table LOGIN_USER_ROLE
(
    LOGIN_USER_ID int not null,
    ROLE_ID       int not null
);

alter table LOGIN_USER_ROLE
    add constraint LOGIN_USER_ROLE_LOGIN_USER_ID_FK foreign key (LOGIN_USER_ID) references LOGIN_USER (ID);
alter table LOGIN_USER_ROLE
    add constraint LOGIN_USER_ROLE_ROLE_ID_FK foreign key (ROLE_ID) references ROLE (ID);