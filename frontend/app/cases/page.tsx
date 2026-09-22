import Link from "next/link";
import { buildCaseQueueUrl, caseApiOrigin } from "@/lib/backend";
import { display, shortId } from "@/lib/format";
import { Badge, Notice, Severity } from "@/components/ui";
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
const SORTS = [
  { value: "newest", label: "Newest first" },
  { value: "oldest", label: "Oldest first" },
  { value: "severity", label: "Severity" },
  { value: "category", label: "Category" },
  { value: "status", label: "Status" },
];
const PAGE_SIZE = 25;

const SEVERITY_RANK: Record<string, number> = {
  CRITICAL: 0,
  HIGH: 1,
  BLOCKER: 0,
  MEDIUM: 2,
  MODERATE: 2,
  LOW: 3,
  INFO: 3,
};

function severityRank(s: string): number {
  return SEVERITY_RANK[(s ?? "").toUpperCase()] ?? 4;
}

function matchesQuery(c: CaseSummary, q: string): boolean {
  const needle = q.toLowerCase();
  return (
    c.exceptionId.toLowerCase().includes(needle) ||
    c.externalTxnId.toLowerCase().includes(needle) ||
    c.category.toLowerCase().includes(needle) ||
    (c.assignedTo ?? "").toLowerCase().includes(needle)
  );
}

function sortCases(rows: CaseSummary[], sort: string): CaseSummary[] {
  const out = [...rows];
  switch (sort) {
    case "oldest":
      out.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      break;
    case "severity":
      out.sort(
        (a, b) => severityRank(a.severity) - severityRank(b.severity) ||
          a.createdAt.localeCompare(b.createdAt),
      );
      break;
    case "category":
      out.sort(
        (a, b) => a.category.localeCompare(b.category) || a.createdAt.localeCompare(b.createdAt),
      );
      break;
    case "status":
      out.sort(
        (a, b) => a.status.localeCompare(b.status) || a.createdAt.localeCompare(b.createdAt),
      );
      break;
    default:
      out.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
  return out;
}

function pageHref(sp: Record<string, string | undefined>, page: number): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(sp)) {
    if (v) params.set(k, v);
  }
  if (page > 1) params.set("page", String(page));
  const qs = params.toString();
  return qs ? `/cases?${qs}` : "/cases";
}

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
  searchParams: Promise<{
    status?: string;
    category?: string;
    assignedTo?: string;
    q?: string;
    sort?: string;
    page?: string;
  }>;
}) {
  const sp = await searchParams;
  const params = new URLSearchParams();
  if (sp.status) params.set("status", sp.status);
  if (sp.category) params.set("category", sp.category);
  if (sp.assignedTo) params.set("assignedTo", sp.assignedTo);
  const loaded = await loadQueue(params);

  const q = (sp.q ?? "").trim();
  const sort = SORTS.some((s) => s.value === sp.sort) ? (sp.sort as string) : "newest";
  const requestedPage = Math.max(1, Number.parseInt(sp.page ?? "1", 10) || 1);

  const filtered = loaded === null ? null : q ? loaded.filter((c) => matchesQuery(c, q)) : loaded;
  const sorted = filtered === null ? null : sortCases(filtered, sort);
  const total = sorted?.length ?? 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const page = Math.min(requestedPage, totalPages);
  const pageRows = sorted ? sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE) : [];

  const clearParams: Record<string, string | undefined> = {
    status: sp.status,
    category: sp.category,
    assignedTo: sp.assignedTo,
    q: sp.q,
    sort: sp.sort && sp.sort !== "newest" ? sp.sort : undefined,
  };
  const hasFilters = Boolean(sp.status || sp.category || sp.assignedTo || q);

  return (
    <>
      <h1>Exception queue</h1>
      <p className="muted">
        Filtered analyst queue. Facts come from the exception service verbatim.
      </p>
      <div className="card">
        <form method="get" aria-label="Filter cases">
          <div className="row">
            <label className="field" htmlFor="filter-q">
              Search
              <input
                id="filter-q"
                name="q"
                defaultValue={q}
                placeholder="Case, transaction, category…"
                maxLength={128}
                autoComplete="off"
              />
            </label>
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
            <label className="field" htmlFor="filter-sort">
              Sort
              <select id="filter-sort" name="sort" defaultValue={sort}>
                {SORTS.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit">Apply</button>
            {hasFilters && (
              <a href="/cases" className="btn secondary btn-sm">
                Clear filters
              </a>
            )}
          </div>
        </form>
      </div>

      {loaded === null ? (
        <Notice kind="error" title="Queue unavailable">
          The exception service did not answer. Check that it is running — no cases are
          shown rather than stale or guessed ones.
        </Notice>
      ) : total === 0 ? (
        <p className="muted" role="status">
          No cases match these filters.
        </p>
      ) : (
        <>
          <div className="card">
            <table className="grid">
              <caption className="muted">
                {total} matching {total === 1 ? "case" : "cases"}
                {loaded.length >= 500 && q ? ` (of ${loaded.length} loaded, capped at 500)` : ""}
                {loaded.length >= 500 && !q ? " (capped at 500)" : ""}
                {total > PAGE_SIZE ? ` · page ${page} of ${totalPages}` : ""}
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
                {pageRows.map((c) => (
                  <tr key={c.exceptionId}>
                    <td>
                      <Link href={`/cases/${c.exceptionId}`}>{shortId(c.exceptionId)}</Link>
                    </td>
                    <td>{display(c.externalTxnId)}</td>
                    <td>{display(c.category)}</td>
                    <td>
                      <Severity severity={c.severity} />
                    </td>
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

          {totalPages > 1 && (
            <nav className="pager" aria-label="Queue pages">
              {page > 1 ? (
                <Link className="btn secondary btn-sm" href={pageHref(sp, page - 1)}>
                  Previous
                </Link>
              ) : (
                <span className="btn secondary btn-sm pager-disabled" aria-disabled="true">
                  Previous
                </span>
              )}
              <span className="muted pager-status">
                Page {page} of {totalPages}
              </span>
              {page < totalPages ? (
                <Link className="btn secondary btn-sm" href={pageHref(sp, page + 1)}>
                  Next
                </Link>
              ) : (
                <span className="btn secondary btn-sm pager-disabled" aria-disabled="true">
                  Next
                </span>
              )}
            </nav>
          )}
        </>
      )}
    </>
  );
}
