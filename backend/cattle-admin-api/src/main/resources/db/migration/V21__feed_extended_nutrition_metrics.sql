ALTER TABLE feed_ingredient
    ADD COLUMN metabolizable_energy_value DECIMAL(10,3) NULL AFTER tdn_pct,
    ADD COLUMN adf_pct DECIMAL(6,2) NULL AFTER ndf_pct,
    ADD COLUMN ash_pct DECIMAL(6,2) NULL AFTER adf_pct,
    ADD COLUMN crude_fat_pct DECIMAL(6,2) NULL AFTER ash_pct;
