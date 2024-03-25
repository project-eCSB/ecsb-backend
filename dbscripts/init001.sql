create table login_user
(
    id       serial primary key,
    login    varchar not null,
    password varchar not null
);

create table role
(
    id   int     not null primary key,
    name varchar not null
);

insert into role (id, name)
values (1, 'ADMIN');
insert into role (id, name)
values (2, 'USER');


create table login_user_role
(
    login_user_id int not null references login_user (id),
    role_id       int not null references role (id)
);
