import Link from "next/link";
import { CircleAlert, CircleDot, Coins, ShieldAlert } from "lucide-react";
import { buildKpisUrl, reportingApiOrigin, serviceHealthTargets } from "@/lib/backend";
import { display } from "@/lib/format";
import type { KpiReport } from "@/lib/types";
import { Badge, Notice } from "@/components/ui";
import { CategoryBarChart } from "@/components/CategoryBarChart";

export const dynamic = "force-dynamic";

interface HealthState {
  name: string;
  state: string;
  detail: string;
}

async function load<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(8000) });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

async function checkHealth(): Promise<HealthState[]> {
  const checks = await Promise.all(
    serviceHealthTargets().map(async (target) => {
      try {
        const res = await fetch(target.url, {
          cache: "no-store",
          signal: AbortSignal.timeout(5000),
        });
        if (!res.ok) return { name: target.name, state: "DOWN", detail: `HTTP ${res.status}` };
        const body = (await res.json()) as { status?: string };
        return {
          name: target.name,
          state: body.status ?? "UNKNOWN",
          // Never leak internal origin to the browser; the proxy owns routing.
          detail: "configured backend endpoint",
        };
      } catch {
        return { name: target.name, state: "UNREACHABLE", detail: "not reachable" };
      }
    }),
  );
  return checks;
}

export default async function Home() {
  const [health, kpis] = await Promise.all([
    checkHealth(),
    load<KpiReport>(buildKpisUrl(reportingApiOrigin())),
  ]);
  const down = health.filter((h) => h.state !== "UP");

  return (
    <>
      <section className="campaign-tile" aria-label="FinRecon AI overview">
        <h1 className="campaign-title">
          Reconcile
          <br />
          Explain
          <br />
          Resolve
        </h1>
        <p className="campaign-sub">
          Do the financial systems agree about what happened to a payment — and if not, what
          explains the discrepancy? Facts below come from the backend services; this page
          computes nothing financial.
        </p>
        <div className="pill-row">
          <Link href="/runs" className="btn btn-on-image">
            Start a run
          </Link>
          <Link href="/cases" className="btn btn-on-image">
            Work the queue
          </Link>
        </div>
      </section>

      {down.length > 0 && (
        <Notice kind="warn" title="Some backends are unreachable">
          Start the stack first (see README). The dashboard shows stored facts only — never
          guesses.
        </Notice>
      )}

      <section className="home-stats" aria-label="Key metrics">
        {kpis === null ? (
          <Notice kind="warn" title="KPIs unavailable">
            The reporting service did not answer. No metric is shown rather than a guessed one.
          </Notice>
        ) : (
          <>
            <div className="stat-row">
              <div className="stat-card">
                <p className="stat-label">
                  <CircleDot size={14} aria-hidden="true" />
                  Open cases
                </p>
                <p className="stat-value">{display(kpis.cases.open)}</p>
              </div>
              <div className="stat-card">
                <p className="stat-label">
                  <CircleAlert size={14} aria-hidden="true" />
                  Mismatched results
                </p>
                <p className="stat-value">{display(kpis.results.mismatched)}</p>
              </div>
              <div className="stat-card">
                <p className="stat-label">
                  <Coins size={14} aria-hidden="true" />
                  Unresolved difference
                </p>
                <p className="stat-value">
                  {display(kpis.impact.absoluteUnresolvedDifference)}
                </p>
              </div>
              <div className="stat-card">
                <p className="stat-label">
                  <ShieldAlert size={14} aria-hidden="true" />
                  High-severity unresolved
                </p>
                <p className="stat-value">{display(kpis.impact.highSeverityUnresolved)}</p>
              </div>
            </div>
            <p className="muted stat-provenance">
              Facts from reporting-service · generated {display(kpis.generatedAt)}
            </p>
            <div className="card">
              <h2>Cases by category</h2>
              {kpis.cases.byCategory.length === 0 ? (
                <p className="muted">No cases recorded yet.</p>
              ) : (
                <CategoryBarChart data={kpis.cases.byCategory} />
              )}
            </div>
          </>
        )}
      </section>

      <div className="card">
        <h2>Service status</h2>
        <table className="grid">
          <caption className="muted">Live reachability of backend services</caption>
          <thead>
            <tr>
              <th scope="col">Service</th>
              <th scope="col">Status</th>
              <th scope="col">Endpoint</th>
            </tr>
          </thead>
          <tbody>
            {health.map((h) => (
              <tr key={h.name}>
                <td>{h.name}</td>
                <td>
                  <Badge status={h.state} />
                </td>

                <td className="muted">{h.detail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2>Analyst workflow</h2>
        <ol>
          <li>
            <Link href="/runs">Start a reconciliation run</Link> and open its results.
          </li>
          <li>
            <Link href="/cases">Work the exception queue</Link>: filter, inspect evidence,
            request a cited AI investigation, then assign, resolve, or escalate.
          </li>
        </ol>
        <p className="muted">
          AI investigations always end at human review. Resolving or escalating a case
          happens only through the case controls — never automatically.
        </p>
      </div>
    </>
  );
}
