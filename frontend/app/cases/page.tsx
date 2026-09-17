import Link from "next/link";
import { buildCaseQueueUrl, caseApiOrigin } from "@/lib/backend";
import { display, shortId } from "@/lib/format";
import { Badge, Notice } from "@/components/ui";
import type { CaseSummary } from "@/lib/types";

export const dynamic = "force-dynamic";

const STATUSES = ["", "OPEN", "INVESTIGATING", "RESOLVED", "ESCALATED"];
const CATEGORIES = [
  "",
  "MISSING_SETTLEMENT",
  "AMOUNT_MISMATCH",
  "FEE_VARIANCE",
  "FX_VARIANCE",
  "DUPLICATE_SETTLEMENT",
  "PARTIAL_SETTLEMENT",
  "STATUS_MISMATCH",
  "LATE_SETTLEMENT",
  "UNKNOWN_EXCEPTION",
];

async function loadQueue(params: URLSearchParams): Promise<CaseSummary[] | null> {
  try {
    const res = await fetch(buildCaseQueueUrl(caseApiOrigin(), params), { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as CaseSummary[];
  } catch {
    return null;
  }
}

export default async function CasesPage({
  searchParams,
}: {
  searchParams: { status?: string; category?: string; assignedTo?: string };
}) {
  const params = new URLSearchParams();
  if (searchParams.status) params.set("status", searchParams.status);
  if (searchParams.category) params.set("category", searchParams.category);
  if (searchParams.assignedTo) params.set("assignedTo", searchParams.assignedTo);
  const cases = await loadQueue(params);

  return (
    <>
      <h1>Exception queue</h1>
      <div className="card">
        <form method="get">
          <div className="row">
            <label className="field">
              Status
              <select name="status" defaultValue={searchParams.status ?? ""}>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s === "" ? "Any" : s}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Category
              <select name="category" defaultValue={searchParams.category ?? ""}>
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c === "" ? "Any" : c}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Assigned to
              <input name="assignedTo" defaultValue={searchParams.assignedTo ?? ""} />
            </label>
            <button type="submit">Filter</button>
          </div>
        </form>
      </div>

      {cases === null ? (
        <Notice kind="error" title="Queue unavailable">
          The exception service did not answer. Check that it is running — no cases are
          shown rather than stale or guessed ones.
        </Notice>
      ) : cases.length === 0 ? (
        <p className="muted">No cases match these filters.</p>
      ) : (
        <div className="card">
          <table className="grid">
            <thead>
              <tr>
                <th>Case</th>
                <th>Transaction</th>
                <th>Category</th>
                <th>Severity</th>
                <th>Status</th>
                <th>Assignee</th>
                <th>Difference</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((c) => (
                <tr key={c.exceptionId}>
                  <td>
                    <Link href={`/cases/${c.exceptionId}`}>{shortId(c.exceptionId)}</Link>
                  </td>
                  <td>{display(c.externalTxnId)}</td>
                  <td>{display(c.category)}</td>
                  <td>{display(c.severity)}</td>
                  <td>
                    <Badge status={c.status} />
                  </td>
                  <td>{display(c.assignedTo)}</td>
                  <td>{display(c.amountDifference)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
