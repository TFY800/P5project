ALTER TABLE items ADD COLUMN creation_key varchar(100);
ALTER TABLE items ADD CONSTRAINT item_creation_key UNIQUE(seller_id,creation_key);
