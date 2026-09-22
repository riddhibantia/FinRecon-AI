import { buildCaseDetailUrl, caseApiOrigin } from "@/lib/backend";
import { display, shortId } from "@/lib/format";
import { Badge, Notice, Severity } from "@/components/ui";
import AiPanel from "@/components/AiPanel";
import CaseActions from "@/components/CaseActions";
import FeedbackForm from "@/components/FeedbackForm";
import type { CaseDetail, EvidenceRow } from "@/lib/types";

export const dynamic = "force-dynamic";

// Tab set mirrors design-mockups.html sections 4-7: Overview / Evidence /
// AI Investigation / Activity. Server-rendered via ?tab= so every section
// works without client JS; the active tab carries aria-current.
const TABS = [
  { value: "overview", label: "Overview" },
  { value: "evidence", label: "Evidence" },
  { value: "ai", label: "AI Investigation" },
  { value: "activity", label: "Activity" },
] as const;

type TabValue = (typeof TABS)[number]["value"];

function isTab(value: string | undefined): value is TabValue {
  return TABS.some((t) => t.value === value);
}

function tabHref(id: string, tab: TabValue): string {
  const encoded = encodeURIComponent(id);
  return tab === "overview" ? `/cases/${encoded}` : `/cases/${encoded}?tab=${tab}`;
}

// Presentation-only emphasis: highlight rows where the engine recorded two
// different strings. Values stay verbatim; nothing is parsed or computed.
function rowDiffers(e: EvidenceRow): boolean {
  return (
    e.expectedValue !== null &&
    e.observedValue !== null &&
    e.expectedValue !== e.observedValue
  );
}

