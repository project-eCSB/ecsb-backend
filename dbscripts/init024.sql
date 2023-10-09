alter table game_session add column INTERACTION_RADIUS int;

update game_session set INTERACTION_RADIUS = 7;
alter table game_session alter column INTERACTION_RADIUS set not null;