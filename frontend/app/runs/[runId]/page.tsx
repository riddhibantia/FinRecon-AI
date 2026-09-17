import Link from "next/link";
import { reconApiOrigin, buildRunUrl, buildRunResultsUrl } from "@/lib/backend";
import { display } from "@/lib/format";
import { Badge, Notice } from "@/components/ui";
import type { ResultView, RunSummary } from "@/lib/types";

export const dynamic = "force-dynamic";

async function load(runId: string): Promise<{ run: RunSummary; results: ResultView[] } | null> {
  try {
    const origin = reconApiOrigin();
    const [runRes, resultsRes] = await Promise.all([
      fetch(buildRunUrl(origin, runId), { cache: "no-store" }),
      fetch(buildRunResultsUrl(origin, runId), { cache: "no-store" }),
    ]);
    if (!runRes.ok) return null;
    const run = (await runRes.json()) as RunSummary;
    const results = resultsRes.ok ? ((await resultsRes.json()) as ResultView[]) : [];
    return { run, results };
  } catch {
    return null;
  }
}

export default async function RunDetailPage({ params }: { params: { runId: string } }) {
  const data = await load(params.runId);
  if (!data) {
    return (
      <Notice kind="error" title="Run unavailable">
        The reconciliation service did not return run {params.runId}. It may be unreachable
        or the run may not exist.
      </Notice>
    );
  }
  const { run, results } = data;
  return (
    <>
      <h1>Run {run.runId.slice(0, 8)}</h1>
      <div className="card">
        <p>
          Status <Badge status={run.status} /> · rule version {display(run.ruleVersion)} ·{" "}
          {run.matched} matched, {run.mismatched} mismatched of {run.total}
        </p>
      </div>
      <div className="card">
        <h2>Results ({results.length})</h2>
        {results.length === 0 ? (
          <p className="muted">This run produced no per-payment results.</p>
        ) : (
          <table className="grid">
            <thead>
              <tr>
                <th>Transaction</th>
                <th>Status</th>
                <th>Mismatch</th>
                <th>Difference</th>
              </tr>
            </thead>
            <tbody>
              {results.map((r) => (
                <tr key={r.resultId}>
                  <td>{display(r.externalTxnId)}</td>
                  <td>
                    <Badge status={r.matchStatus} />
                  </td>
                  <td>{display(r.mismatchType)}</td>
                  <td>{display(r.amountDifference)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <p>
        <Link href="/cases">Open the exception queue</Link> to work mismatches as cases.
      </p>
    </>
  );
}
