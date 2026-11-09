-- Security foundation only: deliberately no ledger, commerce tables or RPCs.
create schema app_private authorization postgres;

revoke all privileges on schema app_private from PUBLIC, anon, authenticated;

-- Global defaults are necessary: PostgreSQL's global PUBLIC EXECUTE default
-- cannot be cancelled by a schema-local REVOKE alone. These defaults apply to
-- objects created by postgres, not arbitrary future migration owners.
alter default privileges for role postgres
  revoke all privileges on tables from PUBLIC, anon, authenticated;
alter default privileges for role postgres
  revoke all privileges on sequences from PUBLIC, anon, authenticated;
alter default privileges for role postgres
  revoke all privileges on functions from PUBLIC, anon, authenticated;

-- Also remove any schema-local defaults supplied by the local bootstrap.
alter default privileges for role postgres in schema app_private
  revoke all privileges on tables from PUBLIC, anon, authenticated;
alter default privileges for role postgres in schema app_private
  revoke all privileges on sequences from PUBLIC, anon, authenticated;
alter default privileges for role postgres in schema app_private
  revoke all privileges on functions from PUBLIC, anon, authenticated;

comment on schema app_private is
  'Server-only foundation. Not exposed in Data API. Future tables require explicit ENABLE ROW LEVEL SECURITY and reviewed grants; schema defaults do not enable RLS.';
