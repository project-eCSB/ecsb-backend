alter table game_session
    add column interaction_radius int not null default 7,
    alter column interaction_radius drop default;