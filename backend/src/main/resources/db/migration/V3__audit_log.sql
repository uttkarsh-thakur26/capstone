-- Audit entries (specification 7.2, 14.2, ADR-024). Rows never change, so there is no
-- created_at or updated_at: occurred_at is the time.
CREATE TABLE audit_log (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL,
    actor_user_id uuid        REFERENCES app_user (id) ON DELETE SET NULL,
    actor_email   text,
    action        text        NOT NULL,
    resource_type text,
    resource_id   text,
    outcome       text        NOT NULL,
    ip_address    inet,
    details       jsonb       NOT NULL,
    -- Every action of 14.2 is listed now, so later phases need no migration to record theirs.
    CONSTRAINT audit_log_action_check CHECK (action IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'USER_REGISTERED', 'ROLE_CHANGED',
        'USER_ENABLED', 'USER_DISABLED', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'DOCUMENT_UPLOADED', 'DOCUMENT_DOWNLOADED', 'DOCUMENT_UPDATED', 'DOCUMENT_DELETED',
        'DOCUMENT_REPROCESSED', 'DOCUMENT_ARCHIVED', 'GRANTS_CHANGED', 'ACCESS_DENIED',
        'AI_QUERY', 'SUMMARY_REQUESTED', 'COMPARISON_REQUESTED', 'REINDEX_STARTED')),
    CONSTRAINT audit_log_outcome_check CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
);

CREATE INDEX audit_log_occurred_at_idx ON audit_log (occurred_at);
CREATE INDEX audit_log_actor_user_id_idx ON audit_log (actor_user_id);

-- Append-only, enforced in the database too. Revoking UPDATE and DELETE would not bind the
-- application, which connects as the table's owner. The ON DELETE SET NULL above is an update, so
-- deleting a user (never done, AUTH-11) would also be refused here.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER audit_log_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();
