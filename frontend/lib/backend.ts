// Backend URL builders. Pure functions over explicit origins: no fetch,
// no Next.js imports, so node --test can exercise them after tsc.
// Origins come from the environment with localhost defaults; the browser
// never sees them (server components and /api proxies only).
export const REQUEST_ID_HEADER = "X-Request-Id";

// Case lifecycle actions the UI may invoke. Closed set: the proxy route
// rejects anything else, so the dashboard cannot invent P4 transitions.
export const CASE_ACTIONS = ["assign", "resolve", "escalate"] as const;
export type CaseAction = (typeof CASE_ACTIONS)[number];

export function isCaseAction(value: string | null | undefined): value is CaseAction {
  return (CASE_ACTIONS as readonly string[]).includes(value ?? "");
}

export function caseApiOrigin(): string {
  return process.env.FINRECON_CASE_API_URL ?? "http://localhost:8083";
}

export function reconApiOrigin(): string {
  return process.env.FINRECON_RECON_API_URL ?? "http://localhost:8082";
}

export function aiApiOrigin(): string {
  return process.env.FINRECON_AI_API_URL ?? "http://localhost:8000";
}

export function buildCaseQueueUrl(origin: string, params: URLSearchParams): string {
  const query = params.toString();
  return `${origin}/api/cases${query ? `?${query}` : ""}`;
}

export function buildCaseDetailUrl(origin: string, id: string): string {
  return `${origin}/api/cases/${encodeURIComponent(id)}`;
}

// Returns null for anything outside the closed action set.
export function buildCaseActionUrl(
  origin: string,
  id: string,
  action: string,
): string | null {
  if (!isCaseAction(action)) return null;
  return `${origin}/api/cases/${encodeURIComponent(id)}/${action}`;
}

export function buildRunStartUrl(origin: string, sourceSet: string): string {
  return `${origin}/api/reconcile?sourceSet=${encodeURIComponent(sourceSet)}`;
}

export function buildRunUrl(origin: string, runId: string): string {
  return `${origin}/api/reconcile/runs/${encodeURIComponent(runId)}`;
}

export function buildRunResultsUrl(origin: string, runId: string): string {
  return `${origin}/api/reconcile/runs/${encodeURIComponent(runId)}/results`;
}

export function buildInvestigateUrl(origin: string): string {
  return `${origin}/investigate`;
}

export interface HealthTarget {
  name: string;
  url: string;
}

// Services shown on the dashboard home page. Ports follow docker-compose
// and the root .env.template; all paths are the P0 health contract.
export function serviceHealthTargets(): HealthTarget[] {
  return [
    { name: "gateway-service", url: `${gatewayOrigin()}/api/health` },
    { name: "ingestion-service", url: `${ingestionOrigin()}/api/health` },
    { name: "reconciliation-service", url: `${reconApiOrigin()}/api/health` },
    { name: "exception-service", url: `${caseApiOrigin()}/api/health` },
    { name: "reporting-service", url: `${reportingOrigin()}/api/health` },
    { name: "ai-service", url: `${aiApiOrigin()}/health` },
  ];
}

function gatewayOrigin(): string {
  return process.env.FINRECON_GATEWAY_API_URL ?? "http://localhost:8080";
}

function ingestionOrigin(): string {
  return process.env.FINRECON_INGEST_API_URL ?? "http://localhost:8081";
}

function reportingOrigin(): string {
  return process.env.FINRECON_REPORTING_API_URL ?? "http://localhost:8084";
}
