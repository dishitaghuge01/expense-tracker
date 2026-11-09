-- Synthetic security canaries only. Nothing survives this transaction.
-- Run with the pinned local Supabase CLI's `supabase test db` after reset.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions, pg_catalog;
select no_plan();

select has_schema('app_private', 'private foundation schema exists');
select schema_owner_is('app_private', 'postgres', 'postgres owns the private schema');
select is(
  (select count(*)::integer from pg_class c join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'app_private' and c.relkind in ('r', 'p', 'v', 'm', 'f', 'S')),
  0,
  'foundation deliberately contains no business relations before test canaries'
);
select is(
  (select count(*)::integer from pg_proc p join pg_namespace n on n.oid = p.pronamespace
   where n.nspname = 'app_private'),
  0,
  'foundation deliberately contains no business RPCs before test canaries'
);

select ok(
  not has_schema_privilege(role_name, 'app_private', privilege_name),
  role_name || ' has no private schema ' || privilege_name
)
from (values ('anon'), ('authenticated')) as roles(role_name)
cross join (values ('USAGE'), ('CREATE')) as privileges(privilege_name);
select is(
  (select count(*)::integer
   from pg_namespace n
   cross join lateral aclexplode(coalesce(n.nspacl, acldefault('n', n.nspowner))) a
   where n.nspname = 'app_private' and a.grantee = 0),
  0,
  'PUBLIC has no schema ACL privileges (PUBLIC is an ACL grantee, not a login role)'
);

-- Explicitly create as postgres: ALTER DEFAULT PRIVILEGES is creator-specific.
set local role postgres;
create table app_private.p1_test_canary_68d980c1 (value integer not null);
create sequence app_private.p1_test_sequence_68d980c1;
create function app_private.p1_test_function_68d980c1()
returns integer language sql immutable security invoker
set search_path = pg_catalog
as $$ select 73 $$;
insert into app_private.p1_test_canary_68d980c1 values (73);
alter table app_private.p1_test_canary_68d980c1 enable row level security;
reset role;

