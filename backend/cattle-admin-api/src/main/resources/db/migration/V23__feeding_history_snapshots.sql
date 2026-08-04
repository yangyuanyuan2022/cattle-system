-- Freeze the ingredient facts that were used to make a formula or mixing order.
-- Existing rows are backfilled once during this migration; new rows are snapshotted by the service.
ALTER TABLE ration_formula_item
    ADD COLUMN ingredient_name_snapshot VARCHAR(100) NULL AFTER ingredient_id,
    ADD COLUMN dry_matter_pct_snapshot DECIMAL(6,2) NULL AFTER sort_no,
    ADD COLUMN tdn_pct_snapshot DECIMAL(6,2) NULL AFTER dry_matter_pct_snapshot,
    ADD COLUMN metabolizable_energy_value_snapshot DECIMAL(10,3) NULL AFTER tdn_pct_snapshot,
    ADD COLUMN crude_protein_pct_snapshot DECIMAL(6,2) NULL AFTER metabolizable_energy_value_snapshot,
    ADD COLUMN starch_pct_snapshot DECIMAL(6,2) NULL AFTER crude_protein_pct_snapshot,
    ADD COLUMN energy_value_snapshot DECIMAL(10,3) NULL AFTER starch_pct_snapshot,
    ADD COLUMN gain_energy_value_snapshot DECIMAL(10,3) NULL AFTER energy_value_snapshot,
    ADD COLUMN ndf_pct_snapshot DECIMAL(6,2) NULL AFTER gain_energy_value_snapshot,
    ADD COLUMN pe_ndf_pct_snapshot DECIMAL(6,2) NULL AFTER ndf_pct_snapshot,
    ADD COLUMN adf_pct_snapshot DECIMAL(6,2) NULL AFTER pe_ndf_pct_snapshot,
    ADD COLUMN ash_pct_snapshot DECIMAL(6,2) NULL AFTER adf_pct_snapshot,
    ADD COLUMN crude_fat_pct_snapshot DECIMAL(6,2) NULL AFTER ash_pct_snapshot,
    ADD COLUMN calcium_pct_snapshot DECIMAL(6,2) NULL AFTER crude_fat_pct_snapshot,
    ADD COLUMN phosphorus_pct_snapshot DECIMAL(6,2) NULL AFTER calcium_pct_snapshot,
    ADD COLUMN rdp_pct_snapshot DECIMAL(6,2) NULL AFTER phosphorus_pct_snapshot,
    ADD COLUMN unit_price_snapshot DECIMAL(10,2) NULL AFTER rdp_pct_snapshot,
    ADD COLUMN snapshot_at DATETIME NULL AFTER unit_price_snapshot;

ALTER TABLE mixing_order_item
    ADD COLUMN ingredient_name_snapshot VARCHAR(100) NULL AFTER ingredient_id,
    ADD COLUMN dry_matter_pct_snapshot DECIMAL(6,2) NULL AFTER adjust_reason,
    ADD COLUMN tdn_pct_snapshot DECIMAL(6,2) NULL AFTER dry_matter_pct_snapshot,
    ADD COLUMN metabolizable_energy_value_snapshot DECIMAL(10,3) NULL AFTER tdn_pct_snapshot,
    ADD COLUMN crude_protein_pct_snapshot DECIMAL(6,2) NULL AFTER metabolizable_energy_value_snapshot,
    ADD COLUMN starch_pct_snapshot DECIMAL(6,2) NULL AFTER crude_protein_pct_snapshot,
    ADD COLUMN energy_value_snapshot DECIMAL(10,3) NULL AFTER starch_pct_snapshot,
    ADD COLUMN gain_energy_value_snapshot DECIMAL(10,3) NULL AFTER energy_value_snapshot,
    ADD COLUMN ndf_pct_snapshot DECIMAL(6,2) NULL AFTER gain_energy_value_snapshot,
    ADD COLUMN pe_ndf_pct_snapshot DECIMAL(6,2) NULL AFTER ndf_pct_snapshot,
    ADD COLUMN adf_pct_snapshot DECIMAL(6,2) NULL AFTER pe_ndf_pct_snapshot,
    ADD COLUMN ash_pct_snapshot DECIMAL(6,2) NULL AFTER adf_pct_snapshot,
    ADD COLUMN crude_fat_pct_snapshot DECIMAL(6,2) NULL AFTER ash_pct_snapshot,
    ADD COLUMN calcium_pct_snapshot DECIMAL(6,2) NULL AFTER crude_fat_pct_snapshot,
    ADD COLUMN phosphorus_pct_snapshot DECIMAL(6,2) NULL AFTER calcium_pct_snapshot,
    ADD COLUMN rdp_pct_snapshot DECIMAL(6,2) NULL AFTER phosphorus_pct_snapshot,
    ADD COLUMN unit_price_snapshot DECIMAL(10,2) NULL AFTER rdp_pct_snapshot,
    ADD COLUMN snapshot_at DATETIME NULL AFTER unit_price_snapshot;

