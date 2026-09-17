# Salyant CEO Command Hub — focused autonomous operations build

This package is designed for one-person CEO use: keep the UI small, let the AI coordinate the work, and avoid adding features that do not reduce daily operational effort.

## Included
- Existing SDR + four Zoho mailbox backend preserved.
- AI Operations Director using the existing Router workflow `KyVpWgFhSgrOUAEV`.
- n8n workflow/execution control, repair loop, activation/publishing/deletion policy gates.
- Task state, checkpoints, retries (bounded by agent policy), approvals and escalation records.
- Audit trail and usage ledger.
- Persistent memory and lightweight company/contact source-of-truth through Supabase/Postgres.
- LangSearch web search + semantic rerank.
- Reacher email verification (hosted API or self-hosted URL).
- Dynamic integration registry.
- Multimodal attachment metadata in chat and browser voice input/output.
- Minimal Today/Tasks screen showing only tasks and approvals that need attention.
- Existing dashboard loaded through `salyant-live.js` without replacing the SDR UI.

## Required configuration
n8n environment variables:
- `N8N_API_BASE_URL`
- `N8N_API_KEY`
- `SALYANT_COMMAND_SHARED_SECRET`
- `SALYANT_N8N_BASE_URL`

Persistence/research:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `LANGSEARCH_API_KEY`
- `REACHER_API_KEY` OR `REACHER_BASE_URL`

Optional:
- `N8N_MCP_URL`
- `N8N_MCP_BEARER_TOKEN`

Vercel environment variables:
- `SALYANT_N8N_COMMAND_URL`
- `SALYANT_N8N_COMMAND_SECRET`

## Persistence setup
Run `db/salyant_supabase_schema.sql` in Supabase SQL editor. Use the service-role key only server-side in n8n. Never expose it to the browser.

## Import/deploy
1. Import `n8n/salyant-sdr-command-hub-ceo.n8n.json` into the n8n instance that already contains the AI Router.
2. Set the n8n environment variables above.
3. Deploy the Vercel `api/salyant-sdr/command.js` function and set its two env vars.
4. Replace/add `js/salyant-api.js`, `js/salyant-live.js`, and `js/salyant-command-control.js` in the dashboard.
5. Open the dashboard and use Today first. Then test: `list my workflows`, `show failed executions today`, `search the web for ...`, and `remember ...`.

## Safety defaults
- Reading/research/inspection are automatic.
- Workflow creation/update/validation/run/deactivation are automatic.
- Production activation/publish/delete, bulk email and destructive MCP calls require explicit confirmation.
- Tool loops are bounded to prevent runaway execution.
- Secrets are server-side only.

## Intentionally not automated
Provider OAuth credential creation is not fabricated. Adding Gmail/Outlook/Zoho credentials still requires the provider's real OAuth/credential flow. The integration registry stores capability metadata and credential references without placing secrets in chat.


## CEO Autopilot
The command hub now includes a CEO Autopilot profile and a daily 08:00 n8n schedule. Configure goals, automatically allowed actions, approval-required actions and limits in the UI. The scheduled job calls the same command control plane, so it can inspect memory/tasks/approvals/system health/workflows/mail/research and execute bounded low-risk work. Keep production-changing and destructive actions behind approval.

## Self-healing additions

This package includes `js/salyant-self-heal.js`, a bounded browser watchdog. It checks the health endpoint, retries the live data layer, reloads failed lazy modules once, and enters safe mode after repeated failures to prevent infinite recovery loops.

It also includes `n8n/salyant-recovery-supervisor.n8n.json`. Import it into n8n and configure:
- `SALYANT_PUBLIC_HEALTH_URL` — public health endpoint
- `SALYANT_COMMAND_URL` — protected command endpoint
- `SALYANT_COMMAND_SECRET` — same secret expected by the command endpoint

The supervisor runs hourly when activated, asks the operations agent to diagnose/recover low-risk failures, and escalates unresolved problems. It does not attempt destructive infrastructure changes.


## Hardening / self-healing v6
- All legacy n8n browser-facing endpoints are secret-gated and should be reached through the same-origin Vercel proxy.
- Invalid HTTP response codes in the previous generated export were corrected to 200.
- The legacy chat subworkflow now points to the real AI Router `KyVpWgFhSgrOUAEV`.
- Tool-broker HTTP calls use n8n Code-node `$helpers.httpRequest` with bounded timeouts.
- Destructive operations are enforced by the tool broker, not only by the model prompt.
- Workflow updates/activation/publishing take a best-effort database backup before mutation.
- Every tool operation is automatically audited when persistence is available.
- Frontend errors can be reported to persistent storage and queried by the reliability agent.
- Optional GitHub/Vercel repair tools let the agent inspect and patch frontend code, preferably on a repair branch/PR.
- Recovery supervisor now authenticates its health request.

### Frontend self-healing configuration
Set `GITHUB_TOKEN`, `GITHUB_OWNER`, `GITHUB_REPO`, `GITHUB_BRANCH`, and optionally `VERCEL_DEPLOY_HOOK_URL` if you want the operations agent to inspect/repair/deploy the dashboard source. Keep these values server-side only.


## Reliability / JSM
Optional Jira Service Management integration can receive unresolved operational incidents. Configure JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN and JIRA_CLOUD_ID. The agent uses bounded retries and a maximum of 6 control steps per synchronous command. Long-running work should be checkpointed as a task rather than kept in an unbounded loop.
