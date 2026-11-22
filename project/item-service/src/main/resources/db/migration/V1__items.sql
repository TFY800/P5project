CREATE TABLE items (
 id uuid PRIMARY KEY,
 name varchar(120) NOT NULL,
 description varchar(4000) NOT NULL,
 image_url varchar(2000) NOT NULL,
 seller_id uuid NOT NULL,
 frozen_by uuid
);
CREATE INDEX items_seller ON items(seller_id);
