alter table game_session
    drop column ended_at,
    add column time_for_game timestamptz;

alter table game_session_user_classes
    add column regen_time timestamptz;
