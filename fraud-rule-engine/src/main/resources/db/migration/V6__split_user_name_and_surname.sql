ALTER TABLE app_user
    ADD COLUMN name VARCHAR(100);

ALTER TABLE app_user
    ADD COLUMN surname VARCHAR(100);

UPDATE app_user
SET name = display_name,
    surname = '';

ALTER TABLE app_user
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE app_user
    ALTER COLUMN surname SET NOT NULL;

ALTER TABLE app_user
    DROP COLUMN display_name;
