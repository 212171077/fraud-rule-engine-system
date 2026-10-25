CREATE TABLE rule_configuration_revision (
    revision_id SMALLINT PRIMARY KEY,
    revision BIGINT NOT NULL,
    CONSTRAINT ck_rule_configuration_revision_singleton CHECK (revision_id = 1),
    CONSTRAINT ck_rule_configuration_revision_non_negative CHECK (revision >= 0)
);

INSERT INTO rule_configuration_revision (revision_id, revision)
VALUES (1, 0);
