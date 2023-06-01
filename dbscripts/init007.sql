ALTER TABLE GAME_SESSION
    DROP COLUMN starting_x,
    DROP COLUMN starting_y,
    DROP COLUMN starting_direction,
    add column MAP_ID bigint references map_asset (saved_asset_id);