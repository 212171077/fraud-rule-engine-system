ALTER TABLE app_user DROP CONSTRAINT ck_app_user_status;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED'));
