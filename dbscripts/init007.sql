alter table game_session
    drop column starting_x,
    drop column starting_y,
    drop column starting_direction,
    add column map_id bigint references map_asset (saved_asset_id);