ALTER TABLE mixing_execution
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'EXECUTED' AFTER deviation_note,
    ADD COLUMN void_reason VARCHAR(500) NULL AFTER status,
    ADD COLUMN voided_by BIGINT NULL AFTER void_reason,
    ADD COLUMN voided_at DATETIME NULL AFTER voided_by,
    ADD INDEX idx_mixing_execution_status(farm_id, status);

UPDATE ration_formula_item x
JOIN feed_ingredient i ON i.ingredient_id = x.ingredient_id AND i.farm_id = x.farm_id
SET x.ingredient_name_snapshot = i.ingredient_name,
    x.dry_matter_pct_snapshot = i.dry_matter_pct,
    x.tdn_pct_snapshot = i.tdn_pct,
    x.metabolizable_energy_value_snapshot = i.metabolizable_energy_value,
    x.crude_protein_pct_snapshot = i.crude_protein_pct,
    x.starch_pct_snapshot = i.starch_pct,
    x.energy_value_snapshot = i.energy_value,
    x.gain_energy_value_snapshot = i.gain_energy_value,
    x.ndf_pct_snapshot = i.ndf_pct,
    x.pe_ndf_pct_snapshot = i.pe_ndf_pct,
    x.adf_pct_snapshot = i.adf_pct,
    x.ash_pct_snapshot = i.ash_pct,
    x.crude_fat_pct_snapshot = i.crude_fat_pct,
    x.calcium_pct_snapshot = i.calcium_pct,
    x.phosphorus_pct_snapshot = i.phosphorus_pct,
    x.rdp_pct_snapshot = i.rdp_pct,
    x.unit_price_snapshot = i.unit_price,
    x.snapshot_at = NOW()
WHERE x.ingredient_name_snapshot IS NULL;

UPDATE mixing_order_item x
JOIN feed_ingredient i ON i.ingredient_id = x.ingredient_id AND i.farm_id = x.farm_id
SET x.ingredient_name_snapshot = i.ingredient_name,
    x.dry_matter_pct_snapshot = i.dry_matter_pct,
    x.tdn_pct_snapshot = i.tdn_pct,
    x.metabolizable_energy_value_snapshot = i.metabolizable_energy_value,
    x.crude_protein_pct_snapshot = i.crude_protein_pct,
    x.starch_pct_snapshot = i.starch_pct,
    x.energy_value_snapshot = i.energy_value,
    x.gain_energy_value_snapshot = i.gain_energy_value,
    x.ndf_pct_snapshot = i.ndf_pct,
    x.pe_ndf_pct_snapshot = i.pe_ndf_pct,
    x.adf_pct_snapshot = i.adf_pct,
    x.ash_pct_snapshot = i.ash_pct,
    x.crude_fat_pct_snapshot = i.crude_fat_pct,
    x.calcium_pct_snapshot = i.calcium_pct,
    x.phosphorus_pct_snapshot = i.phosphorus_pct,
    x.rdp_pct_snapshot = i.rdp_pct,
    x.unit_price_snapshot = i.unit_price,
    x.snapshot_at = NOW()
WHERE x.ingredient_name_snapshot IS NULL;
