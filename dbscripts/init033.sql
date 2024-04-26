alter table login_user
    add column verification_token varchar                     not null default 'no_token',
    add column alter_date         timestamp(3) with time zone not null default now(),
    add column verified           boolean                     not null default true;

alter table login_user
    alter column verification_token drop default,
    alter column alter_date drop default,
    alter column verified drop default