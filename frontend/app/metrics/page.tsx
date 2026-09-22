import { buildAgeingUrl, buildKpisUrl, reportingApiOrigin } from "@/lib/backend";
import { display } from "@/lib/format";
import { Notice } from "@/components/ui";
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

export default async function MetricsPage() {
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

  return (
    <>
      <h1>Operational metrics</h1>
      <p className="muted">
        Generated {display(kpis.generatedAt)} by reporting-service. Numbers are repeated
        verbatim from stored facts; nothing is computed here.
      </p>
      <div className="card">
        <table className="grid">
          <caption className="muted">Key operational indicators</caption>
          <tbody>
            <tr><th scope="row">Reconciliation runs</th><td>{display(kpis.runs.total)}</td></tr>
            <tr><th scope="row">Results</th><td>{display(kpis.results.total)}</td></tr>
            <tr><th scope="row">Matched</th><td>{display(kpis.results.matched)}</td></tr>
            <tr><th scope="row">Mismatched</th><td>{display(kpis.results.mismatched)}</td></tr>
            <tr><th scope="row">Automatic match rate</th><td>{display(kpis.results.autoMatchRate)}</td></tr>
            <tr><th scope="row">Open cases</th><td>{display(kpis.cases.open)}</td></tr>
            <tr>
              <th scope="row">Unresolved absolute difference</th>
              <td>{display(kpis.impact.absoluteUnresolvedDifference)}</td>
            </tr>
            <tr><th scope="row">High-severity unresolved</th><td>{display(kpis.impact.highSeverityUnresolved)}</td></tr>
            <tr>
              <th scope="row">Unresolved older than {ageing.olderThanDays} days</th>
              <td>{display(ageing.openOlderThanBoundary)}</td>
            </tr>
            <tr><th scope="row">Analyst corrections recorded</th><td>{display(kpis.feedback.corrections)}</td></tr>
          </tbody>
        </table>
      </div>
      <div className="card">
        <h2>Cases by category</h2>
        {kpis.cases.byCategory.length === 0 ? (
          <p className="muted">No cases recorded yet.</p>
        ) : (
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
        )}
      </div>
    </>
  );
}
