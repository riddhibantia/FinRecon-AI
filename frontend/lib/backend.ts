// Backend URL builders. Pure functions over explicit origins: no fetch,
// no Next.js imports, so node --test can exercise them after tsc.
// Origins come from the environment with localhost defaults; the browser
// never sees them (server components and /api proxies only).
export const REQUEST_ID_HEADER = "X-Request-Id";

// Single backend origin for the monolith. All APIs live on 8080.
export function apiOrigin(): string {
  return process.env.FINRECON_API_URL ?? "http://localhost:8080";
}

// Case lifecycle actions the UI may invoke. Closed set: the proxy route
// rejects anything else, so the dashboard cannot invent P4 transitions.
export const CASE_ACTIONS = ["assign", "resolve", "escalate"] as const;
export type CaseAction = (typeof CASE_ACTIONS)[number];

export function isCaseAction(value: string | null | undefined): value is CaseAction {
  return (CASE_ACTIONS as readonly string[]).includes(value ?? "");
}

export function caseApiOrigin(): string {
  return apiOrigin();
}

export function reconApiOrigin(): string {
  return apiOrigin();
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

export function reportingApiOrigin(): string {
  return apiOrigin();
}

export function buildInvestigateUrl(origin: string): string {
  return `${origin}/investigate`;
}

// FR-11 analyst feedback: append-only confirm/correct on a case.
export function buildFeedbackUrl(origin: string, id: string): string {
  return `${origin}/api/cases/${encodeURIComponent(id)}/feedback`;
}

// FR-13 read-only reporting projections.
export function buildKpisUrl(origin: string): string {
  return `${origin}/api/reports/kpis`;
}

export function buildAgeingUrl(origin: string): string {
  return `${origin}/api/reports/ageing`;
}

export interface HealthTarget {
  name: string;
  url: string;
}

// Services shown on the dashboard home page.
export function serviceHealthTargets(): HealthTarget[] {
  return [
    { name: "finrecon-app", url: `${apiOrigin()}/api/health` },
    { name: "ai-service", url: `${aiApiOrigin()}/health` },
  ];
}

