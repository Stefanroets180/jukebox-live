-- Make party latitude and longitude nullable to support parties without geofence
ALTER TABLE parties ALTER COLUMN latitude DROP NOT NULL;
ALTER TABLE parties ALTER COLUMN longitude DROP NOT NULL;
