create extension pgcrypto;

update login_user set password = crypt(password, gen_salt('bf'));