async function loadCase(id: string): Promise<CaseDetail | null> {
  try {
    const res = await fetch(buildCaseDetailUrl(caseApiOrigin(), id), { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as CaseDetail;
  } catch {
    return null;
  }
}

export default async function CaseDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ tab?: string }>;
}) {
  const { id } = await params;
  const { tab } = await searchParams;
  const active: TabValue = isTab(tab) ? tab : "overview";
  const detail = await loadCase(id);
  if (!detail) {
    return (
      <Notice kind="error" title="Case unavailable">
        Case {id} could not be loaded. The exception service may be unreachable or
        the case may not exist.
      </Notice>
    );
  }

  const activityCount =
    detail.feedback.length + detail.caseActions.length + detail.auditTrail.length;
  const tabLabel = (value: TabValue): string => {
    if (value === "evidence") return `Evidence (${detail.evidence.length})`;
    if (value === "activity") return `Activity (${activityCount})`;
    return TABS.find((t) => t.value === value)?.label ?? value;
  };

  return (
    <>
      <h1>
        Case {shortId(detail.exceptionId)} · {display(detail.externalTxnId)}
      </h1>
      <div className="card">
        <p>
          <Badge status={detail.status} /> <strong>{display(detail.category)}</strong> ·
          severity <Severity severity={detail.severity} /> · assignee {display(detail.assignedTo)}
        </p>
        <p className="muted">
          Opened {display(detail.createdAt)}
          {detail.resolvedAt ? ` · resolved ${detail.resolvedAt}` : ""}
        </p>
      </div>

      <nav className="tabs" aria-label="Case sections">
        {TABS.map((t) => (
          <a
            key={t.value}
            className="tab"
            href={tabHref(detail.exceptionId, t.value)}
            aria-current={t.value === active ? "page" : undefined}
          >
            {tabLabel(t.value)}
          </a>
        ))}
      </nav>

      <div className="detail-layout">
        <div className="detail-main">
          {active === "overview" && (
            <>
              <div className="card">
                <h2>Reconciliation discrepancy</h2>
                <p>
                  Result <Badge status={detail.matchStatus} /> · mismatch{" "}
                  <strong>{display(detail.mismatchType)}</strong> · difference{" "}
                  <strong className="diff-val">{display(detail.amountDifference)}</strong> · rule
                  version {display(detail.ruleVersion)}
                </p>
                <p className="muted">
                  Established by the deterministic engine. The dashboard repeats it verbatim.
                </p>
              </div>

              <div className="card">
                <h2>Source records ({detail.sources.length})</h2>
                {detail.sources.length === 0 ? (
                  <p className="muted">No source records attached.</p>
                ) : (
                  <table className="grid">
                    <caption className="muted">Source records</caption>
                    <thead>
                      <tr>
                        <th scope="col">System</th>
                        <th scope="col">Record</th>
                        <th scope="col">Summary</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.sources.map((s, i) => (
                        <tr key={i}>
                          <td>{display(s.sourceType)}</td>
                          <td className="muted">{shortId(s.recordId)}</td>
                          <td>{display(s.summary)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          )}

          {active === "evidence" && (
            <div className="card">
              <h2>Evidence ({detail.evidence.length})</h2>
              {detail.evidence.length === 0 ? (
                <p className="muted">No evidence rows recorded for this case.</p>
              ) : (
                <table className="grid">
                  <caption className="muted">
                    Evidence — rows where expected and observed differ are highlighted
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">Source</th>
                      <th scope="col">Field</th>
                      <th scope="col">Expected</th>
                      <th scope="col">Observed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.evidence.map((e, i) => (
                      <tr key={i} className={rowDiffers(e) ? "diff-row" : undefined}>
                        <td>
                          {display(e.sourceType)}
                          <br />
                          <span className="muted">{shortId(e.sourceRecordId)}</span>
                        </td>
                        <td>{display(e.fieldName)}</td>
                        <td>{display(e.expectedValue)}</td>
                        <td>{display(e.observedValue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {active === "ai" && <AiPanel exceptionId={detail.exceptionId} />}

          {active === "activity" && (
            <>
              <FeedbackForm caseId={detail.exceptionId} currentCategory={detail.category} />

              <div className="card">
                <h2>Analyst corrections ({detail.feedback.length})</h2>
                {detail.feedback.length === 0 ? (
                  <p className="muted">No analyst corrections recorded.</p>
                ) : (
                  <table className="grid">
                    <caption className="muted">Analyst feedback</caption>
                    <thead>
                      <tr>
                        <th scope="col">Analyst</th>
                        <th scope="col">Original</th>
                        <th scope="col">Corrected</th>
                        <th scope="col">Reason</th>
                        <th scope="col">At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.feedback.map((f) => (
                        <tr key={f.feedbackId}>
                          <td>{display(f.analystId)}</td>
                          <td>{display(f.originalValue)}</td>
                          <td>{display(f.correctedValue)}</td>
                          <td>{display(f.reason)}</td>
                          <td className="muted">{display(f.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div className="card">
                <h2>Resolution actions ({detail.caseActions.length})</h2>
                {detail.caseActions.length === 0 ? (
                  <p className="muted">No actions recorded yet.</p>
                ) : (
                  <table className="grid">
                    <caption className="muted">Resolution actions</caption>
                    <thead>
                      <tr>
                        <th scope="col">Action</th>
                        <th scope="col">Actor</th>
                        <th scope="col">Notes</th>
                        <th scope="col">At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.caseActions.map((a, i) => (
                        <tr key={i}>
                          <td>{display(a.actionType)}</td>
                          <td>
                            {display(a.actorType)} {display(a.actorId)}
                          </td>
                          <td>{display(a.notes)}</td>
                          <td className="muted">{display(a.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div className="card">
                <h2>Audit trail ({detail.auditTrail.length})</h2>
                {detail.auditTrail.length === 0 ? (
                  <p className="muted">No audit entries.</p>
                ) : (
                  <table className="grid">
                    <caption className="muted">Audit trail</caption>
                    <thead>
                      <tr>
                        <th scope="col">Actor</th>
                        <th scope="col">Action</th>
                        <th scope="col">At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.auditTrail.map((a, i) => (
                        <tr key={i}>
                          <td>
                            {display(a.actorType)} {display(a.actorId)}
                          </td>
                          <td>{display(a.action)}</td>
                          <td className="muted">{display(a.timestamp)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          )}
        </div>

        <aside className="detail-aside" aria-label="Case summary and actions">
          <div className="card">
            <h2>Case at a glance</h2>
            <table className="grid">
              <caption className="muted">Stored case facts</caption>
              <tbody>
                <tr>
                  <th scope="row">Status</th>
                  <td>
                    <Badge status={detail.status} />
                  </td>
                </tr>
                <tr>
                  <th scope="row">Category</th>
                  <td>{display(detail.category)}</td>
                </tr>
                <tr>
                  <th scope="row">Severity</th>
                  <td>
                    <Severity severity={detail.severity} />
                  </td>
                </tr>
                <tr>
                  <th scope="row">Assignee</th>
                  <td>{display(detail.assignedTo)}</td>
                </tr>
                <tr>
                  <th scope="row">Transaction</th>
                  <td>{display(detail.externalTxnId)}</td>
                </tr>
                <tr>
                  <th scope="row">Difference</th>
                  <td>
                    <strong className="diff-val">{display(detail.amountDifference)}</strong>
                  </td>
                </tr>
                <tr>
                  <th scope="row">Opened</th>
                  <td>{display(detail.createdAt)}</td>
                </tr>
                <tr>
                  <th scope="row">Resolved</th>
                  <td>{display(detail.resolvedAt)}</td>
                </tr>
                <tr>
                  <th scope="row">Rule version</th>
                  <td>{display(detail.ruleVersion)}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <CaseActions exceptionId={detail.exceptionId} status={detail.status} />
        </aside>
      </div>
    </>
  );
}
