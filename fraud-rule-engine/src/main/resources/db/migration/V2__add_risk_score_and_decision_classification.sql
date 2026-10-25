ALTER TABLE fraud_evaluation
    ADD COLUMN risk_score NUMERIC(38, 18) NOT NULL DEFAULT 0;

ALTER TABLE fraud_evaluation
    ADD CONSTRAINT ck_fraud_evaluation_risk_score
        CHECK (risk_score >= 0 AND risk_score <= 100);

ALTER TABLE fraud_evaluation
    ADD CONSTRAINT ck_fraud_evaluation_failed_score
        CHECK (processing_status <> 'FAILED' OR risk_score = 0);

ALTER TABLE fraud_evaluation
    DROP CONSTRAINT ck_evaluation_decision;

ALTER TABLE fraud_evaluation
    DROP CONSTRAINT ck_evaluation_failure_decision;

UPDATE fraud_evaluation
SET decision = CASE decision
    WHEN 'FLAGGED' THEN 'REVIEW'
    WHEN 'NOT_FLAGGED' THEN 'CLEAR'
    ELSE decision
END;

ALTER TABLE fraud_evaluation
    ADD CONSTRAINT ck_evaluation_decision
        CHECK (decision IN ('CLEAR', 'REVIEW', 'HIGH_RISK', 'UNDETERMINED'));

ALTER TABLE fraud_evaluation
    ADD CONSTRAINT ck_evaluation_failure_decision
        CHECK ((processing_status = 'FAILED' AND decision = 'UNDETERMINED')
            OR (processing_status <> 'FAILED' AND decision <> 'UNDETERMINED'));

CREATE INDEX ix_fraud_evaluation_score_time
    ON fraud_evaluation (risk_score DESC, evaluated_at DESC, evaluation_id DESC);
