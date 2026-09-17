# Salyant Command Hub v6 — expert audit and fixes

## Critical defects fixed
1. Previous generated Respond to Webhook nodes had invalid HTTP status codes (-600, -100, 400, 900, 1400). All are now 200.
2. The legacy chat Execute Workflow node referenced `REPLACE_WITH_AI_ROUTER_WORKFLOW_ID`; it now points to the existing `KyVpWgFhSgrOUAEV`.
3. Tool-broker HTTP calls used `this.helpers.httpRequest` inside nested functions; they now use n8n Code-node `$helpers.httpRequest` with timeouts.
4. Browser-facing legacy webhooks were not protected. They are now secret-gated and accessed through same-origin Vercel proxy routes.
5. Destructive/publishing/MCP/bulk-mail operations are enforced at the broker, independently of the model prompt.
6. Workflow update/activation/publishing now take a best-effort persisted backup before mutation.
7. Tool operations are automatically audited when persistence is configured.
8. Frontend error telemetry is persisted and available to the operations agent.
9. Recovery supervisor health checks now authenticate.
10. Frontend repair tools were added for GitHub inspection/update/branch/PR and optional Vercel deploy-hook execution; direct production frontend changes require confirmation.

## Known external prerequisites
- The real AI Router workflow internals are not present in the supplied files, so its internal tool/response contract cannot be verified here. The control workflow now sends the full system/task contract through the Router's known `chatInput` input as a compatibility measure.
- Live n8n/Vercel/GitHub/Supabase credentials were not available for end-to-end execution testing in this session.
- Provider OAuth authorization remains a real provider-side action.
- A broken frontend entrypoint cannot repair itself if the browser never loads any Salyant bootstrap code; the strongest solution is to load the tiny self-healing boot script before optional modules in the HTML.

## Validation performed
- Main n8n export: 70 nodes, 64 connection blocks, no duplicate names/IDs, no dangling node references.
- Recovery supervisor: 6 nodes, 5 connection blocks, no duplicate names/IDs, no dangling references.
- Browser/server JavaScript syntax checks passed.
- LangSearch endpoints match its current official Web Search and Rerank API documentation.


## v7 hardening pass
- Fixed MCP session handling so initialize response headers are preserved.
- Reduced synchronous agent loop ceiling from 8 to 6.
- Added optional Jira Service Management alert/incident tools for unresolved operational failures.
- Hardened browser self-heal fallback when optional live methods are absent.
- Added JSM configuration to env example and operational documentation.
