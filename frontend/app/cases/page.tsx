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
    const res = await fetch(buildCaseQueueUrl(caseApiOrigin(), params), {
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) return null;
    const data = (await res.json()) as unknown;
    return Array.isArray(data) ? (data as CaseSummary[]) : null;
  } catch {
    return null;
  }
}

export default async function CasesPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; category?: string; assignedTo?: string }>;
}) {
  const sp = await searchParams;
  const params = new URLSearchParams();
  if (sp.status) params.set("status", sp.status);
  if (sp.category) params.set("category", sp.category);
  if (sp.assignedTo) params.set("assignedTo", sp.assignedTo);
  const cases = await loadQueue(params);

  return (
    <>
      <h1>Exception queue</h1>
      <p className="muted">
        Filtered analyst queue. Facts come from the exception service verbatim.
      </p>
      <div className="card">
        <form method="get" aria-label="Filter cases">
          <div className="row">
            <label className="field" htmlFor="filter-status">
              Status
              <select id="filter-status" name="status" defaultValue={sp.status ?? ""}>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s === "" ? "Any" : s}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="filter-category">
              Category
              <select
                id="filter-category"
                name="category"
                defaultValue={sp.category ?? ""}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c === "" ? "Any" : c}
                  </option>
                ))}
              </select>
            </label>
            <label className="field" htmlFor="filter-assignee">
              Assigned to
              <input
                id="filter-assignee"
                name="assignedTo"
                defaultValue={sp.assignedTo ?? ""}
                maxLength={128}
                autoComplete="off"
              />
            </label>
            <button type="submit">Filter</button>
            {(sp.status || sp.category || sp.assignedTo) && (
              <a href="/cases" className="btn secondary btn-sm">
                Clear filters
              </a>
            )}
          </div>
        </form>
      </div>

      {cases === null ? (
        <Notice kind="error" title="Queue unavailable">
          The exception service did not answer. Check that it is running — no cases are
          shown rather than stale or guessed ones.
        </Notice>
      ) : cases.length === 0 ? (
        <p className="muted" role="status">
          No cases match these filters.
        </p>
      ) : (
        <div className="card">
          <table className="grid">
            <caption className="muted">
              {cases.length} cases {cases.length >= 500 ? "(capped at 500)" : ""}
            </caption>
            <thead>
              <tr>
                <th scope="col">Case</th>
                <th scope="col">Transaction</th>
                <th scope="col">Category</th>
                <th scope="col">Severity</th>
                <th scope="col">Status</th>
                <th scope="col">Assignee</th>
                <th scope="col">Difference</th>
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
