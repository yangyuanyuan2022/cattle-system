ALTER TABLE feed_ingredient
    ADD COLUMN tdn_pct DECIMAL(6,2) NULL AFTER dry_matter_pct,
    ADD COLUMN starch_pct DECIMAL(6,2) NULL AFTER crude_protein_pct,
    ADD COLUMN gain_energy_value DECIMAL(10,3) NULL AFTER energy_value,
    ADD COLUMN rdp_pct DECIMAL(6,2) NULL AFTER ndf_pct;
