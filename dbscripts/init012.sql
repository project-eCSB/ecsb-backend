create table anal_log
(
    game_session_id bigint      not null references game_session (id),
    sender_id       varchar     not null,
    sent_at         timestamptz not null,
    message         json
);

create index game_session_player_anal on anal_log (game_session_id, sender_id);