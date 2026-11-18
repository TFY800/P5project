CREATE TABLE auctions (
 id uuid PRIMARY KEY, item_id uuid NOT NULL, seller_id uuid NOT NULL, creation_key varchar(100) NOT NULL,
 starting_price bigint NOT NULL CHECK(starting_price BETWEEN 1 AND 1000000000000),
 minimum_increment bigint NOT NULL CHECK(minimum_increment BETWEEN 1 AND 1000000000000),
 ends_at timestamptz NOT NULL, created_at timestamptz NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('PREPARING','ACTIVE','ENDED','FAILED')),
 failure varchar(200), item_name varchar(120), item_description varchar(4000), image_url varchar(2000),
 current_price bigint NOT NULL CHECK(current_price > 0), leading_bidder uuid, version bigint NOT NULL DEFAULT 0,
 UNIQUE(seller_id,creation_key)
);
CREATE UNIQUE INDEX one_auction_per_item ON auctions(item_id) WHERE status <> 'FAILED';
CREATE INDEX auction_due ON auctions(ends_at) WHERE status='ACTIVE';
CREATE TABLE bids (
 id uuid PRIMARY KEY, auction_id uuid NOT NULL REFERENCES auctions(id), bidder_id uuid NOT NULL,
 amount bigint NOT NULL CHECK(amount BETWEEN 1 AND 1000000000000), idempotency_key varchar(100) NOT NULL,
 created_at timestamptz NOT NULL, version bigint NOT NULL,
 UNIQUE(auction_id,bidder_id,idempotency_key), UNIQUE(auction_id,version)
);
CREATE INDEX bid_participation ON bids(bidder_id,auction_id);
CREATE TABLE outbox (
 id uuid PRIMARY KEY, payload text NOT NULL, created_at timestamptz NOT NULL,
 next_attempt timestamptz NOT NULL, delivered boolean NOT NULL DEFAULT false, attempts integer NOT NULL DEFAULT 0
);
CREATE INDEX pending_outbox ON outbox(next_attempt) WHERE NOT delivered;
