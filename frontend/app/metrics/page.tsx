import {
  Activity,
  CircleAlert,
  CircleCheck,
  CircleDot,
  Clock,
  Coins,
  Hourglass,
  Layers,
  MessageSquare,
  Percent,
  ShieldAlert,
} from "lucide-react";
import { buildAgeingUrl, buildKpisUrl, reportingApiOrigin } from "@/lib/backend";
import { display } from "@/lib/format";
import { Notice } from "@/components/ui";
import { CategoryBarChart } from "@/components/CategoryBarChart";
import type { AgeingReport, KpiReport } from "@/lib/types";

export const dynamic = "force-dynamic";

async function load<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url, { cache: "no-store", signal: AbortSignal.timeout(8000) });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

export default async function MetricsPage({
  searchParams,
}: {
  searchParams: Promise<{ view?: string }>;
}) {
  const { view } = await searchParams;
  // One global display switch: charts by default, plain tables on ?view=table.
  // Server-rendered links, so it works without client JS.
  const showTable = view === "table";
  const origin = reportingApiOrigin();
  const [kpis, ageing] = await Promise.all([
    load<KpiReport>(buildKpisUrl(origin)),
    load<AgeingReport>(buildAgeingUrl(origin)),
  ]);

  if (kpis === null || ageing === null) {
    return (
      <>
        <h1>Operational metrics</h1>
        <Notice kind="error" title="Reporting unavailable">
          The reporting service did not answer. No metric is shown rather than a guessed one.
        </Notice>
      </>
    );
  }

  // Values below are repeated verbatim from stored reporting facts; the page
  // groups and draws them but computes nothing financial.
  const severityData = kpis.cases.bySeverity.map((row) => ({
    category: row.severity,
    count: row.count,
  }));
  const ageingData = [
    { category: "All unresolved", count: ageing.unresolvedTotal },
    {
      category: `Open older than ${ageing.olderThanDays} days`,
      count: ageing.openOlderThanBoundary,
    },
  ];

  return (
    <>
      <h1>Operational metrics</h1>
      <p className="muted">
        Generated {display(kpis.generatedAt)} by reporting-service. Numbers are repeated
        verbatim from stored facts; nothing is computed here.
      </p>

      <div className="view-toggle" role="group" aria-label="Result display">
        <span className="muted" id="view-toggle-label">
          Display:
        </span>
        <a
          href="/metrics"
          className={showTable ? "filter-chip" : "filter-chip filter-chip-active"}
          aria-current={showTable ? undefined : "page"}
        >
          Charts
        </a>
        <a
          href="/metrics?view=table"
          className={showTable ? "filter-chip filter-chip-active" : "filter-chip"}
          aria-current={showTable ? "page" : undefined}
        >
          Tables
        </a>
      </div>

      <section className="metrics-stats" aria-label="Key indicators">
        <div className="stat-group">
          <h2>Runs and results</h2>
          <div className="stat-row">
            <div className="stat-card">
              <p className="stat-label">
                <Activity size={14} aria-hidden="true" />
                Reconciliation runs
              </p>
              <p className="stat-value">{display(kpis.runs.total)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <Layers size={14} aria-hidden="true" />
                Results
              </p>
              <p className="stat-value">{display(kpis.results.total)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <CircleCheck size={14} aria-hidden="true" />
                Matched
              </p>
              <p className="stat-value">{display(kpis.results.matched)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <CircleAlert size={14} aria-hidden="true" />
                Mismatched
              </p>
              <p className="stat-value">{display(kpis.results.mismatched)}</p>
            </div>
          </div>
        </div>

        <div className="stat-group">
          <h2>Cases and impact</h2>
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
                <Percent size={14} aria-hidden="true" />
                Automatic match rate
              </p>
              <p className="stat-value">{display(kpis.results.autoMatchRate)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <Coins size={14} aria-hidden="true" />
                Unresolved absolute difference
              </p>
              <p className="stat-value">{display(kpis.impact.absoluteUnresolvedDifference)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <ShieldAlert size={14} aria-hidden="true" />
                High-severity unresolved
              </p>
              <p className="stat-value">{display(kpis.impact.highSeverityUnresolved)}</p>
            </div>
          </div>
        </div>

        <div className="stat-group">
          <h2>Ageing and feedback</h2>
          <div className="stat-row">
            <div className="stat-card">
              <p className="stat-label">
                <Hourglass size={14} aria-hidden="true" />
                Unresolved total
              </p>
              <p className="stat-value">{display(ageing.unresolvedTotal)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <Hourglass size={14} aria-hidden="true" />
                Unresolved older than {ageing.olderThanDays} days
              </p>
              <p className="stat-value">{display(ageing.openOlderThanBoundary)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <Clock size={14} aria-hidden="true" />
                Oldest unresolved case
              </p>
              <p className="stat-value">{display(ageing.oldestUnresolvedCreatedAt)}</p>
            </div>
            <div className="stat-card">
              <p className="stat-label">
                <MessageSquare size={14} aria-hidden="true" />
                Analyst corrections recorded
              </p>
              <p className="stat-value">{display(kpis.feedback.corrections)}</p>
            </div>
          </div>
        </div>
      </section>

      <div className="card">
        <h2>Cases by category</h2>
        {kpis.cases.byCategory.length === 0 ? (
          <p className="muted">No cases recorded yet.</p>
        ) : showTable ? (
          <table className="grid">
            <caption className="muted">Exception counts by category</caption>
            <thead>
              <tr><th scope="col">Category</th><th scope="col">Cases</th></tr>
            </thead>
            <tbody>
              {kpis.cases.byCategory.map((row) => (
                <tr key={row.category}>
                  <td>{display(row.category)}</td>
                  <td>{display(row.count)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <CategoryBarChart data={kpis.cases.byCategory} />
        )}
      </div>

      <div className="card">
        <h2>Cases by severity</h2>
        {severityData.length === 0 ? (
          <p className="muted">No cases recorded yet.</p>
        ) : showTable ? (
          <table className="grid">
            <caption className="muted">Exception counts by severity</caption>
            <thead>
              <tr><th scope="col">Severity</th><th scope="col">Cases</th></tr>
            </thead>
            <tbody>
              {severityData.map((row) => (
                <tr key={row.category}>
                  <td>{display(row.category)}</td>
                  <td>{display(row.count)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <CategoryBarChart data={severityData} />
        )}
      </div>

      <div className="card">
        <h2>Ageing snapshot</h2>
        {showTable ? (
          <table className="grid">
            <caption className="muted">Unresolved case ageing</caption>
            <tbody>
              <tr>
                <th scope="row">All unresolved</th>
                <td>{display(ageing.unresolvedTotal)}</td>
              </tr>
              <tr>
                <th scope="row">Open older than {ageing.olderThanDays} days</th>
                <td>{display(ageing.openOlderThanBoundary)}</td>
              </tr>
              <tr>
                <th scope="row">Oldest unresolved case</th>
                <td>{display(ageing.oldestUnresolvedCreatedAt)}</td>
              </tr>
            </tbody>
          </table>
        ) : (
          <CategoryBarChart data={ageingData} />
        )}
      </div>
    </>
  );
}
