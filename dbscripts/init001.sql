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
    LOGIN_USER_ID int not null references LOGIN_USER (ID),
    ROLE_ID       int not null references ROLE (ID)
);
