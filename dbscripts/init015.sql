ALTER TABLE GAME_SESSION
    drop column ended_at,
    add column TIME_FOR_GAME timestamptz;


alter table game_session_user_classes
    add column REGEN_TIME timestamptz;