select ok(
  not has_table_privilege(role_name, 'app_private.p1_test_canary_68d980c1', privilege_name),
  role_name || ' has no default table ' || privilege_name
)
from (values ('anon'), ('authenticated')) as roles(role_name)
cross join (values ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'), ('REFERENCES'), ('TRIGGER'))
  as privileges(privilege_name);
select ok(
  not has_sequence_privilege(role_name, 'app_private.p1_test_sequence_68d980c1', privilege_name),
  role_name || ' has no default sequence ' || privilege_name
)
from (values ('anon'), ('authenticated')) as roles(role_name)
cross join (values ('USAGE'), ('SELECT'), ('UPDATE')) as privileges(privilege_name);
select ok(
  not has_function_privilege(role_name, 'app_private.p1_test_function_68d980c1()', 'EXECUTE'),
  role_name || ' has no default function EXECUTE'
)
from (values ('anon'), ('authenticated')) as roles(role_name);

select is(
  (select count(*)::integer from pg_class c
   cross join lateral aclexplode(coalesce(c.relacl, acldefault(
     case when c.relkind = 'S' then 's'::"char" else 'r'::"char" end, c.relowner))) a
   where c.oid in ('app_private.p1_test_canary_68d980c1'::regclass,
                   'app_private.p1_test_sequence_68d980c1'::regclass)
     and a.grantee = 0),
  0,
  'PUBLIC receives no default table or sequence grants'
);
select is(
  (select count(*)::integer from pg_proc p
   cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
   where p.oid = 'app_private.p1_test_function_68d980c1()'::regprocedure and a.grantee = 0),
  0,
  'PUBLIC default function EXECUTE is revoked, including PostgreSQL implicit defaults'
);
select ok(
  (select relrowsecurity from pg_class where oid = 'app_private.p1_test_canary_68d980c1'::regclass),
  'synthetic table explicitly enables RLS; schema defaults do not enable it'
);
select is(
  (select count(*)::integer from pg_policy where polrelid = 'app_private.p1_test_canary_68d980c1'::regclass),
  0,
  'canary has no permissive policies'
);
select is((select count(*)::integer from app_private.p1_test_canary_68d980c1), 1,
          'postgres can see the one synthetic canary row');

-- pgTAP must keep running as the harness owner: its own functions may also
-- inherit the revoked global EXECUTE default. Helpers switch only the probe,
-- restore the role on both outcomes, and leave assertions outside that role.
create function pg_temp.p1_test_sqlstate_as(actor name, command text)
returns text language plpgsql security invoker as $$
declare observed_state text;
begin
  execute format('set local role %I', actor);
  begin
    execute command;
    observed_state := '00000';
  exception when others then
    observed_state := sqlstate;
  end;
  reset role;
  return observed_state;
end;
$$;
create function pg_temp.p1_test_integer_as(actor name, command text)
returns integer language plpgsql security invoker as $$
declare observed_value integer;
begin
  execute format('set local role %I', actor);
  execute command into observed_value;
  reset role;
  return observed_value;
exception when others then
  reset role;
  raise;
end;
$$;

-- Real role-switched operations first demonstrate the schema boundary.
select is(pg_temp.p1_test_sqlstate_as('anon', 'select * from app_private.p1_test_canary_68d980c1'), '42501',
          'anon cannot cross private schema boundary');
select is(pg_temp.p1_test_sqlstate_as('anon', 'create table app_private.p1_test_intruder_68d980c1 (value integer)'), '42501',
          'anon cannot create private relations');
select is(pg_temp.p1_test_sqlstate_as('authenticated', 'select * from app_private.p1_test_canary_68d980c1'), '42501',
          'authenticated cannot cross private schema boundary');
select is(pg_temp.p1_test_sqlstate_as('authenticated', 'create table app_private.p1_test_intruder_68d980c1 (value integer)'), '42501',
          'authenticated cannot create private relations');

-- Transaction-only schema access isolates object defaults from the schema deny.
-- This is not a production/client grant: ROLLBACK restores the migrated state.
grant usage on schema app_private to anon, authenticated;
select is(pg_temp.p1_test_sqlstate_as(role_name, command), '42501', role_name || ' ' || description)
from (values ('anon'::name), ('authenticated'::name)) as roles(role_name)
cross join (values
  ('select * from app_private.p1_test_canary_68d980c1', 'SELECT denied by default table ACL'),
  ('insert into app_private.p1_test_canary_68d980c1 values (99)', 'INSERT denied'),
  ('update app_private.p1_test_canary_68d980c1 set value = 99', 'UPDATE denied'),
  ('delete from app_private.p1_test_canary_68d980c1', 'DELETE denied'),
  ('truncate app_private.p1_test_canary_68d980c1', 'TRUNCATE denied'),
  ($probe$select nextval('app_private.p1_test_sequence_68d980c1')$probe$, 'cannot advance default sequence'),
  ('select app_private.p1_test_function_68d980c1()', 'cannot execute default function')
) as probes(command, description);

-- Prove explicit RLS denies rows independently even if SELECT were later granted.
grant select on app_private.p1_test_canary_68d980c1 to anon, authenticated;
select is(
  pg_temp.p1_test_integer_as(role_name, $probe$select row_security_active('app_private.p1_test_canary_68d980c1'::regclass)::integer$probe$),
  1,
  role_name || ' has active RLS'
)
from (values ('anon'::name), ('authenticated'::name)) as roles(role_name);
select is(
  pg_temp.p1_test_integer_as(role_name, 'select count(*)::integer from app_private.p1_test_canary_68d980c1'),
  0,
  role_name || ' sees no rows with explicit RLS and no policies'
)
from (values ('anon'::name), ('authenticated'::name)) as roles(role_name);

select * from finish();
rollback;
