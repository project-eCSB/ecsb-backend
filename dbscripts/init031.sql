alter table saved_assets
    add column default_asset bool not null default false,
    alter column default_asset drop default;
create unique index ix_saved_assets_default on saved_assets (file_type) where default_asset = true;
