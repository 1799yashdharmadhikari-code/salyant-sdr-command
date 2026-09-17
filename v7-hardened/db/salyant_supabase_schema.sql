-- Salyant CEO Command Hub persistence (Supabase/Postgres)
create extension if not exists pgcrypto;
create table if not exists salyant_memory (
 id uuid primary key default gen_random_uuid(), scope text not null default 'global', kind text not null default 'note', title text,
 content text not null, session_id text, task_id text, metadata jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index if not exists salyant_memory_scope_idx on salyant_memory(scope);
create index if not exists salyant_memory_updated_idx on salyant_memory(updated_at desc);

create table if not exists salyant_tasks (
 id text primary key, title text not null, status text not null default 'queued', priority text not null default 'normal', owner text not null default 'ai',
 session_id text, progress numeric not null default 0, plan jsonb not null default '{}'::jsonb, checkpoint jsonb not null default '{}'::jsonb,
 metadata jsonb not null default '{}'::jsonb, last_error text, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), completed_at timestamptz
);
create index if not exists salyant_tasks_status_idx on salyant_tasks(status,updated_at desc);

create table if not exists salyant_approvals (
 id uuid primary key default gen_random_uuid(), task_id text, action text not null, reason text, payload jsonb not null default '{}'::jsonb,
 status text not null default 'pending', resolution_note text, created_at timestamptz not null default now(), resolved_at timestamptz
);
create index if not exists salyant_approvals_pending_idx on salyant_approvals(status,created_at desc);

create table if not exists salyant_audit (
 id uuid primary key default gen_random_uuid(), task_id text, session_id text, actor text not null, action text not null, tool text,
 reason text, input jsonb not null default '{}'::jsonb, result jsonb not null default '{}'::jsonb, approval_id uuid,
 created_at timestamptz not null default now()
);
create index if not exists salyant_audit_task_idx on salyant_audit(task_id,created_at desc);

create table if not exists salyant_usage (
 id uuid primary key default gen_random_uuid(), task_id text, provider text not null, metric text not null, quantity numeric not null default 1,
 estimated_cost numeric not null default 0, metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create index if not exists salyant_usage_created_idx on salyant_usage(created_at desc);

create table if not exists salyant_integrations (
 id text primary key, name text not null, provider text not null, credential_ref text, secret_ref_env text, enabled boolean not null default true,
 metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists salyant_contacts (
 id uuid primary key default gen_random_uuid(), email text unique, name text, company text, role text, status text not null default 'lead', source text,
 metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index if not exists salyant_contacts_company_idx on salyant_contacts(company);

create table if not exists salyant_events (
 id uuid primary key default gen_random_uuid(), event_type text not null, payload jsonb not null default '{}'::jsonb, task_id text, source text,
 created_at timestamptz not null default now()
);
create index if not exists salyant_events_created_idx on salyant_events(created_at desc);

-- Optional lightweight full-text search for memory without requiring pgvector.
create index if not exists salyant_memory_fts_idx on salyant_memory using gin(to_tsvector('simple', coalesce(title,'') || ' ' || content));

-- CEO Autopilot profile and actionable suggestions
create table if not exists salyant_ceo_profile (
  id text primary key,
  goals text not null default '',
  auto_allowed text not null default '',
  approval_required text not null default '',
  limits text not null default '',
  updated_at timestamptz not null default now()
);
create table if not exists salyant_ceo_suggestions (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text not null default '',
  action text,
  priority integer not null default 0,
  status text not null default 'open',
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
insert into salyant_ceo_profile(id) values ('default') on conflict (id) do nothing;


create table if not exists salyant_workflow_backups (
 id uuid primary key default gen_random_uuid(), workflow_id text not null, name text, version_id text, workflow jsonb not null, reason text, task_id text, created_at timestamptz not null default now()
);
create index if not exists salyant_workflow_backups_idx on salyant_workflow_backups(workflow_id,created_at desc);

create table if not exists salyant_frontend_errors (
 id uuid primary key default gen_random_uuid(), session_id text, severity text not null default 'error', message text not null, source text, stack text, metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create index if not exists salyant_frontend_errors_idx on salyant_frontend_errors(created_at desc);
